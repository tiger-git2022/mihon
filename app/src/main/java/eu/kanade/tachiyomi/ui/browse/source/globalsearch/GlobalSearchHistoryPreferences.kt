package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Global search history (fork-only feature).
 *
 * Kept out of [eu.kanade.domain.source.service.SourcePreferences] to avoid merge
 * conflicts with upstream.
 */
@Inject
@SingleIn(AppScope::class)
class GlobalSearchHistoryPreferences(
    preferenceStore: PreferenceStore,
) {

    val globalSearchHistory: Preference<List<String>> = preferenceStore.getObjectFromString(
        key = "global_search_history",
        defaultValue = emptyList(),
        serializer = { Json.encodeToString(it) },
        deserializer = { Json.decodeFromString(it) },
    )

    private val legacyGlobalSearchHistory: Preference<List<String>> = preferenceStore.getObjectFromString(
        key = Preference.appStateKey("global_search_history"),
        defaultValue = emptyList(),
        serializer = { Json.encodeToString(it) },
        deserializer = { Json.decodeFromString(it) },
    )

    init {
        if (!globalSearchHistory.isSet() && legacyGlobalSearchHistory.isSet()) {
            globalSearchHistory.set(legacyGlobalSearchHistory.get())
            legacyGlobalSearchHistory.delete()
        }
    }
}
