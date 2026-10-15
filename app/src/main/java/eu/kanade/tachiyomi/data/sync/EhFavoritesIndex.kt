package eu.kanade.tachiyomi.data.sync

/**
 * Lookup of the EhViewer favorites for list items: by E-Hentai gallery id for E-Hentai sources, else by title, see
 * [EhTitleNormalizer] and [EhTitleSimilarity]. Titles are found through a character pair index, so a lookup only
 * compares a few candidates; results are cached.
 *
 * Made of one [EhFavoritesShardIndex] per shard EhViewer exports (fork-only feature). A new list of shards reuses the
 * shards already indexed ([updated]), so a changed favorite only indexes its shard again, and the old index keeps
 * working until the new one is complete.
 */
class EhFavoritesIndex(
    /** Names of the cloud favorite slots, empty if unknown. */
    val slotNames: List<String>,
    shards: List<EhFavoritesShardIndex>,
    private val normalizer: EhTitleNormalizer,
) {
    /** Shards with galleries, by gid: shards cover separate gid ranges. */
    val shards: List<EhFavoritesShardIndex> = shards.filter { it.size > 0 }.sortedBy { it.firstGid }

    private val byHash = this.shards.associateBy(EhFavoritesShardIndex::hash)
    private val firstGids = LongArray(this.shards.size) { this.shards[it].firstGid }

    // Galleries and keys are numbered across shards: shard i starts at offsets[i]
    private val galleryOffsets = offsets(EhFavoritesShardIndex::size)
    private val keyOffsets = offsets(EhFavoritesShardIndex::keyCount)

    private val cache = object : LinkedHashMap<String, EhFavoriteLookup>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EhFavoriteLookup>?) = size > CACHE_SIZE
    }

    /** Number of galleries. */
    val size: Int
        get() = galleryOffsets.last()

    val isEmpty: Boolean
        get() = size == 0

    /** All galleries, by gid. */
    val galleries: Sequence<EhIndexedGallery>
        get() = shards.asSequence().flatMap { it.galleries }

    /** Other spellings of the names in these favorites, built on first use (a global search finding nothing). */
    val aliases: EhNameAliases by lazy { EhNameAliases.build(galleries.asIterable()) }

    /** Whether a shard with [hash] is indexed here, see [updated]. */
    fun hasShard(hash: String): Boolean = hash in byHash

    /**
     * The index of the shards with [hashes], reusing the shards of this index with the same hash and indexing the
     * others with [indexShard], which must use the same [normalizer].
     */
    fun updated(
        slotNames: List<String>,
        hashes: List<String>,
        normalizer: EhTitleNormalizer = this.normalizer,
        indexShard: (hash: String) -> EhFavoritesShardIndex,
    ): EhFavoritesIndex {
        val newShards = hashes.distinct().map { hash -> byHash[hash] ?: indexShard(hash) }
        return EhFavoritesIndex(slotNames, newShards, normalizer)
    }

    /**
     * The favorites matching a manga. [authors] are the author and artist fields of the manga, used to accept slightly
     * different titles by the same artist and to reject equal short titles by different artists.
     */
    fun find(mangaUrl: String, title: String, authors: List<String> = emptyList()): EhFavoriteMatch? {
        if (isEmpty) return null
        val cacheKey = cacheKey(mangaUrl, title, authors)
        synchronized(cache) {
            cache[cacheKey]?.let { return it.match }
        }
        val result = findUncached(mangaUrl, title, authors)
        synchronized(cache) { cache[cacheKey] = EhFavoriteLookup(result) }
        return result
    }

    /** The result of [find] if it is already known, without searching: null if it is not. */
    fun cached(mangaUrl: String, title: String, authors: List<String> = emptyList()): EhFavoriteLookup? {
        if (isEmpty) return NO_MATCH
        val cacheKey = cacheKey(mangaUrl, title, authors)
        return synchronized(cache) { cache[cacheKey] }
    }

    private fun cacheKey(mangaUrl: String, title: String, authors: List<String>) =
        "$mangaUrl\u0000$title\u0000${authors.joinToString("\u0000")}"

    private fun findUncached(mangaUrl: String, title: String, authors: List<String>): EhFavoriteMatch? {
        MangaMarkKeys.galleryId(mangaUrl)?.let { gid ->
            galleryByGid(gid)?.let { return EhFavoriteMatch(listOf(it), byGalleryId = true) }
        }
        val mangaArtists = normalizer.artists(title) +
            authors.flatMap { it.split(',', '、') }.map(normalizer::name).filter { it.isNotEmpty() }
        // Best score of each matched gallery (numbered across shards), in the order they were found
        val scoredGalleries = GrowingIntArray()
        val scores = GrowingIntArray()
        normalizer.keys(title).filterNot(EhTitleSimilarity::mostlyWildcards).forEach { query ->
            candidates(query).forEach candidate@{ globalKey ->
                val shardIndex = rangeOf(keyOffsets, globalKey)
                val shard = shards[shardIndex]
                val key = globalKey - keyOffsets[shardIndex]
                val text = shard.keyText(key)
                val gallery = shard.galleryOfKey(key)
                val known = mangaArtists.isNotEmpty() && shard.hasArtists(gallery)
                val agree = known && shard.anyArtistIn(gallery, mangaArtists)
                if (!EhTitleSimilarity.sameNumbers(query, text)) return@candidate
                val length = maxOf(query.length, text.length)
                val distance = EhTitleSimilarity.allowedEdits(length, agree, artistsConflict = known && !agree)
                    ?.let { allowed -> EhTitleSimilarity.distance(query, text, allowed) }
                    ?: containedScore(query, text, agree)
                    ?: return@candidate
                val globalGallery = galleryOffsets[shardIndex] + gallery
                val previous = scoredGalleries.indexOf(globalGallery)
                if (previous < 0) {
                    scoredGalleries.add(globalGallery)
                    scores.add(distance)
                } else if (distance < scores[previous]) {
                    scores[previous] = distance
                }
            }
        }
        if (scoredGalleries.size == 0) return null
        val matched = (0..<scoredGalleries.size)
            .sortedBy { scores[it] }
            .take(MAX_MATCHES)
            .map { galleryAt(scoredGalleries[it]) }
        return EhFavoriteMatch(matched, byGalleryId = false)
    }

    /**
     * A title shortened on one site (only the main title, without its subtitle) matches when the artists agree; it
     * ranks after the titles matched by edit distance.
     */
    private fun containedScore(query: String, key: String, artistsAgree: Boolean): Int? {
        if (!artistsAgree || minOf(query.length, key.length) < EhTitleSimilarity.MIN_CONTAINED_LENGTH) return null
        val contained = if (query.length <= key.length) {
            EhTitleSimilarity.contains(key, query)
        } else {
            EhTitleSimilarity.contains(query, key)
        }
        return if (contained) CONTAINED_SCORE + kotlin.math.abs(query.length - key.length) else null
    }

    /** Keys (numbered across shards) sharing enough character pairs with [query], most shared first. */
    private fun candidates(query: String): IntArray {
        val queryBigrams = EhFavoritesShardIndex.bigrams(query)
        if (queryBigrams.isEmpty()) {
            // Two characters with a wildcard, or a single pair: compare with the keys of that length only
            val result = GrowingIntArray()
            shards.forEachIndexed { shardIndex, shard ->
                for (key in 0..<shard.keyCount) {
                    if (result.size >= MAX_CANDIDATES) return result.toArray()
                    if (shard.keyLength(key) == query.length) result.add(keyOffsets[shardIndex] + key)
                }
            }
            return result.toArray()
        }
        val minShared = EhTitleSimilarity.minShared(queryBigrams.size)
        val ranked = GrowingLongArray()
        val scratch = SCRATCH.get()!!
        shards.forEachIndexed { shardIndex, shard ->
            shard.collectShared(queryBigrams, minShared, keyOffsets[shardIndex], scratch, ranked)
        }
        // Most shared pairs first, then by key
        val sorted = ranked.toArray().apply { sort() }
        return IntArray(minOf(sorted.size, MAX_CANDIDATES)) { sorted[it].toInt() }
    }

    private fun galleryByGid(gid: Long): EhIndexedGallery? {
        // The last shard starting at or before gid
        var low = 0
        var high = firstGids.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (firstGids[middle] <= gid) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        if (found < 0) return null
        val shard = shards[found]
        return shard.indexOfGid(gid).takeIf { it >= 0 }?.let { shard.galleries[it] }
    }

    private fun galleryAt(globalGallery: Int): EhIndexedGallery {
        val shardIndex = rangeOf(galleryOffsets, globalGallery)
        return shards[shardIndex].galleries[globalGallery - galleryOffsets[shardIndex]]
    }

    private inline fun offsets(count: (EhFavoritesShardIndex) -> Int): IntArray {
        val result = IntArray(shards.size + 1)
        shards.forEachIndexed { index, shard -> result[index + 1] = result[index] + count(shard) }
        return result
    }

    /** The shard holding [value]: the last one starting at or before it (one without keys starts where the next does). */
    private fun rangeOf(offsets: IntArray, value: Int): Int {
        var low = 0
        var high = offsets.size - 2
        var found = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (offsets[middle] <= value) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }

    companion object {
        private const val CACHE_SIZE = 2_000
        private const val MAX_CANDIDATES = 50
        private const val MAX_MATCHES = 5
        private const val CONTAINED_SCORE = 1_000

        private val NO_MATCH = EhFavoriteLookup(null)

        /** Counting arrays of [EhFavoritesShardIndex.collectShared], one per thread looking up titles. */
        private val SCRATCH = ThreadLocal.withInitial { EhFavoritesShardIndex.Scratch() }

        val EMPTY = EhFavoritesIndex(emptyList(), emptyList(), EhTitleNormalizer())
    }
}

