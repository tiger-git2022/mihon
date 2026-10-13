package eu.kanade.tachiyomi.data.sync

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil

/**
 * Turns gallery and manga titles into comparable keys, so the same work matches across sites that write its title
 * differently (fork-only feature):
 * - full-width and half-width forms, upper and lower case, traditional and simplified Chinese ([toSimplified]) are
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

    /** Circle and artist of an E-Hentai style title: the first [...] group, after an optional (event). */
    fun artists(title: String): Set<String> {
        val text = Normalizer.normalize(title, Normalizer.Form.NFKC)
        val group = LEADING_GROUP.find(text)?.groupValues?.get(1) ?: return emptySet()
        val inner = INNER_PARENS.find(group)
        val names = if (inner != null) {
            listOf(group.substring(0, inner.range.first), inner.groupValues[1])
        } else {
            listOf(group)
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
                    char.isLetterOrDigit() -> append(char)
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
        private val LEADING_GROUP = Regex("""^\s*(?:\([^)]*\)\s*)?[\[【]([^\]】]+)[\]】]""")
        private val INNER_PARENS = Regex("""\(([^)]*)\)""")
        private const val CENSOR_MARKS = "○◯〇●◎◉*×✕✖■□◆◇"
    }
}

/** Similarity rules between two keys, see [EhFavoritesIndex.find]. */
internal object EhTitleSimilarity {
    private val DIGITS = Regex("""\d+""")

