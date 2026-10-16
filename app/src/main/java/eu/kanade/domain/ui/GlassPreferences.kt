package eu.kanade.domain.ui

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore

/** Fork-only appearance preferences, kept out of upstream UiPreferences. */
@Inject
@SingleIn(AppScope::class)
class GlassPreferences(preferenceStore: PreferenceStore) {
    val liquidGlass = preferenceStore.getBoolean("tiger_liquid_glass", false)
}
