package eu.kanade.tachiyomi.data.sync

import java.text.Normalizer

/**
 * Hepburn romanization of names written in kana, the way E-Hentai titles spell them ("チビゴン" -> "chibigon",
 * "むなしむじょう" -> "munashimujou", "エッチ" -> "ecchi"), fork-only feature: a global search for an artist nobody
 * favorited in EhViewer can still try the romanized name. Kanji have several readings and are left alone.
 */
object KanaRomaji {
    private val DIGRAPHS = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo", "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "しゃ" to "sha", "しゅ" to "shu", "しぇ" to "she", "しょ" to "sho", "じゃ" to "ja", "じゅ" to "ju", "じぇ" to "je",
        "じょ" to "jo",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちぇ" to "che", "ちょ" to "cho", "ぢゃ" to "ja", "ぢゅ" to "ju", "ぢょ" to "jo",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo", "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo", "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo", "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "てぃ" to "ti", "でぃ" to "di", "とぅ" to "tu", "どぅ" to "du",
        "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo", "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo", "つぁ" to "tsa",
        "でゅ" to "dyu", "てゅ" to "tyu", "ふゅ" to "fyu", "ゔゅ" to "vyu", "いぇ" to "ye",
        "くぁ" to "kwa", "くぃ" to "kwi", "くぇ" to "kwe", "くぉ" to "kwo", "ぐぁ" to "gwa",
        "すぃ" to "si", "ずぃ" to "zi", "つぃ" to "tsi", "つぇ" to "tse", "つぉ" to "tso",
    )
    private val MONOGRAPHS = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'ゐ' to "i", 'ゑ' to "e", 'を' to "wo", 'ん' to "n", 'ゔ' to "vu",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o", 'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo", 'ゎ' to "wa",
    )
    private val ALLOWED = Regex("""[ぁ-ゖァ-ヺー・\s]+""")
    private val SPACES = Regex("""\s+""")

    /** The romanized [text] if it is only kana (and spaces, "・", "ー"), else null. */
    fun romanize(text: String): String? {
        // Half-width "ﾁﾋﾞｺﾞﾝ" and decomposed voiced marks become the usual kana
        val trimmed = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
        if (trimmed.isEmpty() || !ALLOWED.matches(trimmed)) return null
        // Katakana to hiragana, one table for both
        val kana = buildString { trimmed.forEach { append(if (it in 'ァ'..'ヶ') it - 0x60 else it) } }
        val out = StringBuilder()
        var i = 0
        var doubleNext = false
        while (i < kana.length) {
            val c = kana[i]
            val syllable = DIGRAPHS[kana.substring(i, minOf(i + 2, kana.length))]?.also { i += 2 }
                ?: MONOGRAPHS[c]?.also { i++ }
            when {
                syllable != null -> {
                    if (doubleNext) out.append(if (syllable.startsWith("ch")) 'c' else syllable[0])
                    doubleNext = false
                    out.append(syllable)
                }
                c == 'っ' -> {
                    doubleNext = true
                    i++
                }
                // A long vowel repeats the one before it
                c == 'ー' -> {
                    out.lastOrNull()?.takeIf { it in "aeiou" }?.let { out.append(it) }
                    i++
                }
                else -> {
                    out.append(' ')
                    i++
                }
            }
        }
        return out.toString().replace(SPACES, " ").trim().ifEmpty { null }
    }
}
