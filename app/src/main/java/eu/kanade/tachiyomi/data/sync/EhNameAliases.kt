package eu.kanade.tachiyomi.data.sync

import java.text.Normalizer

/**
 * The other spelling of artist, circle and title names, learned from the EhViewer favorites (fork-only feature):
 * a gallery listed with its default title and its Japanese one pairs "[Mille-Crepe (Chibigon)] SNS de ..." with
 * "[ミルクレープ (チビゴン)] SNSで...", so "Chibigon" and "チビゴン" name the same artist. A global search that finds
 * nothing in a source searches it again with the other spelling, see [alternative].
 */
class EhNameAliases private constructor(private val aliases: Map<String, String>) {

    /** The other spelling of [query] (a whole artist, circle or title name), null if none is known. */
    fun alternative(query: String): String? = aliases[key(query)]

    val size: Int
        get() = aliases.size

    companion object {
        val EMPTY = EhNameAliases(emptyMap())

        private val LEADING_GROUP = Regex("""^\s*(?:(?:\([^)]*\)|（[^）]*）)\s*)*[\[【［]([^\]】］]+)[\]】］]""")
        private val INNER_PARENS = Regex("""[(（]([^)）]+)[)）]""")
        private val SPACES = Regex("""\s+""")
        private const val MIN_TITLE_LENGTH = 4

        // A leading group naming translators or a language, as in CrossAppSearch
        private val NOT_ARTIST = Regex(
            """汉化|漢化|翻訳|翻译|翻譯|中文|中国|中國|chinese|english|translat|個人|个人|机翻|機翻|嵌字|修正|dl版|digital""",
            RegexOption.IGNORE_CASE,
        )

        fun build(galleries: List<EhIndexedGallery>): EhNameAliases {
            // name -> other spelling -> galleries giving that pair; the spelling most galleries agree on wins
            val votes = HashMap<String, HashMap<String, Int>>()
            fun vote(a: String?, b: String?) {
                if (a.isNullOrBlank() || b.isNullOrBlank()) return
                val keyA = key(a)
                val keyB = key(b)
                if (keyA == keyB) return
                votes.getOrPut(keyA) { HashMap() }.merge(b.trim(), 1, Int::plus)
                votes.getOrPut(keyB) { HashMap() }.merge(a.trim(), 1, Int::plus)
            }
            galleries.forEach { gallery ->
                val default = gallery.title
                val japanese = gallery.titleJpn
                if (default.isNullOrBlank() || japanese.isNullOrBlank()) return@forEach
                val a = names(default)
                val b = names(japanese)
                // Only groups of the same shape pair up: "[Circle (Artist)]" with "[サークル (作者)]"
                if (a.circle != null && b.circle != null) vote(a.circle, b.circle)
                if ((a.circle == null) == (b.circle == null)) vote(a.artist, b.artist)
                val titleA = CrossAppSearch.coreTitle(default)
                val titleB = CrossAppSearch.coreTitle(japanese)
                if (titleA.length >= MIN_TITLE_LENGTH && titleB.length >= MIN_TITLE_LENGTH) vote(titleA, titleB)
            }
            return EhNameAliases(votes.mapValues { (_, others) -> others.maxBy { it.value }.key })
        }

        private class Names(val circle: String?, val artist: String?)

        /** Circle and artist of the first leading bracket group that names them, "[Circle (Artist)]". */
        private fun names(title: String): Names {
            var rest = title
            repeat(3) {
                val group = LEADING_GROUP.find(rest) ?: return Names(null, null)
                val inside = group.groupValues[1].trim()
                rest = rest.substring(group.range.last + 1)
                if (NOT_ARTIST.containsMatchIn(inside)) return@repeat
                val inner = INNER_PARENS.find(inside) ?: return Names(null, inside)
                val circle = inside.substring(0, inner.range.first).trim().takeIf { it.isNotEmpty() }
                return Names(circle, inner.groupValues[1].trim())
            }
            return Names(null, null)
        }

        private fun key(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().replace(SPACES, " ").trim()
    }
}
