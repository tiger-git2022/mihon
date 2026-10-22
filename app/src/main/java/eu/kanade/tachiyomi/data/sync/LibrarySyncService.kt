package eu.kanade.tachiyomi.data.sync

import android.app.Activity
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchHistoryPreferences
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchHistoryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.io.File
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
class LibrarySyncService(
    context: Context,
    private val json: Json,
    private val preferences: LibrarySyncPreferences,
    private val mangaRepository: MangaRepository,
    private val oneDriveSyncClient: OneDriveSyncClient,
    private val searchHistoryPreferences: GlobalSearchHistoryPreferences,
    private val mangaMarkStore: MangaMarkStore,
    private val ehFavoritesStore: EhFavoritesStore,
    private val settingsBackupSync: SettingsBackupSync,
) {
    private val syncMutex = Mutex()

    // Fork: the files as last synced, so unchanged ones are neither downloaded nor uploaded
    private val cacheDir = File(context.filesDir, "tiger/sync-cache")
    private val libraryCache = SyncFileCache(cacheDir, OneDriveSyncClient.LIBRARY_FILE)
    private val searchHistoryCache = SyncFileCache(cacheDir, SearchHistorySyncFile.FILE_NAME)
    private val marksCache = SyncFileCache(cacheDir, MangaMarksFile.FILE_NAME)

    // Fork: manual syncs run here, not in the screen that started them, see syncManually
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _progress = MutableStateFlow<LibrarySyncProgress?>(null)

    /** Progress of the running sync, null when no sync runs. */
    val progress: StateFlow<LibrarySyncProgress?> = _progress.asStateFlow()

    // At most once a second within a step and phase: every change redraws the library
    private val progressThrottle = ProgressThrottle<LibrarySyncProgress>(
        samePart = LibrarySyncProgress::isSamePart,
        isFinal = { it.total > 0 && it.done >= it.total },
        publish = { _progress.value = it },
    )

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after every sync, successful or not. */
    val finished: SharedFlow<Unit> = _finished

    private val _changesPending = MutableStateFlow(false)

    /** True while changes wait for a sync [LibraryAutoSync] scheduled for them; the next sync takes them all. */
    val changesPending: StateFlow<Boolean> = _changesPending.asStateFlow()

    fun markChangesPending() {
        _changesPending.value = true
    }

    /** True while a sync runs; library writes seen meanwhile may come from the sync itself. */
    val isSyncing: Boolean
        get() = syncMutex.isLocked

    /** Library entries written to OneDrive by the last library sync step in this process, null before it. */
    @Volatile
    var lastSyncedFavorites: Set<LibrarySyncKey>? = null
        private set

    val redirectUri: String
        get() = oneDriveSyncClient.redirectUri

    suspend fun refreshAccount() {
        updateAccount(oneDriveSyncClient.currentAccount())
    }

    suspend fun signIn(activity: Activity): OneDriveAccount {
        return oneDriveSyncClient.signIn(activity).also { updateAccount(it) }
    }

    suspend fun signOut() {
        oneDriveSyncClient.signOut()
        updateAccount(null)
    }

    /**
     * Syncs the library, then the search history, the manga marks and this device's settings
     * backup. A failure after the library step is reported in [LibrarySyncResult.warning].
     */
    suspend fun sync(): LibrarySyncResult = checkNotNull(syncIf(manual = true) { true })

    private val _manualSyncRunning = MutableStateFlow(false)

    /**
     * True from the tap on sync until [LibraryManualSyncWorker] has finished that sync, which runs as WorkManager work
     * so that it goes on in the background.
     */
    val manualSyncRunning: StateFlow<Boolean> = _manualSyncRunning.asStateFlow()

    internal fun setManualSyncRunning(running: Boolean) {
        _manualSyncRunning.value = running
    }

    /**
     * [sync] asked from a screen, run in the service's own scope: leaving the screen cancels only the wait for the
     * result, the sync itself goes on.
     */
    suspend fun syncManually(): LibrarySyncResult = scope.async { sync() }.await()

    /**
     * [sync], unless [canStart] returns false once the previous sync has finished. Automatic syncs check their
     * options there, since they may have changed while the sync waited, and leave the settings backup to manual
     * syncs ([manual] false): only the data that changes while reading is synced automatically.
     */
    suspend fun syncIf(manual: Boolean = false, canStart: () -> Boolean): LibrarySyncResult? = try {
        syncMutex.withLock { if (canStart()) syncLocked(manual) else null }
    } finally {
        // After the lock is released, so a listener can start the next sync right away
        _finished.tryEmit(Unit)
    }

    private suspend fun syncLocked(manual: Boolean): LibrarySyncResult {
        // This sync takes every change made so far
        _changesPending.value = false
        try {
            return syncSteps(manual).also { recordResult(failed = it.warning != null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordResult(failed = true)
            throw e
        } finally {
            progressThrottle.update(null)
        }
    }

    private fun recordResult(failed: Boolean) {
        preferences.lastSyncTime.set(System.currentTimeMillis())
        preferences.lastSyncFailed.set(failed)
    }

    private suspend fun syncSteps(manual: Boolean): LibrarySyncResult {
        progressThrottle.update(LibrarySyncProgress(LibrarySyncProgress.Step.LIBRARY))
        val account = oneDriveSyncClient.currentAccount()
            ?: throw LibrarySyncException("Sign in to OneDrive before syncing")
        updateAccount(account)
        val device = preferences.currentDevice()

        // One request tells which files changed on OneDrive since this device last synced them
        phase(LibrarySyncProgress.Phase.DOWNLOAD)
        val eTags = oneDriveSyncClient.topLevelETags()
        val result = retryOnConflict { syncLibrary(eTags[OneDriveSyncClient.LIBRARY_FILE]) }
        val warnings = listOfNotNull(
            runStep(LibrarySyncProgress.Step.SEARCH_HISTORY) {
                retryOnConflict { syncSearchHistory(device, eTags[SearchHistorySyncFile.FILE_NAME]) }
            },
            runStep(LibrarySyncProgress.Step.MARKS) { retryOnConflict { syncMarks(eTags[MangaMarksFile.FILE_NAME]) } },
            if (manual) {
                runStep(LibrarySyncProgress.Step.SETTINGS_BACKUP) {
                    settingsBackupSync.upload(device) { stage ->
                        progressThrottle.update(
                            LibrarySyncProgress(LibrarySyncProgress.Step.SETTINGS_BACKUP, stage = stage),
                        )
                    }
                }
            } else {
                null
            },
            runStep(LibrarySyncProgress.Step.EHVIEWER_FAVORITES) {
                ehFavoritesStore.sync { stage, done, total ->
                    progressThrottle.update(
                        LibrarySyncProgress(
                            LibrarySyncProgress.Step.EHVIEWER_FAVORITES,
                            done = done.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            total = total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                            favoritesStage = stage,
                        ),
                    )
                }
            },
        )
        return result.copy(warning = warnings.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }

    /**
     * Each step reads the cloud file and writes it back with its ETag before changing anything locally,
     * so a step that lost the race against another device can simply run again.
     */
    private suspend fun <T> retryOnConflict(step: suspend () -> T): T {
        repeat(CONFLICT_RETRIES) {
            try {
                return step()
            } catch (e: OneDriveSyncConflictException) {
                logcat(LogPriority.WARN, e) { "Sync file changed on another device, retrying" }
            }
        }
        return step()
    }

    suspend fun listSettingsBackups(): List<SettingsBackup> = settingsBackupSync.list()

    suspend fun restoreSettings(backup: SettingsBackup) = settingsBackupSync.restore(backup)

    suspend fun setRead(manga: Manga, read: Boolean) {
        mangaMarkStore.setRead(manga.source, manga.url, manga.title, read, preferences.currentDevice())
    }

    private suspend fun syncSearchHistory(device: SyncDevice, cloudETag: String?) {
        val now = Clock.System.now().toEpochMilliseconds()
        phase(LibrarySyncProgress.Phase.DOWNLOAD)
        val remoteDocument = readCloud(searchHistoryCache, cloudETag)
        val cloud = remoteDocument.contents?.takeIf(String::isNotBlank)
            ?.let { json.decodeFromString<SearchHistorySyncFile>(it) }
            ?: SearchHistorySyncFile()
        if (cloud.version > SearchHistorySyncFile.CURRENT_VERSION) {
            throw LibrarySyncException("The search history sync file was created by a newer app version")
        }
        val local = searchHistoryPreferences.entries.get().map { entry ->
            // Entries from before search history was synced have no device yet
            if (entry.deviceId.isBlank()) entry.copy(deviceId = device.id, deviceName = device.name) else entry
        }
        val clearedAt = maxOf(searchHistoryPreferences.clearedAt.get(), cloud.clearedAt)
        val merged = SearchHistoryMerger.merge(local, cloud.entries, now, clearedAt)
        phase(LibrarySyncProgress.Phase.UPLOAD)
        val unchanged = remoteDocument.contents != null &&
            clearedAt == cloud.clearedAt &&
            merged.toSet() == cloud.entries.toSet()
        val upload = if (unchanged) {
            null
        } else {
            val contents = json.encodeToString(SearchHistorySyncFile(entries = merged, clearedAt = clearedAt))
            contents to write(searchHistoryCache, contents, remoteDocument)
        }
        // Keeps searches (or a clear) made while syncing; they go up with the next sync
        searchHistoryPreferences.update { entries, localClearedAt ->
            val newClearedAt = maxOf(localClearedAt, clearedAt)
            SearchHistoryState(SearchHistoryMerger.merge(entries, merged, now, newClearedAt), newClearedAt)
        }
        keepCopy(searchHistoryCache, remoteDocument, upload)
    }

    private suspend fun syncMarks(cloudETag: String?) {
        phase(LibrarySyncProgress.Phase.DOWNLOAD)
        val remoteDocument = readCloud(marksCache, cloudETag)
        val cloud = remoteDocument.contents?.takeIf(String::isNotBlank)
            ?.let { json.decodeFromString<MangaMarksFile>(it) }
            ?: MangaMarksFile()
        if (cloud.version > MangaMarksFile.CURRENT_VERSION) {
            throw LibrarySyncException("The marks sync file was created by a newer app version")
        }
        val merged = MangaMarksMerger.merge(mangaMarkStore.get(), cloud)
        phase(LibrarySyncProgress.Phase.UPLOAD)
        val unchanged = remoteDocument.contents != null && merged.readMarks.toSet() == cloud.readMarks.toSet()
        val upload = if (unchanged) {
            null
        } else {
            val contents = json.encodeToString(merged)
            contents to write(marksCache, contents, remoteDocument)
        }
        mangaMarkStore.update { MangaMarksMerger.merge(it, merged) }
        keepCopy(marksCache, remoteDocument, upload)
    }

    /**
     * The file on OneDrive: the copy in [cache] while the file still has [cloudETag] (its eTag in this sync's listing),
     * otherwise downloaded.
     */
    private suspend fun readCloud(cache: SyncFileCache, cloudETag: String?): CloudCopy {
        val copy = cache.contentsIfCurrent(cloudETag)
        if (copy != null) return CloudCopy(copy, writeETag = cloudETag, listedETag = cloudETag, downloaded = false)
        val document = oneDriveSyncClient.readDocument(cache.fileName)
        return CloudCopy(document.contents, writeETag = document.etag, listedETag = cloudETag, downloaded = true)
    }

    /** Uploads [contents] over [cloud], returning the eTag of the new file. */
    private suspend fun write(cache: SyncFileCache, contents: String, cloud: CloudCopy): String? = try {
        oneDriveSyncClient.writeDocument(fileName = cache.fileName, contents = contents, etag = cloud.writeETag)
    } catch (e: OneDriveSyncConflictException) {
        // The retry downloads the file, whatever the listing said
        cache.forgetETag()
        throw e
    }

    /**
     * Keeps the file as now on OneDrive as the copy in [cache], once the step has applied it: [upload] (the contents
     * and their eTag) if the step uploaded, otherwise the file it read.
     */
    private fun keepCopy(cache: SyncFileCache, cloud: CloudCopy, upload: Pair<String, String?>?) {
        when {
            upload != null -> cache.save(upload.first, upload.second)
            cloud.downloaded && cloud.contents != null -> cache.save(cloud.contents, cloud.listedETag)
        }
    }

    private class CloudCopy(
        val contents: String?,
        /** For If-Match on upload: the eTag of the download, or that of the listing for the copy. */
        val writeETag: String?,
        /** The eTag of the file in this sync's listing. */
        val listedETag: String?,
        val downloaded: Boolean,
    )

    /** Runs a secondary sync step; returns its error message instead of failing the whole sync. */
    private suspend fun runStep(progressStep: LibrarySyncProgress.Step, step: suspend () -> Unit): String? {
        return try {
            progressThrottle.update(LibrarySyncProgress(progressStep))
            step()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            e.message ?: e.toString()
        }
    }

    /** Shows [phase] of the current step, see [LibrarySyncProgress.phase]. */
    private fun phase(phase: LibrarySyncProgress.Phase, done: Int = 0, total: Int = 0) {
        val step = progressThrottle.latest?.step ?: return
        progressThrottle.update(LibrarySyncProgress(step, done, total, phase = phase))
    }

    private suspend fun syncLibrary(cloudETag: String?): LibrarySyncResult {
        val now = Clock.System.now().toEpochMilliseconds()
        phase(LibrarySyncProgress.Phase.DOWNLOAD)
        // Read before the copy is replaced below
        val baseline = readBaseline()
        val remoteDocument = readCloud(libraryCache, cloudETag)
        val cloud = readSyncFile(remoteDocument.contents)
        val favorites = mangaRepository.getFavorites()
        val favoriteByKey = favorites.associateBy { LibrarySyncKey(it.source, it.url) }
        val local = collectLocalState(favoriteByKey, cloud, baseline, now)
        val merged = LibrarySyncMerger.merge(local, cloud, baseline, now)

        phase(LibrarySyncProgress.Phase.UPLOAD)
        val upload = if (remoteDocument.contents != null && merged.entries == cloud.entries) {
            null
        } else {
            val contents = json.encodeToString(merged)
            contents to write(libraryCache, contents, remoteDocument)
        }
        val result = applyMergedState(merged, favoriteByKey, now)
        // Only once applied: until then the next sync must still see the cloud changes as changes
        keepCopy(libraryCache, remoteDocument, upload)
        preferences.baseline.delete()
        // The state written to OneDrive, not read back: a change made meanwhile must still count as a change
        lastSyncedFavorites = merged.entries.filter(LibrarySyncEntry::favorite).mapTo(HashSet(), LibrarySyncEntry::key)
        return result.copy(total = merged.entries.count(LibrarySyncEntry::favorite))
    }

    private suspend fun updateAccount(account: OneDriveAccount?) {
        val previousAccountId = preferences.accountId.get()
        val currentAccountId = account?.id.orEmpty()
        if (previousAccountId != currentAccountId) {
            preferences.baseline.delete()
            libraryCache.clear()
            searchHistoryCache.clear()
            marksCache.clear()
            // The EhViewer favorites belong to the previous account; the next sync downloads those of the new one
            ehFavoritesStore.clear()
            // So is the result of the last sync shown by the sync action
            preferences.lastSyncTime.delete()
            preferences.lastSyncFailed.delete()
            _changesPending.value = false
        }
        if (account == null) {
            preferences.accountId.delete()
            preferences.accountName.delete()
        } else {
            preferences.accountId.set(account.id)
            preferences.accountName.set(account.name)
        }
    }

    private fun readSyncFile(contents: String?): LibrarySyncFile {
        if (contents.isNullOrBlank()) return LibrarySyncFile()

        val file = runCatching { json.decodeFromString<LibrarySyncFile>(contents) }
            .getOrElse { throw LibrarySyncException("The library sync file is invalid", it) }
        if (file.version > LibrarySyncFile.CURRENT_VERSION) {
            throw LibrarySyncException("The library sync file was created by a newer app version")
        }
        return file
    }

    /** The library as last synced: the copy of the file, or (before the first sync with the copy) the old preference. */
    private fun readBaseline(): LibrarySyncFile? {
        return (libraryCache.contents() ?: preferences.baseline.get())
            .takeIf(String::isNotBlank)
            ?.let { contents -> runCatching { json.decodeFromString<LibrarySyncFile>(contents) }.getOrNull() }
    }

    /**
     * This device's library as sync entries: its favorites ([favoriteByKey], read in one query), and for the entries
     * of the cloud or the baseline that are not favorites here, whether that is a change since the baseline. Those
     * keep the details of the baseline, so no entry needs a query of its own.
     */
    private fun collectLocalState(
        favoriteByKey: Map<LibrarySyncKey, Manga>,
        cloud: LibrarySyncFile,
        baseline: LibrarySyncFile?,
        now: Long,
    ): Map<LibrarySyncKey, LibrarySyncEntry> {
        val cloudKeys = cloud.entries.mapTo(HashSet(), LibrarySyncEntry::key)
        val baselineByKey = baseline?.entries.orEmpty().associateBy(LibrarySyncEntry::key)
        val keys = favoriteByKey.keys + cloudKeys + baselineByKey.keys
        var compared = 0
        val entries = mutableMapOf<LibrarySyncKey, LibrarySyncEntry>()

        for (key in keys) {
            phase(LibrarySyncProgress.Phase.COMPARE, compared++, keys.size)
            val favorite = favoriteByKey[key]
            val baselineEntry = baselineByKey[key]

            if (baselineEntry == null) {
                if (favorite != null) {
                    entries[key] = favorite.toSyncEntry(modifiedAt = favorite.membershipModifiedAt(now))
                }
                continue
            }

            val currentFavorite = favorite != null
            val metadata = favorite?.let(LibrarySyncManga::from) ?: baselineEntry.manga
            entries[key] = if (currentFavorite == baselineEntry.favorite) {
                baselineEntry.copy(manga = metadata)
            } else {
                LibrarySyncEntry(
                    sourceId = key.sourceId,
                    mangaUrl = key.mangaUrl,
                    favorite = currentFavorite,
                    modifiedAt = favorite?.membershipModifiedAt(now) ?: now,
                    manga = metadata,
                )
            }
        }
        return entries
    }

    /** Applies [merged] to the library; only entries added here need a query, to find a manga already known. */
    private suspend fun applyMergedState(
        merged: LibrarySyncFile,
        favoriteByKey: Map<LibrarySyncKey, Manga>,
        now: Long,
    ): LibrarySyncResult {
        var added = 0
        var removed = 0

        for ((index, entry) in merged.entries.withIndex()) {
            phase(LibrarySyncProgress.Phase.APPLY, index, merged.entries.size)
            val favorite = favoriteByKey[entry.key]
            when {
                entry.favorite && favorite == null -> {
                    val existing = mangaRepository.getMangaByUrlAndSourceId(entry.mangaUrl, entry.sourceId)
                    when {
                        existing == null -> {
                            mangaRepository.insertNetworkManga(
                                listOf(entry.manga.toManga(entry.sourceId, entry.mangaUrl, now)),
                            )
                            added++
                        }
                        // Added here while syncing
                        existing.favorite -> Unit
                        else -> {
                            val success = mangaRepository.update(
                                MangaUpdate(existing.id) { favoriteAt = Instant.fromEpochMilliseconds(now) },
                            )
                            if (!success) {
                                throw LibrarySyncException("Could not add ${entry.manga.title} to the library")
                            }
                            added++
                        }
                    }
                }
                !entry.favorite && favorite != null -> {
                    val success = mangaRepository.update(MangaUpdate(favorite.id) { favoriteAt = null })
                    if (!success) throw LibrarySyncException("Could not remove ${entry.manga.title} from the library")
                    removed++
                }
            }
        }
        return LibrarySyncResult(added = added, removed = removed, total = 0)
    }

    private fun Manga.toSyncEntry(modifiedAt: Long) = LibrarySyncEntry(
        sourceId = source,
        mangaUrl = url,
        favorite = favorite,
        modifiedAt = modifiedAt,
        manga = LibrarySyncManga.from(this),
    )

    /**
     * When the entry was added to the library, if known. Removal times are not recorded by the
     * app, so a removal is treated as having happened at sync time.
     */
    private fun Manga.membershipModifiedAt(now: Long): Long {
        return favoriteAt?.toEpochMilliseconds()?.takeIf { it > 0 } ?: now
    }
}

data class LibrarySyncProgress(
    val step: Step,
    /** Entries done and total, for steps that report them; 0 of 0 otherwise. */
    val done: Int = 0,
    val total: Int = 0,
    val stage: SettingsBackupStage? = null,
    /** Download (bytes in [done] and [total], [total] 0 if the size is unknown) or reading of the EhViewer favorites. */
    val favoritesStage: EhFavoritesSyncStage? = null,
    /** Part of the library, search history or marks step, with [done] of [total] entries where they are counted. */
    val phase: Phase? = null,
) {
    /** Parts of a step and the share of it each takes. */
    enum class Phase(val part: ClosedFloatingPointRange<Float>) {
        DOWNLOAD(0f..0.2f),
        COMPARE(0.2f..0.5f),
        UPLOAD(0.5f..0.6f),
        APPLY(0.6f..1f),
    }

    enum class Step(val number: Int) {
        LIBRARY(1),
        SEARCH_HISTORY(2),
        MARKS(3),
        SETTINGS_BACKUP(4),
        EHVIEWER_FAVORITES(5),
    }

    /** Whether [other] shows the same part of the sync, only further along. */
    fun isSamePart(other: LibrarySyncProgress): Boolean =
        step == other.step && phase == other.phase && stage == other.stage && favoritesStage == other.favoritesStage

    val percent: Int?
        // In Long: bytes of a download over 20 MB times 100 overflow an Int
        get() = if (total > 0) (done.toLong() * 100 / total).coerceIn(0, 100).toInt() else null

    /** Progress of the whole sync from 0 to 1: the steps done, plus the part of the current step that is known. */
    val fraction: Float
        get() {
            val known = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
            val stepFraction = when {
                // The download and the reading of the favorites take about half of their step each
                favoritesStage == EhFavoritesSyncStage.DOWNLOAD -> known / 2
                favoritesStage == EhFavoritesSyncStage.READ -> 0.5f + known / 2
                phase != null -> phase.part.start + (phase.part.endInclusive - phase.part.start) * known
                total > 0 -> known
                stage == SettingsBackupStage.UPLOAD -> 0.5f
                else -> 0f
            }
            return ((step.number - 1 + stepFraction) / STEP_COUNT).coerceIn(0f, 1f)
        }

    companion object {
        const val STEP_COUNT = 5
    }
}

data class LibrarySyncResult(
    val added: Int,
    val removed: Int,
    val total: Int,
    /** Error of a step after the library sync, which itself succeeded. */
    val warning: String? = null,
)

private const val CONFLICT_RETRIES = 2

class LibrarySyncException(message: String, cause: Throwable? = null) : IOException(message, cause)
