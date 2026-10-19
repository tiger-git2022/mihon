package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

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

/** Fork: blur and opacity of the glass, shown while the glass is on; changes show at once on the pages. */
@Composable
internal fun getGlassTuningGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val preferences = remember { context.appGraph.glassPreferences }
    val enabled by preferences.liquidGlass.collectAsState()
    val dp: @Composable (Int) -> String = { stringResource(MR.strings.pref_glass_blur_value, it) }
    val percent: @Composable (Int) -> String = { "$it%" }
    return Preference.PreferenceGroup(
        title = stringResource(MR.strings.pref_glass_tuning),
        visible = enabled,
        preferenceItems = listOf(
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.glassBarBlur,
                title = stringResource(MR.strings.pref_glass_bar_blur),
                valueText = dp,
                valueRange = 0..20,
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.glassBarOpacity,
                title = stringResource(MR.strings.pref_glass_bar_opacity),
                subtitle = stringResource(MR.strings.pref_glass_opacity_summary),
                valueText = percent,
                valueRange = 0..100 step 5,
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.glassOverlayBlur,
                title = stringResource(MR.strings.pref_glass_overlay_blur),
                subtitle = stringResource(MR.strings.pref_glass_overlay_summary),
                valueText = dp,
                valueRange = 0..30,
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.glassOverlayOpacity,
                title = stringResource(MR.strings.pref_glass_overlay_opacity),
                valueText = percent,
                valueRange = 0..100 step 5,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_glass_reset),
                onClick = {
                    preferences.glassBarBlur.delete()
                    preferences.glassBarOpacity.delete()
                    preferences.glassOverlayBlur.delete()
                    preferences.glassOverlayOpacity.delete()
                },
            ),
        ),
    )
}
