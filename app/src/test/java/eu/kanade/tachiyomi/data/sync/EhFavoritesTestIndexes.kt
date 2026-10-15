package eu.kanade.tachiyomi.data.sync

/** Indexes of test favorites, see [EhFavoritesIndex]. */
internal object EhFavoritesTestIndexes {

    fun hash(number: Int): String = number.toString(16).padStart(32, '0')

    fun oneShard(
        galleries: List<EhIndexedGallery>,
        normalizer: EhTitleNormalizer,
        slotNames: List<String> = emptyList(),
    ) = EhFavoritesIndex(slotNames, listOf(EhFavoritesShardIndex.build(hash(0), galleries, normalizer)), normalizer)

    fun shardPerGallery(galleries: List<EhIndexedGallery>, normalizer: EhTitleNormalizer) = EhFavoritesIndex(
        emptyList(),
        galleries.sortedBy(EhIndexedGallery::gid).mapIndexed { index, gallery ->
            EhFavoritesShardIndex.build(hash(index + 1), listOf(gallery), normalizer)
        },
        normalizer,
    )
}
