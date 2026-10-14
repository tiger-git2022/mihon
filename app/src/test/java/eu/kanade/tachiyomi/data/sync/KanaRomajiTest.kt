package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KanaRomajiTest {
    @Test
    fun `kana names romanize as E-Hentai spells them`() {
        assertEquals("chibigon", KanaRomaji.romanize("チビゴン"))
        assertEquals("munashimujou", KanaRomaji.romanize("むなしむじょう"))
        assertEquals("ecchi", KanaRomaji.romanize("エッチ"))
        assertEquals("kyouryuu", KanaRomaji.romanize("きょうりゅう"))
        assertEquals("matsukaze", KanaRomaji.romanize("まつかぜ"))
        assertEquals("yappari", KanaRomaji.romanize("ヤッパリ"))
        assertEquals("sumaato fon", KanaRomaji.romanize("スマート・フォン"))
        assertEquals("tatapon", KanaRomaji.romanize("  たたぽん "))
    }

    @Test
    fun `anything but kana is left alone`() {
        assertNull(KanaRomaji.romanize("宮島桜"))
        assertNull(KanaRomaji.romanize("チビゴン 本"))
        assertNull(KanaRomaji.romanize("chibigon"))
        assertNull(KanaRomaji.romanize(" "))
    }
}
