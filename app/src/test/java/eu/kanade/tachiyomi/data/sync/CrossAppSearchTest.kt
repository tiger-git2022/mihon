package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CrossAppSearchTest {
    @Test
    fun `core title drops every bracket group`() {
        assertEquals(
            "デリヘルチケット使ったら・・・来たのは大好きな推しでした。",
            CrossAppSearch.coreTitle("[ミルクレープ (チビゴン)] デリヘルチケット使ったら・・・来たのは大好きな推しでした。 [中国翻訳]"),
        )
        assertEquals(
            "催眠性指導 宮島桜の場合",
            CrossAppSearch.coreTitle("(C97) [サークルA (作者A)] 催眠性指導 宮島桜の場合 (オリジナル) [中国翻訳] [DL版]"),
        )
        assertEquals(
            "Kanojo no Himitsu",
            CrossAppSearch.coreTitle("[Circle B] Kanojo no Himitsu | Her Secret [English]"),
        )
        assertEquals("Kanojo no Himitsu", CrossAppSearch.coreTitle("Kanojo no Himitsu|Her Secret [English]"))
        assertEquals("ドスケベシスターズの誘惑-背徳の聖女ハーレム-", CrossAppSearch.coreTitle("【デンジャラス・ラビット】ドスケベシスターズの誘惑-背徳の聖女ハーレム-（无修正）"))
        // Nothing but brackets: kept as it is
        assertEquals("[作者E]", CrossAppSearch.coreTitle("[作者E]"))
    }

    @Test
    fun `artist comes from the leading group`() {
        assertEquals("チビゴン", CrossAppSearch.artist("[ミルクレープ (チビゴン)] デリヘルチケット使ったら"))
        assertEquals("デンジャラス・ラビット", CrossAppSearch.artist("[デンジャラス・ラビット] ドスケベシスターズの誘惑"))
        assertEquals("作者A", CrossAppSearch.artist("(C97) [サークルA (作者A)] 催眠性指導"))
        // A translator group first: the next group names the artist
        assertEquals("作者A", CrossAppSearch.artist("[脸肿汉化组] (C97) [サークルA (作者A)] 催眠性指導"))
        assertEquals("作者A", CrossAppSearch.artist("[AI機翻] [サークルA (作者A)] 催眠性指導"))
        assertEquals("作者A", CrossAppSearch.artist("[嵌字组] [サークルA (作者A)] 催眠性指導"))
        assertEquals("飞鸟", CrossAppSearch.artist("催眠性指導", listOf("飞鸟")))
        assertNull(CrossAppSearch.artist("催眠性指導"))
    }
}