/**
 * The galleries of one shard EhViewer exports, with their title keys, artists and character pair index, for
 * [EhFavoritesIndex]. Immutable, and shared by the indexes listing a shard with the same [hash]. Kept in primitive
 * arrays: 150 000 favorites have millions of character pairs.
 */
class EhFavoritesShardIndex private constructor(
    val hash: String,
    /** By gid. */
    val galleries: List<EhIndexedGallery>,
    private val gids: LongArray,
    /** The keys one after another, key i from keyStarts[i] to keyStarts[i + 1]. */
    private val keyTexts: String,
    private val keyStarts: IntArray,
    private val keyGalleries: IntArray,
    /** The artists of gallery i are artistNames from artistStarts[i] to artistStarts[i + 1]. */
    private val artistStarts: IntArray,
    private val artistNames: Array<String>,
    /** Sorted; the keys containing bigrams[i] are postings from postingStarts[i] to postingStarts[i + 1]. */
    private val bigrams: IntArray,
    private val postingStarts: IntArray,
    private val postings: IntArray,
) {
    val size: Int
        get() = galleries.size

    val keyCount: Int
        get() = keyGalleries.size

    internal val firstGid: Long
        get() = if (gids.isEmpty()) Long.MAX_VALUE else gids[0]

    internal fun indexOfGid(gid: Long): Int = gids.binarySearch(gid).coerceAtLeast(-1)

    internal fun keyText(key: Int): String = keyTexts.substring(keyStarts[key], keyStarts[key + 1])

    internal fun keyLength(key: Int): Int = keyStarts[key + 1] - keyStarts[key]

    internal fun galleryOfKey(key: Int): Int = keyGalleries[key]

    internal fun hasArtists(gallery: Int): Boolean = artistStarts[gallery + 1] > artistStarts[gallery]

    internal fun anyArtistIn(gallery: Int, artists: Set<String>): Boolean {
        for (i in artistStarts[gallery]..<artistStarts[gallery + 1]) {
            if (artistNames[i] in artists) return true
        }
        return false
    }

    /**
     * Adds the keys sharing at least [minShared] of [queryBigrams] to [out], as the number shared (most first when
     * sorted) and the key numbered from [keyOffset].
     */
    internal fun collectShared(
        queryBigrams: IntArray,
        minShared: Int,
        keyOffset: Int,
        scratch: Scratch,
        out: GrowingLongArray,
    ) {
        scratch.ensureSize(keyCount)
        val counts = scratch.counts
        val touched = scratch.touched
        var touchedCount = 0
        for (bigram in queryBigrams) {
            val index = bigrams.binarySearch(bigram)
            if (index < 0) continue
            for (posting in postingStarts[index]..<postingStarts[index + 1]) {
                val key = postings[posting]
                if (counts[key]++ == 0) touched[touchedCount++] = key
            }
        }
        for (i in 0..<touchedCount) {
            val key = touched[i]
            val count = counts[key]
            counts[key] = 0
            if (count >= minShared) out.add(((Int.MAX_VALUE - count).toLong() shl 32) or (keyOffset + key).toLong())
        }
    }

    /** Counting arrays reused by the lookups of one thread, all zero between lookups. */
    internal class Scratch {
        var counts = IntArray(0)
            private set
        var touched = IntArray(0)
            private set

        fun ensureSize(size: Int) {
            if (counts.size >= size) return
            counts = IntArray(size)
            touched = IntArray(size)
        }
    }

    companion object {
        private const val PROGRESS_STEP = 100

        /**
         * Indexes the [galleries] of the shard with [hash]. [onProgress] gets the galleries indexed so far, every
         * [PROGRESS_STEP] galleries and at the end.
         */
        fun build(
            hash: String,
            galleries: List<EhIndexedGallery>,
            normalizer: EhTitleNormalizer,
            onProgress: ((done: Int) -> Unit)? = null,
        ): EhFavoritesShardIndex {
            val sorted = if ((1..<galleries.size).all { galleries[it - 1].gid <= galleries[it].gid }) {
                galleries
            } else {
                galleries.sortedBy(EhIndexedGallery::gid)
            }
            val keyTexts = StringBuilder()
            val keyStarts = GrowingIntArray().apply { add(0) }
            val keyGalleries = GrowingIntArray()
            val artistStarts = IntArray(sorted.size + 1)
            val artistNames = ArrayList<String>()
            // One String per artist name, however many galleries name it
            val names = HashMap<String, String>()
            // Character pair in the high half, key in the low half
            val pairs = GrowingLongArray()
            sorted.forEachIndexed { index, gallery ->
                val titles = listOfNotNull(gallery.title, gallery.titleJpn)
                titles.flatMapTo(LinkedHashSet()) { normalizer.artists(it) }
                    .forEach { artistNames += names.getOrPut(it) { it } }
                artistStarts[index + 1] = artistNames.size
                titles.flatMap { normalizer.keys(it) }.distinct().forEach { text ->
                    if (EhTitleSimilarity.mostlyWildcards(text)) return@forEach
                    val key = keyGalleries.size
                    keyGalleries.add(index)
                    keyTexts.append(text)
                    keyStarts.add(keyTexts.length)
                    forEachBigram(text) { pairs.add((it.toLong() shl 32) or key.toLong()) }
                }
                if (onProgress != null && (index + 1) % PROGRESS_STEP == 0) onProgress(index + 1)
            }
            onProgress?.invoke(sorted.size)

            // Sorted by pair, then key: the posting lists one after another; a pair twice in one key counts once
            val sortedPairs = pairs.toArray().apply { sort() }
            val bigrams = GrowingIntArray()
            val postingStarts = GrowingIntArray()
            val postings = GrowingIntArray(sortedPairs.size)
            sortedPairs.forEachIndexed { i, pair ->
                if (i > 0 && pair == sortedPairs[i - 1]) return@forEachIndexed
                val bigram = (pair shr 32).toInt()
                if (i == 0 || bigram != (sortedPairs[i - 1] shr 32).toInt()) {
                    bigrams.add(bigram)
                    postingStarts.add(postings.size)
                }
                postings.add(pair.toInt())
            }
            postingStarts.add(postings.size)

            return EhFavoritesShardIndex(
                hash = hash,
                galleries = sorted,
                gids = LongArray(sorted.size) { sorted[it].gid },
                keyTexts = keyTexts.toString(),
                keyStarts = keyStarts.toArray(),
                keyGalleries = keyGalleries.toArray(),
                artistStarts = artistStarts,
                artistNames = artistNames.toTypedArray(),
                bigrams = bigrams.toArray(),
                postingStarts = postingStarts.toArray(),
                postings = postings.toArray(),
            )
        }

        /** The distinct character pairs of [text] without a wildcard, sorted. */
        internal fun bigrams(text: String): IntArray {
            val result = GrowingIntArray()
            forEachBigram(text) { result.add(it) }
            val sorted = result.toArray().apply { sort() }
            var distinct = 0
            sorted.forEachIndexed { i, bigram ->
                if (i == 0 || bigram != sorted[i - 1]) sorted[distinct++] = bigram
            }
            return sorted.copyOf(distinct)
        }

        private inline fun forEachBigram(text: String, action: (Int) -> Unit) {
            for (i in 0..<text.length - 1) {
                val a = text[i]
                val b = text[i + 1]
                if (a == EhTitleNormalizer.WILDCARD || b == EhTitleNormalizer.WILDCARD) continue
                action((a.code shl 16) or b.code)
            }
        }
    }
}

/** An [IntArray] that grows as values are added, without boxing them. */
internal class GrowingIntArray(capacity: Int = 16) {
    private var values = IntArray(maxOf(capacity, 1))

    var size = 0
        private set

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }

    operator fun get(index: Int): Int = values[index]

    operator fun set(index: Int, value: Int) {
        values[index] = value
    }

    fun indexOf(value: Int): Int {
        for (i in 0..<size) {
            if (values[i] == value) return i
        }
        return -1
    }

    fun toArray(): IntArray = values.copyOf(size)
}

/** A [LongArray] that grows as values are added, without boxing them. */
internal class GrowingLongArray(capacity: Int = 16) {
    private var values = LongArray(maxOf(capacity, 1))

    var size = 0
        private set

    fun add(value: Long) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }

    fun toArray(): LongArray = values.copyOf(size)
}
