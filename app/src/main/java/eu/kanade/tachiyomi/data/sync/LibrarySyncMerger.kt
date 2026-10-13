package eu.kanade.tachiyomi.data.sync

internal object LibrarySyncMerger {

    fun merge(
        local: Map<LibrarySyncKey, LibrarySyncEntry>,
        cloud: LibrarySyncFile,
        baseline: LibrarySyncFile?,
        now: Long,
    ): LibrarySyncFile {
        val cloudEntries = cloud.entries.associateBy(LibrarySyncEntry::key)
        val baselineEntries = baseline?.entries.orEmpty().associateBy(LibrarySyncEntry::key)
        val keys = local.keys + cloudEntries.keys + baselineEntries.keys

        val entries = keys.mapNotNull { key ->
            val localEntry = local[key]
            val cloudEntry = cloudEntries[key]
            val baselineEntry = baselineEntries[key]

            if (baselineEntry == null) {
                resolve(localEntry, cloudEntry)
            } else {
                val localChanged = localEntry != null &&
                    localEntry.favorite != baselineEntry.favorite
                val cloudChanged = cloudEntry != null &&
                    cloudEntry.favorite != baselineEntry.favorite

                when {
                    localChanged && cloudChanged -> resolve(localEntry, cloudEntry)
                    localChanged -> localEntry
                    cloudChanged -> cloudEntry
                    else -> cloudEntry ?: localEntry ?: baselineEntry
                }
            }
        }.sortedWith(compareBy(LibrarySyncEntry::sourceId, LibrarySyncEntry::mangaUrl))

        return LibrarySyncFile(
            updatedAt = now,
            entries = entries,
        )
    }

    private fun resolve(
        first: LibrarySyncEntry?,
        second: LibrarySyncEntry?,
    ): LibrarySyncEntry? {
        if (first == null) return second
        if (second == null) return first
        if (first.modifiedAt != second.modifiedAt) {
            return if (first.modifiedAt > second.modifiedAt) first else second
        }
        if (first.favorite != second.favorite) {
            return if (first.favorite) second else first
        }
        return second
    }
}
