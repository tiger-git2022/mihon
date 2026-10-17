package eu.kanade.domain.ui

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore

/** Fork-only appearance preferences (glass, page preview columns), kept out of upstream UiPreferences. */
@Inject
@SingleIn(AppScope::class)
class GlassPreferences(preferenceStore: PreferenceStore) {
    val liquidGlass = preferenceStore.getBoolean("tiger_liquid_glass", false)

    /** Columns of the website page previews, on the details and in the full grid. */
    val websitePreviewColumns = preferenceStore.getInt("tiger_website_preview_columns", 4)
}
