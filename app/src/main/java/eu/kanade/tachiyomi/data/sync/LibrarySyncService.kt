package eu.kanade.tachiyomi.data.sync

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchHistoryPreferences
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchHistoryState
import kotlinx.coroutines.CancellationException
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
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
class LibrarySyncService(
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

    private val _progress = MutableStateFlow<LibrarySyncProgress?>(null)

    /** Progress of the running sync, null when no sync runs. */
    val progress: StateFlow<LibrarySyncProgress?> = _progress.asStateFlow()

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
            _progress.value = null
        }
    }

    private fun recordResult(failed: Boolean) {
        preferences.lastSyncTime.set(System.currentTimeMillis())
        preferences.lastSyncFailed.set(failed)
    }

    private suspend fun syncSteps(manual: Boolean): LibrarySyncResult {
        _progress.value = LibrarySyncProgress(LibrarySyncProgress.Step.LIBRARY)
        val account = oneDriveSyncClient.currentAccount()
            ?: throw LibrarySyncException("Sign in to OneDrive before syncing")
        updateAccount(account)
        val device = preferences.currentDevice()

        val result = retryOnConflict { syncLibrary() }
        val warnings = listOfNotNull(
            runStep(LibrarySyncProgress.Step.SEARCH_HISTORY) { retryOnConflict { syncSearchHistory(device) } },
            runStep(LibrarySyncProgress.Step.MARKS) { retryOnConflict { syncMarks() } },
            if (manual) {
                runStep(LibrarySyncProgress.Step.SETTINGS_BACKUP) {
                    settingsBackupSync.upload(device) { stage ->
                        _progress.value = LibrarySyncProgress(LibrarySyncProgress.Step.SETTINGS_BACKUP, stage = stage)
                    }
                }
            } else {
                null
            },
            runStep(LibrarySyncProgress.Step.EHVIEWER_FAVORITES) {
                ehFavoritesStore.sync { stage, done, total ->
                    _progress.value = LibrarySyncProgress(
                        LibrarySyncProgress.Step.EHVIEWER_FAVORITES,
                        done = done.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        total = total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                        favoritesStage = stage,
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

    private suspend fun syncSearchHistory(device: SyncDevice) {
        val now = Clock.System.now().toEpochMilliseconds()
        val remoteDocument = oneDriveSyncClient.readDocument(SearchHistorySyncFile.FILE_NAME)
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
        oneDriveSyncClient.writeDocument(
            fileName = SearchHistorySyncFile.FILE_NAME,
            contents = json.encodeToString(SearchHistorySyncFile(entries = merged, clearedAt = clearedAt)),
            etag = remoteDocument.etag,
        )
        // Keeps searches (or a clear) made while syncing; they go up with the next sync
        searchHistoryPreferences.update { entries, localClearedAt ->
            val newClearedAt = maxOf(localClearedAt, clearedAt)
            SearchHistoryState(SearchHistoryMerger.merge(entries, merged, now, newClearedAt), newClearedAt)
        }
    }

    private suspend fun syncMarks() {
        val remoteDocument = oneDriveSyncClient.readDocument(MangaMarksFile.FILE_NAME)
        val cloud = remoteDocument.contents?.takeIf(String::isNotBlank)
            ?.let { json.decodeFromString<MangaMarksFile>(it) }
            ?: MangaMarksFile()
        if (cloud.version > MangaMarksFile.CURRENT_VERSION) {
            throw LibrarySyncException("The marks sync file was created by a newer app version")
        }
        val merged = MangaMarksMerger.merge(mangaMarkStore.get(), cloud)
        oneDriveSyncClient.writeDocument(
            fileName = MangaMarksFile.FILE_NAME,
            contents = json.encodeToString(merged),
            etag = remoteDocument.etag,
        )
        mangaMarkStore.update { MangaMarksMerger.merge(it, merged) }
    }

    /** Runs a secondary sync step; returns its error message instead of failing the whole sync. */
    private suspend fun runStep(progressStep: LibrarySyncProgress.Step, step: suspend () -> Unit): String? {
        return try {
            _progress.value = LibrarySyncProgress(progressStep)
            step()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            e.message ?: e.toString()
        }
    }

    private suspend fun syncLibrary(): LibrarySyncResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val remoteDocument = oneDriveSyncClient.readDocument()
        val cloud = readSyncFile(remoteDocument.contents)
        val baseline = readBaseline()
        val localState = collectLocalState(cloud, baseline, now)
        val merged = LibrarySyncMerger.merge(localState.entries, cloud, baseline, now)

        oneDriveSyncClient.writeDocument(
            contents = json.encodeToString(merged),
            etag = remoteDocument.etag,
        )
        val result = applyMergedState(merged, localState.mangaByKey, now)
        preferences.baseline.set(json.encodeToString(merged))
        // The state written to OneDrive, not read back: a change made meanwhile must still count as a change
        lastSyncedFavorites = merged.entries.filter(LibrarySyncEntry::favorite).mapTo(HashSet(), LibrarySyncEntry::key)
        return result.copy(total = merged.entries.count(LibrarySyncEntry::favorite))
    }

    private suspend fun updateAccount(account: OneDriveAccount?) {
        val previousAccountId = preferences.accountId.get()
        val currentAccountId = account?.id.orEmpty()
        if (previousAccountId != currentAccountId) {
            preferences.baseline.delete()
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

    private fun readBaseline(): LibrarySyncFile? {
        return preferences.baseline.get()
            .takeIf(String::isNotBlank)
            ?.let { contents -> runCatching { json.decodeFromString<LibrarySyncFile>(contents) }.getOrNull() }
    }

    private suspend fun collectLocalState(
        cloud: LibrarySyncFile,
        baseline: LibrarySyncFile?,
        now: Long,
    ): LocalState {
        val favorites = mangaRepository.getFavorites()
        val favoriteByKey = favorites.associateBy { LibrarySyncKey(it.source, it.url) }
        val cloudByKey = cloud.entries.associateBy(LibrarySyncEntry::key)
        val baselineByKey = baseline?.entries.orEmpty().associateBy(LibrarySyncEntry::key)
        val keys = favoriteByKey.keys + cloudByKey.keys + baselineByKey.keys
        // Comparing and then applying each entry: about twice the number of entries
        var compared = 0
        val progressTotal = keys.size * 2
        val entries = mutableMapOf<LibrarySyncKey, LibrarySyncEntry>()
        val mangaByKey = mutableMapOf<LibrarySyncKey, Manga?>()

        for (key in keys) {
            _progress.value = LibrarySyncProgress(LibrarySyncProgress.Step.LIBRARY, compared++, progressTotal)
            val existing = favoriteByKey[key]
                ?: mangaRepository.getMangaByUrlAndSourceId(key.mangaUrl, key.sourceId)
            mangaByKey[key] = existing
            val baselineEntry = baselineByKey[key]

            if (baselineEntry == null) {
                if (existing?.favorite == true) {
                    entries[key] = existing.toSyncEntry(modifiedAt = existing.membershipModifiedAt(now))
                }
                continue
            }

            val currentFavorite = existing?.favorite == true
            if (currentFavorite == baselineEntry.favorite) {
                entries[key] = baselineEntry.copy(
                    manga = existing?.let(LibrarySyncManga::from) ?: baselineEntry.manga,
                )
            } else {
                val metadata = existing?.let(LibrarySyncManga::from)
                    ?: baselineEntry.manga
                entries[key] = LibrarySyncEntry(
                    sourceId = key.sourceId,
                    mangaUrl = key.mangaUrl,
                    favorite = currentFavorite,
                    modifiedAt = existing?.membershipModifiedAt(now) ?: now,
                    manga = metadata,
                )
            }
        }
        return LocalState(entries, mangaByKey)
    }

    private suspend fun applyMergedState(
        merged: LibrarySyncFile,
        knownManga: Map<LibrarySyncKey, Manga?>,
        now: Long,
    ): LibrarySyncResult {
        var added = 0
        var removed = 0

        val compared = knownManga.size
        val progressTotal = compared + merged.entries.size
        for ((index, entry) in merged.entries.withIndex()) {
            _progress.value = LibrarySyncProgress(LibrarySyncProgress.Step.LIBRARY, compared + index, progressTotal)
            val existing = knownManga[entry.key]
                ?: mangaRepository.getMangaByUrlAndSourceId(entry.mangaUrl, entry.sourceId)
            when {
                entry.favorite && existing == null -> {
                    mangaRepository.insertNetworkManga(
                        listOf(entry.manga.toManga(entry.sourceId, entry.mangaUrl, now)),
                    )
                    added++
                }
                entry.favorite && existing != null && !existing.favorite -> {
                    val success = mangaRepository.update(
                        MangaUpdate(existing.id) { favoriteAt = Instant.fromEpochMilliseconds(now) },
                    )
                    if (!success) throw LibrarySyncException("Could not add ${entry.manga.title} to the library")
                    added++
                }
                !entry.favorite && existing?.favorite == true -> {
                    val success = mangaRepository.update(MangaUpdate(existing.id) { favoriteAt = null })
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

    private data class LocalState(
        val entries: Map<LibrarySyncKey, LibrarySyncEntry>,
        val mangaByKey: Map<LibrarySyncKey, Manga?>,
    )
}

data class LibrarySyncProgress(
    val step: Step,
    /** Entries done and total, for steps that report them; 0 of 0 otherwise. */
    val done: Int = 0,
    val total: Int = 0,
    val stage: SettingsBackupStage? = null,
    /** Download (bytes in [done] and [total], [total] 0 if the size is unknown) or reading of the EhViewer favorites. */
    val favoritesStage: EhFavoritesSyncStage? = null,
) {
    enum class Step(val number: Int) {
        LIBRARY(1),
        SEARCH_HISTORY(2),
        MARKS(3),
        SETTINGS_BACKUP(4),
        EHVIEWER_FAVORITES(5),
    }

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
