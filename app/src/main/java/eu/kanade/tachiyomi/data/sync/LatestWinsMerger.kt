package eu.kanade.tachiyomi.data.sync

/**
 * Merges two versions of a record list where every record (including deletion markers) carries
 * the time it last changed: for each key, the most recently changed record wins. On a tie the
 * cloud copy wins, so every device converges on the same result.
 */
internal object LatestWinsMerger {

    fun <T> merge(
        local: Collection<T>,
        cloud: Collection<T>,
        key: (T) -> String,
        updatedAt: (T) -> Long,
    ): List<T> {
        val merged = LinkedHashMap<String, T>()
        local.forEach { merged[key(it)] = it }
        cloud.forEach { cloudRecord ->
            val recordKey = key(cloudRecord)
            val localRecord = merged[recordKey]
            if (localRecord == null || updatedAt(cloudRecord) >= updatedAt(localRecord)) {
                merged[recordKey] = cloudRecord
            }
        }
        return merged.values.toList()
    }
}
