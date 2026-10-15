package eu.kanade.tachiyomi.data.sync

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil

/**
 * Turns gallery and manga titles into comparable keys, so the same work matches across sites that write its title
 * differently (fork-only feature):
 * - full-width and half-width forms, upper and lower case, traditional and simplified Chinese ([toSimplified]) and
 *   Japanese forms of Chinese characters (桜/樱, 気/气, which sites converting titles to simplified Chinese use) are
 *   unified;
 * - bracketed parts (event, circle and artist, parody, language, translator: `(C102) [Circle (Artist)] Title (Parody)
 *   [Chinese]`, also with 【】 or full-width brackets) are dropped, and `English | Japanese` titles are split;
 * - punctuation, spaces and decorations like ♡ are dropped;
 * - censor marks (催○, 催●, 催×…) become the wildcard [WILDCARD], which matches any one character.
 */
class EhTitleNormalizer(private val toSimplified: (String) -> String = { it }) {

    /**
     * The keys of a title, one per `|` separated part: without any bracketed part, and with the text in round brackets
     * kept (`Title (Part 1)` on one site is `Title Part 1` on another; a leading `(event)` is still dropped). The
     * whole title is the fallback for a title made only of brackets.
     */
    fun keys(title: String): List<String> {
        val text = Normalizer.normalize(title, Normalizer.Form.NFKC)
        val withoutAny = removeGroups(text, INNERMOST_GROUP)
        val keepingRound = removeGroups(text.replaceFirst(LEADING_EVENT, " "), INNERMOST_NON_ROUND_GROUP)
        val parts = (withoutAny.split('|') + keepingRound.split('|'))
            .map(::clean)
            .filter { it.length >= MIN_KEY_LENGTH }
        return parts.ifEmpty { listOf(clean(text)).filter { it.length >= MIN_KEY_LENGTH } }.distinct()
    }

    /**
     * Circles and artists of an E-Hentai style title: the [...] groups before the title, `[Circle (Artist)]` giving
     * both. Events `(C102)` and translator groups (`[脸肿汉化组]`, which some sites put first) are skipped.
     */
    fun artists(title: String): Set<String> {
        var rest = Normalizer.normalize(title, Normalizer.Form.NFKC)
        val names = ArrayList<String>()
        while (true) {
            val match = LEADING_GROUP.find(rest) ?: break
            rest = rest.substring(match.range.last + 1)
            val group = match.value.trim()
            if (group.startsWith("(")) continue
            val inner = group.substring(1, group.length - 1)
            if (TRANSLATOR.containsMatchIn(inner)) continue
            val parens = INNER_PARENS.find(inner)
            if (parens != null) {
                names += inner.substring(0, parens.range.first)
                names += parens.groupValues[1]
            } else {
                names += inner
            }
        }
        return names.flatMap { it.split(',', '、', '&') }.map(::clean).filter { it.isNotEmpty() }.toSet()
    }

    /** A name from the author or artist field of a manga. */
    fun name(name: String): String = clean(Normalizer.normalize(name, Normalizer.Form.NFKC))

    private fun clean(text: String): String {
        val simplified = toSimplified(text.lowercase(Locale.ROOT))
        return buildString(simplified.length) {
            simplified.forEach { char ->
                when {
                    char in CENSOR_MARKS -> append(WILDCARD)
                    char.isLetterOrDigit() -> append(JAPANESE_FORMS[char] ?: char)
                }
            }
        }
    }

    private fun removeGroups(text: String, group: Regex): String {
        var current = text
        while (true) {
            val next = group.replace(current, " ")
            if (next == current) return current
            current = next
        }
    }

    companion object {
        const val WILDCARD = '*'
        private const val MIN_KEY_LENGTH = 1

        // After NFKC: full-width brackets are already ASCII; 【】 and 〔〕 are not
        private val INNERMOST_GROUP = Regex("""[(\[{【〔][^()\[\]{}【】〔〕]*[)\]}】〕]""")
        private val INNERMOST_NON_ROUND_GROUP = Regex("""[\[{【〔][^\[\]{}【】〔〕]*[\]}】〕]""")
        private val LEADING_EVENT = Regex("""^\s*\([^)]*\)""")
        private val LEADING_GROUP = Regex("""^\s*(?:\([^)]*\)|[\[【][^\]】]*[\]】])""")
        private val TRANSLATOR = Regex(
            "汉化|漢化|翻訳|翻译|翻譯|中文|中国|中國|chinese|english|個人|个人|机翻|機翻|嵌字|修正|dl版|digital",
            RegexOption.IGNORE_CASE,
        )
        private val INNER_PARENS = Regex("""\(([^)]*)\)""")
        private const val CENSOR_MARKS = "○◯〇●◎◉*×✕✖■□◆◇"

        /**
         * Japanese forms of Chinese characters and their simplified Chinese form, where the traditional to simplified
         * conversion does not cover them (it maps 櫻 to 樱, not 桜).
         */
        private val JAPANESE_FORMS: Map<Char, Char> = (
            "気气発发変变様样続续単单関关楽乐図图処处対对帰归戦战歳岁悪恶済济広广拡扩鉄铁転转伝传売卖読读説说" +
                "隠隐縁缘涙泪剣剑薬药験验駅驿桜樱沢泽浜滨栄荣営营労劳覚觉権权歓欢観观黒黑弾弹姫姬恵惠険险検检" +
                "鶏鸡圧压囲围遅迟亜亚悩恼脳脑猟猎獣兽繊纤絵绘経经総总緑绿縄绳継继粋粹穏稳稲稻県县斎斋斉齐歩步" +
                "毎每渋涩満满瀬濑焼烧犠牺獄狱畳叠竜龙糸丝紅红絶绝聴听臓脏舗铺芸艺蔵藏蛍萤衆众覇霸訳译証证豊丰" +
                "賛赞辺边郷乡酔醉醸酿録录闘斗陥陷隣邻雑杂霊灵頼赖顔颜顕显類类飲饮騒骚髪发麺面黙默齢龄拝拜抜拔" +
                "択择拠据捜搜掲揭揺摇撃击摂摄擬拟晩晚暁晓桟栈歯齿殺杀渇渴渓溪滝泷漢汉為为煙烟円圆実实仏佛" +
                "仮假価价偽伪児儿剤剂勧劝収收団团塁垒壊坏奨奖嬢娘寛宽巣巢帯带庁厅廃废従从徳德応应懐怀戯戏挿插" +
                "挙举捨舍掃扫敵敌暦历査查殻壳浄净涼凉渉涉渦涡湯汤漁渔潔洁獲获現现疎疏痩瘦砲炮禍祸穂穗粧妆"
            ).chunked(2).associate { it[0] to it[1] }
    }
}

