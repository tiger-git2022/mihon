package eu.kanade.tachiyomi.data.sync

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * A global search query, or the marker of a deleted one ([deleted]).
 *
 * [updatedAt] is when the query was last searched, or when it was deleted.
 */
@Serializable
data class SearchHistoryEntry(
    val query: String,
    val updatedAt: Long,
    val deviceId: String = "",
    val deviceName: String = "",
    val deleted: Boolean = false,
) {
    val key: String
        get() = searchHistoryKey(query)
}

fun searchHistoryKey(query: String): String = query.trim().lowercase()

@Serializable
data class SearchHistorySyncFile(
    @EncodeDefault
    val version: Int = CURRENT_VERSION,
    val entries: List<SearchHistoryEntry> = emptyList(),
    /** When the history was last cleared; older entries are dropped on every device. */
    val clearedAt: Long = 0L,
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "mihon-search-history.json"
    }
}

internal object SearchHistoryMerger {

    /** Searches kept per list; older ones are dropped. */
    const val MAX_ENTRIES = 200

    /** Deletion markers are kept this long so other devices learn about the deletion. */
    const val DELETION_RETENTION_MILLIS = 180L * 24 * 60 * 60 * 1000

    fun merge(
        local: List<SearchHistoryEntry>,
        cloud: List<SearchHistoryEntry>,
        now: Long,
        clearedAt: Long = 0L,
    ): List<SearchHistoryEntry> {
        return prune(
            LatestWinsMerger.merge(local, cloud, SearchHistoryEntry::key, SearchHistoryEntry::updatedAt)
                .filter { it.updatedAt > clearedAt },
            now,
        )
    }

    fun prune(entries: List<SearchHistoryEntry>, now: Long): List<SearchHistoryEntry> {
        val (deleted, live) = entries.partition(SearchHistoryEntry::deleted)
        return live.sortedByDescending(SearchHistoryEntry::updatedAt).take(MAX_ENTRIES) +
            deleted.filter { now - it.updatedAt < DELETION_RETENTION_MILLIS }
    }
}
