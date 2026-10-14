package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EhFavoritesIndexTest {

    private val saimin = EhIndexedGallery(
        gid = 100,
        token = "0123456789",
        title = "(C102) [Circle Name (Sakka)] Saimin Seishidou (Original) [Chinese] [DL-ban]",
        titleJpn = "(C102) [サークル名 (作者)] 催眠性指導 (オリジナル) [中国翻訳] [DL版]",
        local = true,
        slot = 3,
    )
    private val bilingual = EhIndexedGallery(
        gid = 200,
        title = "[Someone] Kanojo no Himitsu | Her Secret [English]",
        slot = 0,
    )
    private val volumeOne = EhIndexedGallery(gid = 300, titleJpn = "[作者B] 放課後の約束 1", local = true)
    private val short = EhIndexedGallery(gid = 400, titleJpn = "[作者C] 妹", slot = 1)
    private val traditional = EhIndexedGallery(gid = 500, titleJpn = "[作者D] 肉體關係", slot = 2)

    // Stand-in for the ICU converter: the characters the tests use
    private val normalizer =
        EhTitleNormalizer { text -> text.replace('體', '体').replace('關', '关').replace('係', '系').replace('導', '导') }

    private val index = EhFavoritesIndex(
        EhFavoritesIndexFile(
            version = 1,
            slotNames = List(10) { "Slot $it" },
            galleries = listOf(saimin, bilingual, volumeOne, short, traditional),
        ),
        normalizer,
    )

    private fun gids(title: String, url: String = "/manga/1", authors: List<String> = emptyList()) =
        index.find(url, title, authors)?.galleries?.map { it.gid }

    @Test
    fun `gallery id of E-Hentai sources wins`() {
        val match = index.find("/g/100/0123456789/", "anything")
        assertEquals(listOf(100L), match?.galleries?.map { it.gid })
        assertTrue(match!!.byGalleryId)
        assertTrue(match.local)
        assertEquals(listOf(3), match.slots)
    }

    @Test
    fun `brackets of any kind and tags are ignored`() {
        assertEquals(listOf(100L), gids("【作者】催眠性指導【中文】"))
        assertEquals(listOf(100L), gids("［作者］催眠性指導（オリジナル）"))
        assertEquals(listOf(100L), gids("催眠性指导"))
    }

    @Test
    fun `romanized and Japanese titles both match`() {
        assertEquals(listOf(100L), gids("Saimin Seishidou"))
        assertEquals(listOf(100L), gids("ＳＡＩＭＩＮ　ＳＥＩＳＨＩＤＯＵ"))
        assertEquals(listOf(100L), gids("催眠性指導"))
    }

    @Test
    fun `censored characters match anything`() {
        assertEquals(listOf(100L), gids("催○性指導"))
        assertEquals(listOf(100L), gids("催●性指導"))
        assertEquals(listOf(100L), gids("催×性指導"))
    }

    @Test
    fun `either side of a bilingual title matches`() {
        assertEquals(listOf(200L), gids("Her Secret"))
        assertEquals(listOf(200L), gids("Kanojo no Himitsu"))
    }

    @Test
    fun `different volume numbers do not match`() {
        assertEquals(listOf(300L), gids("放課後の約束 1"))
        assertNull(gids("放課後の約束 2"))
        assertNull(gids("放課後の約束 第2巻 12"))
    }

    @Test
    fun `short titles need the same artist`() {
        assertNull(gids("妹"))
        assertNull(gids("妹", authors = listOf("Someone else")))
        assertEquals(listOf(400L), gids("妹", authors = listOf("作者C")))
    }

    @Test
    fun `traditional and simplified Chinese match`() {
        assertEquals(listOf(500L), gids("肉体关系"))
    }

    @Test
    fun `unrelated titles do not match`() {
        assertNull(gids("Completely Different Story"))
        assertNull(gids("催眠アプリ"))
        assertNull(EhFavoritesIndex.EMPTY.find("/g/100/0123456789/", "催眠性指導"))
    }

    @Test
    fun `artists come from the leading bracket group`() {
        assertEquals(setOf("circlename", "sakka"), normalizer.artists(saimin.title!!))
        assertEquals(setOf("作者b"), normalizer.artists(volumeOne.titleJpn!!))
        assertTrue(normalizer.artists("No brackets").isEmpty())
    }

    @Test
    fun `distance treats wildcards as any character`() {
        assertEquals(0, EhTitleSimilarity.distance("催*性指導", "催眠性指導", limit = 0))
        assertEquals(1, EhTitleSimilarity.distance("abcd", "abed", limit = 1))
        assertNull(EhTitleSimilarity.distance("abcd", "wxyz", limit = 2))
        assertFalse(EhTitleSimilarity.sameNumbers("vol1", "vol2"))
        assertTrue(EhTitleSimilarity.sameNumbers("vol01", "vol1"))
    }
}
