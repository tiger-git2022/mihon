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
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Local copy of the EhViewer favorites index ([EhFavoritesIndexFile], fork-only feature). A sync downloads it again
 * only when its ETag changed; list items look manga up in [index].
 */
@Inject
@SingleIn(AppScope::class)
class EhFavoritesStore(
    context: Context,
    private val preferences: LibrarySyncPreferences,
    private val oneDriveSyncClient: OneDriveSyncClient,
) {
    private val file = File(context.filesDir, "tiger/ehviewer-favorites.json.gz")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val normalizer = EhTitleNormalizer(simplifiedChineseConverter())

    private val _index = MutableStateFlow(EhFavoritesIndex.EMPTY)
    val index: StateFlow<EhFavoritesIndex> = _index.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                mutex.withLock {
                    if (!file.exists()) return@withLock
                    runCatching { buildIndex(file.readBytes()) }
                        .onSuccess { _index.value = it }
                        .onFailure {
                            logcat(LogPriority.ERROR, it) { "Could not read the EhViewer favorites" }
                            // A damaged copy: the next sync downloads it again instead of trusting its ETag
                            clearLocked()
                        }
                }
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    /** Waits until the copy kept from the last sync is read: [index] is empty before. */
    suspend fun awaitLoaded() = loaded.await()

    /** Downloads the index EhViewer uploaded, if it changed. An index removed from OneDrive is removed here too. */
    suspend fun sync() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!preferences.showEhViewerFavorites.get()) return@withLock
            val eTag = oneDriveSyncClient.eTag(EhFavoritesIndexFile.PATH)
            if (eTag == null) {
                // EhViewer stopped sharing its favorites, or its cloud data was cleared
                clearLocked()
                return@withLock
            }
            if (eTag == preferences.ehViewerFavoritesETag.get() && file.exists()) return@withLock
            val bytes = oneDriveSyncClient.readBytes(EhFavoritesIndexFile.PATH)
            if (bytes == null) {
                clearLocked()
                return@withLock
            }
            val index = buildIndex(bytes)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            _index.value = index
            preferences.ehViewerFavoritesETag.set(eTag)
            logcat(LogPriority.INFO) { "EhViewer favorites: ${index.file.galleries.size} galleries" }
        }
    }

    /**
     * Forgets the downloaded favorites after signing out or switching the OneDrive account, so no favorites of the
     * previous account stay marked.
     */
    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { clearLocked() } }

    private fun clearLocked() {
        file.delete()
        preferences.ehViewerFavoritesETag.delete()
        _index.value = EhFavoritesIndex.EMPTY
    }

    private fun buildIndex(bytes: ByteArray): EhFavoritesIndex {
        val text = GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
        val indexFile = json.decodeFromString(EhFavoritesIndexFile.serializer(), text)
        if (indexFile.version > EhFavoritesIndexFile.CURRENT_VERSION) {
            throw LibrarySyncException("The EhViewer favorites were written by a newer EhViewer version")
        }
        return EhFavoritesIndex(indexFile, normalizer)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** Traditional to simplified Chinese through ICU (Android 10+); older versions compare as written. */
        fun simplifiedChineseConverter(): (String) -> String {
            val converter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) icuConverter() else null
            return converter ?: { it }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun icuConverter(): ((String) -> String)? {
            val transliterator = runCatching { Transliterator.getInstance("Traditional-Simplified") }
                .onFailure { logcat(LogPriority.WARN, it) { "No traditional to simplified Chinese converter" } }
                .getOrNull()
                ?: return null
            return { text -> synchronized(transliterator) { transliterator.transliterate(text) } }
        }
    }
}
