package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.tachiyomi.data.sync.SearchHistoryEntry
import eu.kanade.tachiyomi.data.sync.SyncDevice
import eu.kanade.tachiyomi.data.sync.searchHistoryKey

/** A search history entry as shown in global search. */
data class SearchHistoryItem(
    val query: String,
    /** Name of the device the query was searched on, or null for this device. */
    val deviceName: String?,
)

/** Records a search for [query]: it becomes the newest entry, replacing any older one. */
internal fun List<SearchHistoryEntry>.withSearchQuery(
    query: String,
    device: SyncDevice,
    now: Long,
): List<SearchHistoryEntry> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this

    val key = searchHistoryKey(trimmedQuery)
    return buildList {
        add(
            SearchHistoryEntry(
                query = trimmedQuery,
                updatedAt = now,
                deviceId = device.id,
                deviceName = device.name,
            ),
        )
        addAll(this@withSearchQuery.filterNot { it.key == key })
    }
}

/** Marks [query] as deleted, so the deletion also reaches other devices. */
internal fun List<SearchHistoryEntry>.withoutSearchQuery(
    query: String,
    device: SyncDevice,
    now: Long,
): List<SearchHistoryEntry> {
    val key = searchHistoryKey(query)
    return map { entry ->
        if (entry.key == key && !entry.deleted) entry.asDeleted(device, now) else entry
    }
}

internal fun List<SearchHistoryEntry>.withoutAnySearchQuery(
    device: SyncDevice,
    now: Long,
): List<SearchHistoryEntry> {
    return map { entry -> if (entry.deleted) entry else entry.asDeleted(device, now) }
}

internal fun List<SearchHistoryEntry>.toSearchHistoryItems(device: SyncDevice): List<SearchHistoryItem> {
    return filterNot(SearchHistoryEntry::deleted)
        .sortedByDescending(SearchHistoryEntry::updatedAt)
        .map { entry ->
            SearchHistoryItem(
                query = entry.query,
                deviceName = entry.deviceName.takeIf { entry.deviceId != device.id && it.isNotBlank() },
            )
        }
}

private fun SearchHistoryEntry.asDeleted(device: SyncDevice, now: Long) = copy(
    updatedAt = now,
    deviceId = device.id,
    deviceName = device.name,
    deleted = true,
)
