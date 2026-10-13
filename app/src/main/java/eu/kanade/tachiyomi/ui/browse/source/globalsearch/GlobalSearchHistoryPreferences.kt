package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.sync.SearchHistoryEntry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import kotlin.time.Clock

/**
 * Global search history (fork-only feature).
 *
 * Kept out of [eu.kanade.domain.source.service.SourcePreferences] to avoid merge
 * conflicts with upstream. Deleted queries stay in the list as deletion markers until they
 * expire, so deletions can be synced to other devices
 * ([eu.kanade.tachiyomi.data.sync.SearchHistoryMerger]).
 *
 * The entries use an app state key: they are synced through OneDrive instead of being part of
 * backups, so restoring a settings backup does not roll the history back.
 */
@Inject
@SingleIn(AppScope::class)
class GlobalSearchHistoryPreferences(
    preferenceStore: PreferenceStore,
) {

    val entries: Preference<List<SearchHistoryEntry>> = preferenceStore.getObjectFromString(
        key = Preference.appStateKey("global_search_history_entries"),
        defaultValue = emptyList(),
        serializer = { json.encodeToString(it) },
        deserializer = {
            runCatching { json.decodeFromString<List<SearchHistoryEntry>>(it) }.getOrDefault(emptyList())
        },
    )

    // Plain list of queries, newest first, used before entries had timestamps and devices.
    private val legacyHistories: List<Preference<List<String>>> = listOf(
        "global_search_history",
        Preference.appStateKey("global_search_history"),
    ).map { key ->
        preferenceStore.getObjectFromString(
            key = key,
            defaultValue = emptyList(),
            serializer = { json.encodeToString(it) },
            deserializer = { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) },
        )
    }

    init {
        legacyHistories.filter { it.isSet() }.forEach { legacy ->
            if (!entries.isSet()) {
                val now = Clock.System.now().toEpochMilliseconds()
                // The device is filled in on first sync, see LibrarySyncService.
                entries.set(
                    legacy.get().mapIndexed { index, query ->
                        SearchHistoryEntry(query = query, updatedAt = now - index)
                    },
                )
            }
            legacy.delete()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