/** Similarity rules between two keys, see [EhFavoritesIndex.find]. */
internal object EhTitleSimilarity {
    private val DIGITS = Regex("""\d+""")

    /**
     * Edit distance where [EhTitleNormalizer.WILDCARD] matches any character; null once above [limit]. Adding or
     * removing a Chinese character costs [HAN_EDIT] and replacing one by another twice that: one of them carries a word
     * (宮島桜の場合 and 宮島椿の場合 are two books of a series), while a kana, letter or punctuation mark differs
     * between sites more easily and costs 1.
     */
    fun distance(a: String, b: String, limit: Int): Int? {
        if (kotlin.math.abs(a.length - b.length) > limit) return null
        var previous = IntArray(b.length + 1)
        for (j in 1..b.length) previous[j] = previous[j - 1] + edit(b[j - 1])
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = previous[0] + edit(a[i - 1])
            var rowMin = current[0]
            for (j in 1..b.length) {
                current[j] = minOf(
                    previous[j - 1] + substitution(a[i - 1], b[j - 1]),
                    previous[j] + edit(a[i - 1]),
                    current[j - 1] + edit(b[j - 1]),
                )
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > limit) return null
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length].takeIf { it <= limit }
    }

    private fun substitution(a: Char, b: Char): Int = when {
        a == b || a == EhTitleNormalizer.WILDCARD || b == EhTitleNormalizer.WILDCARD -> 0
        isHan(a) && isHan(b) -> 2 * HAN_EDIT
        else -> 1
    }

    private fun edit(char: Char): Int = if (isHan(char)) HAN_EDIT else 1

    private fun isHan(char: Char) = Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN

    private const val HAN_EDIT = 3

    /** Titles shorter than this by different artists are different works, even when equal. */
    private const val MIN_CONFLICT_LENGTH = 8

    /**
     * Edits allowed between two keys of the same work: about one in seven characters, one in four when the artists
     * agree, none for short titles unless the artists agree. When both name different artists only an equal title of
     * at least [MIN_CONFLICT_LENGTH] characters matches (the artist is romanized differently). Titles of one or two
     * characters only match exactly with the same artist.
     */
    fun allowedEdits(length: Int, artistsAgree: Boolean, artistsConflict: Boolean): Int? = when {
        length <= 2 -> if (artistsAgree) 0 else null
        artistsConflict -> if (length >= MIN_CONFLICT_LENGTH) 0 else null
        length <= 5 -> if (artistsAgree) 1 else 0
        artistsAgree -> length / 4
        else -> length / 7
    }

    /** Different numbers (volume 1 and 2, chapter 3 and 4) are different works. */
    fun sameNumbers(a: String, b: String): Boolean {
        val numbersA = DIGITS.findAll(a).map { it.value.trimStart('0') }.toList()
        val numbersB = DIGITS.findAll(b).map { it.value.trimStart('0') }.toList()
        return numbersA.isEmpty() || numbersB.isEmpty() || numbersA == numbersB
    }

    fun mostlyWildcards(key: String) = key.count { it == EhTitleNormalizer.WILDCARD } * 2 > key.length

    /** Shortest title that may match a longer one it is part of, see [contains]. */
    const val MIN_CONTAINED_LENGTH = 4

    /** Whether [short] appears in [long], wildcards matching any character. */
    fun contains(long: String, short: String): Boolean {
        if (short.length > long.length) return false
        return (0..long.length - short.length).any { start ->
            short.indices.all { i ->
                val a = long[start + i]
                val b = short[i]
                a == b || a == EhTitleNormalizer.WILDCARD || b == EhTitleNormalizer.WILDCARD
            }
        }
    }

    fun minShared(bigrams: Int) = maxOf(1, ceil(bigrams * 0.3).toInt())
}
