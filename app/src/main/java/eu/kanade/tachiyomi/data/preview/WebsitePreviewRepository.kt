package eu.kanade.tachiyomi.data.preview

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.LinkedHashMap

/** Uses each installed source's own client and headers, including its cookies and domain handling. */
@Inject
@SingleIn(AppScope::class)
class WebsitePreviewRepository {
    private val cache = LinkedHashMap<Pair<Long, String>, WebsitePreviewPage>(16, 0.75f, true)

    /** The first preview page of [chapter], null when its website has no server thumbnails. */
    fun startUrl(source: HttpSource, chapter: SChapter): String? {
        if (HentaiHandPreviewParser.isSource(source.baseUrl)) {
            return HentaiHandPreviewParser.imagesUrl(source.baseUrl, chapter.url)
        }
        val url = runCatching { source.getChapterUrl(chapter) }.getOrNull() ?: return null
        return url.takeIf(WebsitePreviewParser::supports)
    }

    suspend fun load(source: HttpSource, url: String, refresh: Boolean = false): WebsitePreviewPage = withContext(
        Dispatchers.IO,
    ) {
        val parse: (String, String) -> WebsitePreviewPage = when {
            HentaiHandPreviewParser.supports(url) -> HentaiHandPreviewParser::parse
            WebsitePreviewParser.supports(url) -> WebsitePreviewParser::parse
            else -> return@withContext WebsitePreviewPage(emptyList(), null)
        }
        val key = source.id to url
        if (!refresh) synchronized(cache) { cache[key] }?.let { return@withContext it }
        val request = Request.Builder().url(url).headers(source.headers).build()
        val result = source.client.newCall(request).awaitSuccess().use { response ->
            parse(response.body.string(), response.request.url.toString())
        }
        synchronized(cache) {
            if (result.thumbnails.isNotEmpty()) cache[key] = result
            while (cache.size > 16) cache.remove(cache.keys.first())
        }
        result
    }
}
