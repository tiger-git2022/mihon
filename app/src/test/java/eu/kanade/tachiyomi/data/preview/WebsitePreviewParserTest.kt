package eu.kanade.tachiyomi.data.preview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebsitePreviewParserTest {
    private val url = "https://mirror.example/photos-index-aid-123.html"

    @Test
    fun `uses real page thumbnails and keeps the website order`() {
        val result = WebsitePreviewParser.parse(
            """
            <ul>
              <li class="gallary_item"><div class="pic_box"><a href="/photos-view-id-9.html">
                <img src="//cdn.example/data/t/12/3/first.webp"></a></div></li>
              <li class="gallary_item"><div class="pic_box"><a href="/photos-view-id-2.html">
                <img data-original="//cdn.example/data/t/12/3/second.jpg" src="/blank.gif"></a></div></li>
            </ul>
            """.trimIndent(),
            url,
        )
        assertEquals(listOf("first.webp", "second.jpg"), result.thumbnails.map { it.imageUrl.substringAfterLast('/') })
        assertTrue(result.thumbnails.all { it.imageUrl.startsWith("https://cdn.example/data/t/") })
        assertEquals("https://mirror.example/photos-view-id-9.html", result.thumbnails.first().pageUrl)
    }

    @Test
    fun `never treats original pages or gallery covers as page thumbnails`() {
        val html = """
            <div class="uwthumb"><img src="//cdn.example/data/t/cover.jpg"></div>
            <div class="gallary_item"><div class="pic_box"><a href="/photos-view-id-1.html">
              <img src="//cdn.example/data/12/3/original.jpg"></a></div></div>
        """.trimIndent()
        assertTrue(WebsitePreviewParser.parse(html, url).thumbnails.isEmpty())
    }

    @Test
    fun `ignores commented out desktop markup and duplicate links`() {
        val item = """
            <li class="gallary_item"><div class="pic_box"><a href="/photos-view-id-1.html">
              <img src="//cdn.example/data/t/p.jpg"></a></div></li>
        """.trimIndent()
        val result = WebsitePreviewParser.parse("<!-- $item -->$item$item", url)
        assertEquals(1, result.thumbnails.size)
    }

    @Test
    fun `pagination stays on the same gallery and advances exactly one page`() {
        val result = WebsitePreviewParser.parse(
            """
            <div class="paginator">
              <a href="/photos-index-aid-999-page-2.html">other gallery</a>
              <a href="https://other.example/photos-index-aid-123-page-2.html">other host</a>
              <a href="/photos-index-aid-123-page-3.html">last</a>
              <a href="/photos-index-page-2-aid-123.html">next</a>
            </div>
            """.trimIndent(),
            url,
        )
        assertEquals("https://mirror.example/photos-index-page-2-aid-123.html", result.nextUrl)
    }

    @Test
    fun `last preview page does not restart pagination`() {
        val result = WebsitePreviewParser.parse(
            """<div class="paginator"><a href="/photos-index-aid-123.html">first</a></div>""",
            "https://mirror.example/photos-index-page-2-aid-123.html",
        )
        assertNull(result.nextUrl)
    }

    @Test
    fun `unsupported sites and malformed urls have no preview capability`() {
        for (unsupported in listOf("https://example.org/g/123/", "https://example.org/album/123", "not a url")) {
            assertFalse(WebsitePreviewParser.supports(unsupported))
            assertTrue(WebsitePreviewParser.parse("<img src='/data/t/cover.jpg'>", unsupported).thumbnails.isEmpty())
        }
        assertTrue(WebsitePreviewParser.supports(url))
    }
}
