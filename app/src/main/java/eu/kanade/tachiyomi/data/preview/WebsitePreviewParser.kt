package eu.kanade.tachiyomi.data.preview

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** Fork: website-provided thumbnails only. Never derive a preview by requesting an original image. */
data class WebsiteThumbnail(val imageUrl: String, val pageUrl: String)

data class WebsitePreviewPage(val thumbnails: List<WebsiteThumbnail>, val nextUrl: String?)

internal object WebsitePreviewParser {
    private val galleryPath = Regex("/photos-index-(?:page-\\d+-)?aid-(\\d+)(?:-page-\\d+)?\\.html")
    private val pagePath = Regex("/photos-view-id-\\d+\\.html")

    fun supports(url: String): Boolean = url.toHttpUrlOrNull()?.encodedPath?.matches(galleryPath) == true

    fun parse(html: String, url: String): WebsitePreviewPage {
        val base = url.toHttpUrlOrNull() ?: return WebsitePreviewPage(emptyList(), null)
        val galleryId = galleryPath.matchEntire(base.encodedPath)?.groupValues?.get(1)
            ?: return WebsitePreviewPage(emptyList(), null)
        val document = Jsoup.parse(html, url)
        val thumbnails = document.select(".gallary_item .pic_box a[href]").mapNotNull { link ->
            val page = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            if (page.host != base.host || !page.encodedPath.matches(pagePath)) return@mapNotNull null
            val image = link.selectFirst("img") ?: return@mapNotNull null
            val imageUrl = sequenceOf("data-original", "data-src", "src")
                .map { image.absUrl(it) }
                .mapNotNull { it.toHttpUrlOrNull() }
                // WNACG distinguishes server thumbnails (/data/t/) from original pages (/data/).
                .firstOrNull { "/data/t/" in it.encodedPath } ?: return@mapNotNull null
            WebsiteThumbnail(imageUrl.toString(), page.toString())
        }.distinctBy { it.pageUrl }
        val currentPage = Regex("page-(\\d+)").find(base.encodedPath)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val next = document.select(".paginator a[href]").mapNotNull { link ->
            val candidate = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val candidateId = galleryPath.matchEntire(candidate.encodedPath)?.groupValues?.get(1)
            val number = Regex("page-(\\d+)").find(candidate.encodedPath)?.groupValues?.get(1)?.toIntOrNull()
            candidate.takeIf { it.host == base.host && candidateId == galleryId && number == currentPage + 1 }
        }.firstOrNull()?.toString()
        return WebsitePreviewPage(thumbnails, next)
    }
}
