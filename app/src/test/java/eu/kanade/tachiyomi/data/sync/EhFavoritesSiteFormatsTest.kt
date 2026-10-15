package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * EhViewer favorites against titles written the way other sources write them: JMComic (禁漫), wnacg (绅士漫画),
 * PicACG (哔咔), HentaiHand and Hitomi. The works are made up; the title formats follow those sites.
 */
class EhFavoritesSiteFormatsTest {

    private val saimin = EhIndexedGallery(
        gid = 1,
        title = "(C97) [Circle A (Sakka A)] Saimin Seishidou Miyajima Sakura no Baai (Original) [Chinese] [Lianzhong]",
        titleJpn = "(C97) [サークルA (作者A)] 催眠性指導 宮島桜の場合 (オリジナル) [中国翻訳]",
        local = true,
        slot = 2,
    )
    private val himitsu = EhIndexedGallery(
        gid = 2,
        title = "[Circle B (Sakka B)] Kanojo no Himitsu | Her Secret [English] [Digital]",
        titleJpn = "[サークルB (作者B)] 彼女の秘密 [英訳] [DL版]",
        slot = 0,
    )
    private val app = EhIndexedGallery(
        gid = 3,
        title = "[Sakka C] Saimin Apuri de Namaiki na Imouto o Wakaraseru [Chinese] [Decensored]",
        titleJpn = "[作者C] 催眠アプリで生意気な妹をわからせる [中国翻訳] [無修正]",
        local = true,
    )
    private val promise =
        EhIndexedGallery(
            gid = 4,
            title = "[Sakka D] Houkago no Yakusoku 2 [Chinese]",
            titleJpn = "[作者D] 放課後の約束 2 [中国翻訳]",
            slot = 1,
        )
    private val homework = EhIndexedGallery(gid = 5, titleJpn = "[作者E] 夏休みの宿題 [中国翻訳]", slot = 3)

    // Stand-in for ICU's traditional to simplified conversion of the characters used here
    private val normalizer = EhTitleNormalizer { text ->
        text.replace('導', '导').replace('島', '岛').replace('宮', '宫').replace('場', '场')
    }

    private val galleries = listOf(saimin, himitsu, app, promise, homework)
    private val index = EhFavoritesTestIndexes.oneShard(galleries, normalizer)
    private val sharded = EhFavoritesTestIndexes.shardPerGallery(galleries, normalizer)

    private fun match(title: String, vararg authors: String): List<Long>? {
        val gids = index.find("/comic/1", title, authors.toList())?.galleries?.map { it.gid }
        // Split into shards, the favorites match the same
        assertEquals(gids, sharded.find("/comic/1", title, authors.toList())?.galleries?.map { it.gid })
        return gids
    }

    @Test
    fun `JMComic copies the Japanese title or converts it to simplified Chinese`() {
        assertEquals(listOf(1L), match("(C97) [サークルA (作者A)] 催眠性指導 宮島桜の場合 (オリジナル) [中国翻訳]"))
        // 樱 is the simplified form of the Japanese 桜, 的 a translation of の
        assertEquals(listOf(1L), match("[作者A] 催眠性指导 宫岛樱的场合 [禁漫汉化组]"))
        assertEquals(listOf(3L), match("[作者C] 催○アプリで生意気な妹をわからせる [中国翻译]"))
    }

    @Test
    fun `wnacg puts the translator group first`() {
        assertEquals(listOf(1L), match("[脸肿汉化组] (C97) [サークルA (作者A)] 催眠性指導 宮島桜の場合 (オリジナル) [中国翻訳]"))
        assertEquals(setOf("サークルa", "作者a"), normalizer.artists("[脸肿汉化组] (C97) [サークルA (作者A)] 催眠性指導"))
        assertEquals(listOf(3L), match("[作者C] 催*アプリで生意気な妹をわからせる [中国翻訳] [無修正]"))
        assertNull(match("[作者D] 放課後の約束 3 [中国翻訳]"))
    }

    @Test
    fun `PicACG shortens titles and keeps the artist in its own field`() {
        assertEquals(listOf(1L), match("催眠性指導", "作者A"))
        assertEquals(listOf(3L), match("催眠アプリで生意気な妹をわからせる（无修正）", "作者C"))
        // Without the artist a shortened title is not enough
        assertNull(match("催眠性指導"))
        // An equal short title by another artist is another work
        assertNull(match("夏休みの宿題", "作者F"))
        assertEquals(listOf(5L), match("夏休みの宿題", "作者E"))
    }

    @Test
    fun `HentaiHand uses the romanized or English title`() {
        assertEquals(listOf(1L), match("[Circle A (Sakka A)] Saimin Seishidou Miyajima Sakura no Baai [Chinese]"))
        assertEquals(listOf(2L), match("Her Secret", "Sakka B"))
    }

    @Test
    fun `Hitomi shows the title without brackets`() {
        assertEquals(listOf(1L), match("Saimin Seishidou Miyajima Sakura no Baai", "sakka a"))
        assertEquals(listOf(1L), match("催眠性指導 宮島桜の場合", "作者a"))
        assertEquals(listOf(2L), match("Kanojo no Himitsu | Her Secret"))
    }

    @Test
    fun `another book of the same series does not match`() {
        assertNull(match("催眠性指導 宮島椿の場合"))
        assertNull(match("催眠性指導 宮島椿の場合", "作者A"))
        assertNull(match("Saimin Seishidou Miyajima Tsubaki no Baai"))
    }
}
