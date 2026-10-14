package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EhNameAliasesTest {
    private fun gallery(
        gid: Long,
        title: String?,
        titleJpn: String?,
    ) = EhIndexedGallery(gid = gid, title = title, titleJpn = titleJpn)

    private val aliases = EhNameAliases.build(
        listOf(
            gallery(
                1,
                "[Mille-Crepe (Chibigon)] SNS de Uraaka Tokutei shita Hitozuma o Hamemakutta Hanashi. [Chinese] [小衣ゆうき个人机翻润色]",
                "[ミルクレープ (チビゴン)] SNSで裏垢特定した人妻をハメまくった話。 [中国翻訳] [小衣ゆうき个人机翻润色]",
            ),
            gallery(
                2,
                "(C102) [Mille-Crepe (Chibigon)] Tenchou... Tsugi wa Shippai Shimasen kara",
                "(C102) [ミルクレープ (チビゴン)] 店長…次は失敗しませんから",
            ),
            // A translator group first, an artist without a circle
            gallery(3, "[Lazy Translations] [Munashimujou] Oyasumi", "[懒人汉化] [むなしむじょう] おやすみ"),
            // Shapes that differ do not pair
            gallery(4, "[Some Circle] Title Four", "[サークル (作者)] タイトル四"),
            // Old index: no default title yet, both titles are the Japanese one
            gallery(5, "[ミルクレープ (チビゴン)] 同じ", "[ミルクレープ (チビゴン)] 同じ"),
            gallery(6, null, "[作者六] 六"),
            // "Digital" in a circle name is no version marker
            gallery(
                7,
                "[Digital Lover (Nakajima Yuka)] Seifuku Shokushu 10 [Chinese]",
                "[Digital Lover (なかじまゆか)] 制服触手10 [中国翻訳]",
            ),
            gallery(8, "[Chinese] [Kotoba] Seven", "[中国翻訳] [ことば] 七"),
        ),
    )

    @Test
    fun `artists and circles map both ways`() {
        assertEquals("Chibigon", aliases.alternative("チビゴン"))
        assertEquals("チビゴン", aliases.alternative("chibigon"))
        assertEquals("チビゴン", aliases.alternative("  CHIBIGON "))
        assertEquals("Mille-Crepe", aliases.alternative("ミルクレープ"))
        assertEquals("むなしむじょう", aliases.alternative("Munashimujou"))
    }

    @Test
    fun `translation markers are only whole groups`() {
        assertEquals("なかじまゆか", aliases.alternative("Nakajima Yuka"))
        assertEquals("ことば", aliases.alternative("Kotoba"))
    }

    @Test
    fun `a full title is looked up by its core`() {
        assertEquals(
            "SNS de Uraaka Tokutei shita Hitozuma o Hamemakutta Hanashi.",
            aliases.alternative("[ミルクレープ (チビゴン)] SNSで裏垢特定した人妻をハメまくった話。 [中国翻訳]"),
        )
    }

    @Test
    fun `titles map both ways`() {
        assertEquals(
            "SNS de Uraaka Tokutei shita Hitozuma o Hamemakutta Hanashi.",
            aliases.alternative("SNSで裏垢特定した人妻をハメまくった話。"),
        )
        assertEquals(
            "SNSで裏垢特定した人妻をハメまくった話。",
            aliases.alternative("SNS de Uraaka Tokutei shita Hitozuma o Hamemakutta Hanashi."),
        )
    }

    @Test
    fun `unknown and unpaired names have none`() {
        assertNull(aliases.alternative("作者"))
        assertNull(aliases.alternative("Some Circle"))
        assertNull(aliases.alternative("作者六"))
        assertNull(aliases.alternative("Lazy Translations"))
        assertNull(EhNameAliases.EMPTY.alternative("チビゴン"))
    }
}
