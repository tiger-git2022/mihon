package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.IOException

/**
 * Local copy of the EhViewer favorites ([EhFavoritesManifest] and its shards, fork-only feature). A sync reads the
 * manifest again only when its ETag changed, and downloads and indexes only the shards not seen before; list items
 * look manga up in [index].
 */
@Inject
@SingleIn(AppScope::class)
class EhFavoritesStore(
    context: Context,
    private val preferences: LibrarySyncPreferences,
    private val oneDriveSyncClient: OneDriveSyncClient,
) {
    /** The last manifest read and its shards, `shard-<hash>.json.gz` as on OneDrive. */
    private val directory = File(context.filesDir, "tiger/ehviewer-favorites")
    private val manifestFile = File(directory, MANIFEST_FILE)

    // The single file of the favorites before they were split into shards
    private val legacyFile = File(context.filesDir, "tiger/ehviewer-favorites.json.gz")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val normalizer = EhTitleNormalizer(simplifiedChineseConverter())

    private val _index = MutableStateFlow(EhFavoritesIndex.EMPTY)
    val index: StateFlow<EhFavoritesIndex> = _index.asStateFlow()

    /** The manifest [index] was built from, null while it is empty or incomplete. */
    private var manifest: EhFavoritesManifest? = null

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                mutex.withLock {
                    legacyFile.delete()
                    if (!manifestFile.exists()) return@withLock
                    runCatching {
                        val manifest = EhFavoritesFormat.decodeManifest(manifestFile.readText())
                        publish(manifest, buildIndex(manifest, emptyMap()) { _, _, _ -> })
                    }.onFailure {
                        logcat(LogPriority.ERROR, it) { "Could not read the EhViewer favorites" }
                        // A missing or damaged shard: the next sync reads the manifest again instead of trusting its
                        // ETag, and downloads the shards that are not here
                        manifestFile.delete()
                        preferences.ehViewerFavoritesETag.delete()
                    }
                }
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    /** Waits until the copy kept from the last sync is read: [index] is empty before. */
    suspend fun awaitLoaded() = loaded.await()

    /**
     * Reads the favorites EhViewer exported, if they changed. Favorites removed from OneDrive are removed here too.
     * [onProgress] follows the download of the new shards (bytes) and their indexing (galleries).
     */
    suspend fun sync(onProgress: (EhFavoritesSyncStage, done: Long, total: Long) -> Unit = { _, _, _ -> }) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!preferences.showEhViewerFavorites.get()) return@withLock
                repeat(SYNC_ATTEMPTS) {
                    if (syncLocked(onProgress)) return@withLock
                }
                // EhViewer keeps exporting while it changes; the next sync reads the manifest it ends with
                logcat(LogPriority.WARN) { "EhViewer favorites changed while downloading them, kept as they were" }
            }
        }

    /** False if the manifest or one of its shards is gone: EhViewer exported its favorites again meanwhile. */
    private suspend fun syncLocked(onProgress: (EhFavoritesSyncStage, done: Long, total: Long) -> Unit): Boolean {
        val eTag = oneDriveSyncClient.eTag(EhFavoritesManifest.PATH)
        if (eTag == null) {
            // EhViewer stopped sharing its favorites, or its cloud data was cleared
            clearLocked()
            return true
        }
        if (eTag == preferences.ehViewerFavoritesETag.get() && isComplete()) return true
        // Gone right after its ETag was read: asking again tells whether it was removed or replaced
        val text = oneDriveSyncClient.readDocument(EhFavoritesManifest.PATH).contents ?: return false
        val manifest = EhFavoritesFormat.decodeManifest(text)
        val downloaded = download(manifest, onProgress) ?: return false
        val index = buildIndex(manifest, downloaded, onProgress)
        writeAtomically(manifestFile, text)
        publish(manifest, index)
        preferences.ehViewerFavoritesETag.set(eTag)
        // The shards no manifest here needs any more, now that the new index is in place
        val keep = manifest.shards.mapTo(HashSet()) { EhFavoritesManifest.shardFileName(it.hash) } + MANIFEST_FILE
        directory.listFiles()?.filter { it.name !in keep }?.forEach(File::delete)
        logcat(LogPriority.INFO) {
            "EhViewer favorites: ${index.size} galleries, downloaded ${downloaded.size} of ${manifest.shards.size} shards"
        }
        return true
    }

    /**
     * Downloads the shards of [manifest] that are not here yet, streamed to a file each, and returns them read; null
     * if one of them is gone.
     */
    private suspend fun download(
        manifest: EhFavoritesManifest,
        onProgress: (EhFavoritesSyncStage, done: Long, total: Long) -> Unit,
    ): Map<String, EhFavoritesShardFile>? {
        val missing = manifest.shards.distinctBy(EhFavoritesShardRef::hash).filterNot { shardFile(it.hash).exists() }
        if (missing.isEmpty()) return emptyMap()
        directory.mkdirs()
        val total = missing.sumOf { it.size.coerceAtLeast(0) }
        var done = 0L
        onProgress(EhFavoritesSyncStage.DOWNLOAD, 0, total)
        val shards = HashMap<String, EhFavoritesShardFile>()
        for (shard in missing) {
            val file = shardFile(shard.hash)
            val temp = File(directory, "${file.name}.tmp")
            try {
                val found = oneDriveSyncClient.downloadTo(EhFavoritesManifest.shardPath(shard.hash), temp) { read, _ ->
                    onProgress(EhFavoritesSyncStage.DOWNLOAD, done + read, total)
                }
                if (!found) {
                    logcat(LogPriority.WARN) { "EhViewer favorites shard ${shard.hash} is gone" }
                    return null
                }
                done += temp.length()
                // A damaged download fails here, before it is kept
                shards[shard.hash] = temp.inputStream().use { EhFavoritesFormat.decodeShard(it) }
                if (!temp.renameTo(file)) throw IOException("Could not save the EhViewer favorites")
            } finally {
                temp.delete()
            }
            onProgress(EhFavoritesSyncStage.DOWNLOAD, done, total)
        }
        return shards
    }

    /**
     * The index of [manifest]: the shards of the current index are reused, the others indexed from [downloaded] or
     * from the files here. The current index stays in use until this one is complete.
     */
    private fun buildIndex(
        manifest: EhFavoritesManifest,
        downloaded: Map<String, EhFavoritesShardFile>,
        onProgress: (EhFavoritesSyncStage, done: Long, total: Long) -> Unit,
    ): EhFavoritesIndex {
        val current = _index.value
        val total = manifest.shards.distinctBy(EhFavoritesShardRef::hash)
            .filterNot { current.hasShard(it.hash) }
            .sumOf { (downloaded[it.hash]?.galleries?.size ?: it.count).toLong() }
        var done = 0L
        onProgress(EhFavoritesSyncStage.READ, 0, total)
        val hashes = manifest.shards.map(EhFavoritesShardRef::hash)
        return current.updated(manifest.slotNames, hashes, normalizer) { hash ->
            val galleries = downloaded[hash]?.galleries ?: readShard(hash).galleries
            val before = done
            EhFavoritesShardIndex.build(hash, galleries, normalizer) { indexed ->
                onProgress(EhFavoritesSyncStage.READ, before + indexed, total)
            }.also { done += galleries.size }
        }
    }

    private fun readShard(hash: String): EhFavoritesShardFile {
        val file = shardFile(hash)
        return try {
            file.inputStream().use { EhFavoritesFormat.decodeShard(it) }
        } catch (e: Exception) {
            // Downloaded again by the next sync
            file.delete()
            throw e
        }
    }

    private fun publish(manifest: EhFavoritesManifest, index: EhFavoritesIndex) {
        _index.value = index
        this.manifest = manifest
    }

    /** Whether the favorites of the last manifest read are all here. */
    private fun isComplete(): Boolean {
        val manifest = manifest ?: return false
        return manifestFile.exists() && manifest.shards.all { shardFile(it.hash).exists() }
    }

    private fun shardFile(hash: String) = File(directory, EhFavoritesManifest.shardFileName(hash))

    /**
     * Forgets the downloaded favorites after signing out or switching the OneDrive account, so no favorites of the
     * previous account stay marked.
     */
    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { clearLocked() } }

    private fun clearLocked() {
        directory.deleteRecursively()
        legacyFile.delete()
        preferences.ehViewerFavoritesETag.delete()
        manifest = null
        _index.value = EhFavoritesIndex.EMPTY
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"

        /** A shard gone while downloading means a newer manifest: read once more, then leave it to the next sync. */
        const val SYNC_ATTEMPTS = 2

        const val TRADITIONAL_TO_SIMPLIFIED = "Traditional-Simplified"

        fun writeAtomically(file: File, text: String) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) throw IOException("Could not save the EhViewer favorites")
            }
        }

        /** Traditional to simplified Chinese through ICU (Android 10+); older versions compare as written. */
        fun simplifiedChineseConverter(): (String) -> String {
            val converter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) icuConverter() else null
            return converter ?: { it }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun icuConverter(): ((String) -> String)? {
            runCatching { Transliterator.getInstance(TRADITIONAL_TO_SIMPLIFIED) }
                .onFailure { logcat(LogPriority.WARN, it) { "No traditional to simplified Chinese converter" } }
                .getOrNull()
                ?: return null
            // A transliterator is not thread safe: one per thread, so indexing a sync does not hold up the lookups of
            // the badges on other threads
            val transliterators = ThreadLocal.withInitial { Transliterator.getInstance(TRADITIONAL_TO_SIMPLIFIED) }
            return { text ->
                // Romanized titles have nothing to convert
                if (text.all { it.code < 0x80 }) text else transliterators.get()!!.transliterate(text)
            }
        }
    }
}

/** Part of [EhFavoritesStore.sync] that takes long with many favorites: the download, then reading the galleries. */
enum class EhFavoritesSyncStage { DOWNLOAD, READ }
