package eu.kanade.tachiyomi.data.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class LibrarySyncPreferences(
    preferenceStore: PreferenceStore,
) {
    val accountId = preferenceStore.getString(
        Preference.appStateKey("library_sync_account_id"),
        "",
    )

    val accountName = preferenceStore.getString(
        Preference.appStateKey("library_sync_account_name"),
        "",
    )

    val baseline = preferenceStore.getString(
        Preference.appStateKey("library_sync_baseline"),
        "",
    )
}
