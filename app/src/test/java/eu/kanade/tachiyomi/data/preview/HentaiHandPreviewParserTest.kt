package eu.kanade.tachiyomi.data.preview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HentaiHandPreviewParserTest {
    private val url = "https://hentaihand.com/api/comics/some-comic/images"

    @Test
    fun `builds the page list url from the chapter url`() {
        assertTrue(HentaiHandPreviewParser.isSource("https://hentaihand.com"))
        assertFalse(HentaiHandPreviewParser.isSource("https://nothentaihand.com"))
        assertEquals(url, HentaiHandPreviewParser.imagesUrl("https://hentaihand.com", "some-comic"))
        assertEquals(
            "https://hentaihand.com/api/comics/some-comic/chapter-2/images",
            HentaiHandPreviewParser.imagesUrl("https://hentaihand.com", "some-comic/chapter-2"),
        )
        assertNull(HentaiHandPreviewParser.imagesUrl("https://hentaihand.com", "../admin"))
        assertNull(HentaiHandPreviewParser.imagesUrl("https://hentaihand.com", ""))
        assertTrue(HentaiHandPreviewParser.supports(url))
        assertFalse(HentaiHandPreviewParser.supports("https://example.com/api/comics/some-comic/images"))
    }

    @Test
    fun `uses the server thumbnails in the page order of the extension`() {
        val result = HentaiHandPreviewParser.parse(
            """
            {"comic": {"id": 1}, "images": [
              {"page": 2, "source_url": "https://cdn.example/images/1/2.webp",
               "thumbnail_url": "https://cdn.example/thumbnails/1/2.webp"},
              {"page": 1, "source_url": "https://cdn.example/images/1/1.webp",
               "thumbnail_url": "https://cdn.example/thumbnails/1/1.webp"}
            ]}
            """.trimIndent(),
            url,
        )
        assertEquals(
            listOf("https://cdn.example/thumbnails/1/2.webp", "https://cdn.example/thumbnails/1/1.webp"),
            result.thumbnails.map { it.imageUrl },
        )
        assertNull(result.nextUrl)
    }

    @Test
    fun `shows nothing when a page has no separate thumbnail`() {
        val missing = HentaiHandPreviewParser.parse(
            """{"images": [{"page": 1, "source_url": "https://cdn.example/1.webp"},
            {"page": 2, "source_url": "https://cdn.example/2.webp",
            "thumbnail_url": "https://cdn.example/t/2.webp"}]}""",
            url,
        )
        assertTrue(missing.thumbnails.isEmpty())
        val same = HentaiHandPreviewParser.parse(
            """{"images": [{"page": 1, "source_url": "https://cdn.example/1.webp",
            "thumbnail_url": "https://cdn.example/1.webp"}]}""",
            url,
        )
        assertTrue(same.thumbnails.isEmpty())
        assertTrue(HentaiHandPreviewParser.parse("not json", url).thumbnails.isEmpty())
    }
}
