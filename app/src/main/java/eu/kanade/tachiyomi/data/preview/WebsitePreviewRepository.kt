package eu.kanade.tachiyomi.data.preview

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.awaitSuccess
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

    suspend fun load(source: HttpSource, url: String, refresh: Boolean = false): WebsitePreviewPage = withContext(
        Dispatchers.IO,
    ) {
        if (!WebsitePreviewParser.supports(url)) return@withContext WebsitePreviewPage(emptyList(), null)
        val key = source.id to url
        if (!refresh) synchronized(cache) { cache[key] }?.let { return@withContext it }
        val request = Request.Builder().url(url).headers(source.headers).build()
        val result = source.client.newCall(request).awaitSuccess().use { response ->
            WebsitePreviewParser.parse(response.body.string(), response.request.url.toString())
        }
        synchronized(cache) {
            if (result.thumbnails.isNotEmpty()) cache[key] = result
            while (cache.size > 16) cache.remove(cache.keys.first())
        }
        result
    }
}