    /** Edit distance where [EhTitleNormalizer.WILDCARD] matches any character; null once above [limit]. */
    fun distance(a: String, b: String, limit: Int): Int? {
        if (kotlin.math.abs(a.length - b.length) > limit) return null
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val same = a[i - 1] == b[j - 1] ||
                    a[i - 1] == EhTitleNormalizer.WILDCARD ||
                    b[j - 1] == EhTitleNormalizer.WILDCARD
                current[j] = minOf(
                    previous[j - 1] + if (same) 0 else 1,
                    previous[j] + 1,
                    current[j - 1] + 1,
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

    /**
     * Edits allowed between two keys of the same work: about one in seven characters, one in four when the artists
     * agree, none for short titles unless the artists agree, and none when both name different artists. Titles of one
     * or two characters only match exactly with the same artist.
     */
    fun allowedEdits(length: Int, artistsAgree: Boolean, artistsConflict: Boolean): Int? = when {
        length <= 2 -> if (artistsAgree) 0 else null
        artistsConflict -> 0
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

    fun minShared(bigrams: Int) = maxOf(1, ceil(bigrams * 0.3).toInt())
}

/**
 * Lookup of the EhViewer favorites for list items: by E-Hentai gallery id for E-Hentai sources, else by title, see
 * [EhTitleNormalizer] and [EhTitleSimilarity]. Titles are found through a character pair index, so a lookup only
 * compares a few candidates; results are cached.
 */
class EhFavoritesIndex(
    val file: EhFavoritesIndexFile,
    private val normalizer: EhTitleNormalizer,
) {
    private class Key(val gallery: Int, val text: String)

    private val galleries = file.galleries
    private val byGid = galleries.withIndex().associate { it.value.gid to it.index }
    private val artists: List<Set<String>>
    private val keys = ArrayList<Key>()
    private val bigramIndex = HashMap<Int, MutableList<Int>>()
    private val cache = object : LinkedHashMap<String, EhFavoriteMatch?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EhFavoriteMatch?>?) = size > CACHE_SIZE
    }

    init {
        artists = galleries.map { gallery ->
            listOfNotNull(gallery.title, gallery.titleJpn).flatMapTo(HashSet()) { normalizer.artists(it) }
        }
        galleries.forEachIndexed { index, gallery ->
            val texts = listOfNotNull(gallery.title, gallery.titleJpn).flatMap { normalizer.keys(it) }.distinct()
            texts.filterNot(EhTitleSimilarity::mostlyWildcards).forEach { text ->
                val keyIndex = keys.size
                keys += Key(index, text)
                bigrams(text).forEach { bigramIndex.getOrPut(it) { ArrayList() } += keyIndex }
            }
        }
    }

    val slotNames: List<String>
        get() = file.slotNames

    val isEmpty: Boolean
        get() = galleries.isEmpty()

    /**
     * The favorites matching a manga. [authors] are the author and artist fields of the manga, used to accept slightly
     * different titles by the same artist and to reject equal short titles by different artists.
     */
    fun find(mangaUrl: String, title: String, authors: List<String> = emptyList()): EhFavoriteMatch? {
        if (galleries.isEmpty()) return null
        val cacheKey = "$mangaUrl\u0000$title\u0000${authors.joinToString("\u0000")}"
        synchronized(cache) {
            if (cache.containsKey(cacheKey)) return cache[cacheKey]
        }
        val result = findUncached(mangaUrl, title, authors)
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    private fun findUncached(mangaUrl: String, title: String, authors: List<String>): EhFavoriteMatch? {
        MangaMarkKeys.galleryId(mangaUrl)?.let { gid ->
            byGid[gid]?.let { return EhFavoriteMatch(listOf(galleries[it]), byGalleryId = true) }
        }
        val mangaArtists = normalizer.artists(title) +
            authors.flatMap { it.split(',', '、') }.map(normalizer::name).filter { it.isNotEmpty() }
        val scores = HashMap<Int, Int>()
        normalizer.keys(title).filterNot(EhTitleSimilarity::mostlyWildcards).forEach { query ->
            candidates(query).forEach candidate@{ keyIndex ->
                val key = keys[keyIndex]
                val galleryArtists = artists[key.gallery]
                val known = mangaArtists.isNotEmpty() && galleryArtists.isNotEmpty()
                val agree = known && mangaArtists.any { it in galleryArtists }
                val length = maxOf(query.length, key.text.length)
                val allowed = EhTitleSimilarity.allowedEdits(length, agree, artistsConflict = known && !agree)
                    ?: return@candidate
                if (!EhTitleSimilarity.sameNumbers(query, key.text)) return@candidate
                val distance = EhTitleSimilarity.distance(query, key.text, allowed) ?: return@candidate
                val previous = scores[key.gallery]
                if (previous == null || distance < previous) scores[key.gallery] = distance
            }
        }
        if (scores.isEmpty()) return null
        val matched = scores.entries.sortedBy { it.value }.take(MAX_MATCHES).map { galleries[it.key] }
        return EhFavoriteMatch(matched, byGalleryId = false)
    }

    /** Keys sharing enough character pairs with [query], most shared first. */
    private fun candidates(query: String): List<Int> {
        val queryBigrams = bigrams(query)
        if (queryBigrams.isEmpty()) {
            // Two characters with a wildcard, or a single pair: compare with the keys of that length only
            return keys.indices.filter { keys[it].text.length == query.length }.take(MAX_CANDIDATES)
        }
        val counts = HashMap<Int, Int>()
        queryBigrams.forEach { bigram ->
            bigramIndex[bigram]?.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val minShared = EhTitleSimilarity.minShared(queryBigrams.size)
        return counts.entries.asSequence()
            .filter { it.value >= minShared }
            .sortedByDescending { it.value }
            .take(MAX_CANDIDATES)
            .map { it.key }
            .toList()
    }

    private fun bigrams(text: String): Set<Int> {
        val result = HashSet<Int>()
        for (i in 0 until text.length - 1) {
            val a = text[i]
            val b = text[i + 1]
            if (a == EhTitleNormalizer.WILDCARD || b == EhTitleNormalizer.WILDCARD) continue
            result += (a.code shl 16) or b.code
        }
        return result
    }

    companion object {
        private const val CACHE_SIZE = 2_000
        private const val MAX_CANDIDATES = 50
        private const val MAX_MATCHES = 5

        val EMPTY = EhFavoritesIndex(EhFavoritesIndexFile(), EhTitleNormalizer())
    }
}
