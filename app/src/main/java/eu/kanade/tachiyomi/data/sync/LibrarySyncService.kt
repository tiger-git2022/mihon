package eu.kanade.tachiyomi.data.sync

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
) {
    val redirectUri: String
        get() = oneDriveSyncClient.redirectUri

    suspend fun refreshAccount() {
        updateAccount(oneDriveSyncClient.currentAccount())
    }

    suspend fun signIn(activity: Activity): OneDriveAccount {
        return oneDriveSyncClient.signIn(activity).also(::updateAccount)
    }

    suspend fun signOut() {
        oneDriveSyncClient.signOut()
        updateAccount(null)
    }

    suspend fun sync(): LibrarySyncResult {
        val account = oneDriveSyncClient.currentAccount()
            ?: throw LibrarySyncException("Sign in to OneDrive before syncing")
        updateAccount(account)

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
        return result.copy(total = merged.entries.count(LibrarySyncEntry::favorite))
    }

    private fun updateAccount(account: OneDriveAccount?) {
        val previousAccountId = preferences.accountId.get()
        val currentAccountId = account?.id.orEmpty()
        if (previousAccountId != currentAccountId) {
            preferences.baseline.delete()
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
        val entries = mutableMapOf<LibrarySyncKey, LibrarySyncEntry>()
        val mangaByKey = mutableMapOf<LibrarySyncKey, Manga?>()

        for (key in keys) {
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

        for (entry in merged.entries) {
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

data class LibrarySyncResult(
    val added: Int,
    val removed: Int,
    val total: Int,
)

class LibrarySyncException(message: String, cause: Throwable? = null) : IOException(message, cause)
