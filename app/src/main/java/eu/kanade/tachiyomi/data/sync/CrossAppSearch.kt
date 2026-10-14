package eu.kanade.tachiyomi.data.sync

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Searching a manga in the EhViewer fork (fork-only feature): the query is the title without its bracket groups
 * (event, circle, parody, language, translator), or the artist of its leading bracket group.
 */
object CrossAppSearch {
    private val BRACKET_GROUPS = Regex("""\[[^\]]*]|\([^)]*\)|【[^】]*】|（[^）]*）|\{[^}]*}|［[^］]*］""")
    private val LEADING_GROUP = Regex("""^\s*(?:(?:\([^)]*\)|（[^）]*）)\s*)*[\[【［]([^\]】］]+)[\]】］]""")
    private val INNER_PARENS = Regex("""[(（]([^)）]+)[)）]""")
    private val SPACES = Regex("""\s+""")

    // A leading group naming translators or a language rather than the artist, the markers of EhTitleNormalizer
    private val NOT_ARTIST = Regex(
        """汉化|漢化|翻訳|翻译|翻譯|中文|中国|中國|chinese|english|translat|個人|个人|机翻|機翻|嵌字|修正|dl版|digital""",
        RegexOption.IGNORE_CASE,
    )

    /** The title without its bracket groups, the first of several titles separated by "|". */
    fun coreTitle(title: String): String {
        val first = title.substringBefore('|').trim()
        val core = first.replace(BRACKET_GROUPS, " ").replace(SPACES, " ").trim()
        return core.ifEmpty { first }
    }

    /**
     * The artist named in the leading bracket group of [title] ("[Circle (Artist)] Title" gives "Artist"), else the
     * first of [authors]; null if none.
     */
    fun artist(title: String, authors: List<String> = emptyList()): String? {
        var rest = title
        repeat(MAX_LEADING_GROUPS) {
            val group = LEADING_GROUP.find(rest) ?: return@repeat
            val inside = group.groupValues[1].trim()
            rest = rest.substring(group.range.last + 1)
            if (NOT_ARTIST.containsMatchIn(inside)) return@repeat
            val artist = INNER_PARENS.find(inside)?.groupValues?.get(1)?.trim() ?: inside
            if (artist.isNotEmpty()) return artist
        }
        return authors.flatMap { it.split(',', '、', '/') }.map { it.trim() }.firstOrNull { it.isNotEmpty() }
    }

    /** Opens a keyword search for [query] in EhViewer; false if EhViewer is not installed. */
    fun searchInEhViewer(context: Context, query: String): Boolean {
        EHVIEWER_PACKAGES.forEach { pkg ->
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, query)
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Not this build of EhViewer
            }
        }
        return false
    }

    private const val MAX_LEADING_GROUPS = 3
    private val EHVIEWER_PACKAGES = listOf("moe.tarsin.ehviewer", "moe.tarsin.ehviewer.m")
}
