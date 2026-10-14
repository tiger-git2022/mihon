package eu.kanade.tachiyomi.data.download

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Sizes of downloaded chapters for the chapter list (fork-only feature): the files of the chapter folder, or its
 * archive. Sources give no size before a download, so chapters not downloaded show none.
 */
@Inject
@SingleIn(AppScope::class)
class ChapterDownloadSizes(
    private val provider: DownloadProvider,
    private val sourceManager: SourceManager,
) {
    // Chapter id -> bytes, until the chapter is no longer downloaded
    private val cache = ConcurrentHashMap<Long, Long>()

    suspend fun size(manga: Manga, chapter: Chapter): Long? = withContext(Dispatchers.IO) {
        cache[chapter.id]?.let { return@withContext it }
        val source = sourceManager.getOrStub(manga.source)
        val dir = provider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
            ?: return@withContext null
        val size = if (dir.isDirectory) dir.listFiles().orEmpty().sumOf { it.length() } else dir.length()
        size.also { cache[chapter.id] = it }
    }

    /** A deleted chapter downloaded again is counted again. */
    fun forget(chapterId: Long) {
        cache.remove(chapterId)
    }
}

/** The size of a downloaded chapter, e.g. "12 MB", null while it is not known or not downloaded. */
@Composable
fun rememberChapterDownloadSize(manga: Manga, chapter: Chapter, downloaded: Boolean): String? {
    val context = LocalContext.current
    val sizes = remember { context.appGraph.chapterDownloadSizes }
    val bytes by produceState<Long?>(null, chapter.id, downloaded) {
        if (!downloaded) sizes.forget(chapter.id)
        value = if (downloaded) runCatching { sizes.size(manga, chapter) }.getOrNull() else null
    }
    return bytes?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
}
