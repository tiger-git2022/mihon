package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Fork: the glass opt-in lives alongside theme settings without modifying upstream preferences. */
@Composable
internal fun getLiquidGlassPreference(): Preference.PreferenceItem.SwitchPreference {
    val context = LocalContext.current
    val preferences = remember { context.appGraph.glassPreferences }
    return Preference.PreferenceItem.SwitchPreference(
        preference = preferences.liquidGlass,
        title = stringResource(MR.strings.pref_liquid_glass),
        subtitle = stringResource(MR.strings.pref_liquid_glass_summary),
    )
}
