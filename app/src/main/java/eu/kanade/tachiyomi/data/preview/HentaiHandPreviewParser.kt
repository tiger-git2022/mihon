package eu.kanade.tachiyomi.data.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Fork: HentaiHand's page list (`/api/comics/<chapter>/images`) carries a server thumbnail next to each page
 * (`thumbnail_url`, about a tenth of `source_url`). The extension reads the same list in the same order, so a
 * thumbnail's position is its page index in the reader.
 */
internal object HentaiHandPreviewParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val imagesPath = Regex("/api/comics/[^?#]+/images")

    fun isSource(baseUrl: String): Boolean = baseUrl.toHttpUrlOrNull()?.host?.let(::isHost) == true

    private fun isHost(host: String) = host == "hentaihand.com" || host.endsWith(".hentaihand.com")

    /** The page list of a chapter; its url is the comic slug, or the slug and the chapter slug. */
    fun imagesUrl(baseUrl: String, chapterUrl: String): String? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val path = chapterUrl.trim('/')
        val segments = path.split('/')
        if ("://" in path || segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return HttpUrl.Builder().scheme(base.scheme).host(base.host).port(base.port)
            .addPathSegments("api/comics").addPathSegments(path).addPathSegment("images")
            .build().toString()
    }

    fun supports(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return isHost(parsed.host) && parsed.encodedPath.matches(imagesPath)
    }

    fun parse(body: String, url: String): WebsitePreviewPage {
        if (!supports(url)) return WebsitePreviewPage(emptyList(), null)
        val images = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?.get("images") as? JsonArray ?: return WebsitePreviewPage(emptyList(), null)
        val thumbnails = images.mapNotNull { element ->
            val image = element as? JsonObject ?: return@mapNotNull null
            val page = image.string("source_url")?.toHttpUrlOrNull() ?: return@mapNotNull null
            val thumbnail = image.string("thumbnail_url")?.toHttpUrlOrNull() ?: return@mapNotNull null
            // Only a separate server thumbnail, never the page itself.
            if (thumbnail == page) return@mapNotNull null
            WebsiteThumbnail(thumbnail.toString(), page.toString())
        }
        // A page without a thumbnail would shift every later index, opening the wrong page.
        if (thumbnails.size != images.size) return WebsitePreviewPage(emptyList(), null)
        if (thumbnails.distinctBy { it.pageUrl }.size != thumbnails.size) return WebsitePreviewPage(emptyList(), null)
        return WebsitePreviewPage(thumbnails, null)
    }

    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)
        ?.takeIf { it.isString }?.contentOrNull
}
