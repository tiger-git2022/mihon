package eu.kanade.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import mihon.app.di.appGraph
import mihon.presentation.glass.GlassTuning
import mihon.presentation.glass.LiquidGlassTheme
import mihon.presentation.glass.LocalGlassTuning
import tachiyomi.presentation.core.util.collectAsState

/**
 * Fork: observe the persisted opt-in and the glass tuning once per theme; changing either updates the composition
 * immediately.
 */
@Composable
internal fun ProvideLiquidGlass(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.appGraph.glassPreferences }
    val enabled by preferences.liquidGlass.collectAsState()
    val barBlur by preferences.glassBarBlur.collectAsState()
    val barOpacity by preferences.glassBarOpacity.collectAsState()
    val overlayBlur by preferences.glassOverlayBlur.collectAsState()
    val overlayOpacity by preferences.glassOverlayOpacity.collectAsState()
    val tuning = remember(barBlur, barOpacity, overlayBlur, overlayOpacity) {
        GlassTuning(
            panelBlur = barBlur.coerceAtLeast(0).toFloat(),
            panelTint = barOpacity.coerceIn(0, 100) / 100f,
            backdropBlur = overlayBlur.coerceAtLeast(0).toFloat(),
            backdropTint = overlayOpacity.coerceIn(0, 100) / 100f,
        )
    }
    CompositionLocalProvider(LocalGlassTuning provides tuning) {
        LiquidGlassTheme(enabled = enabled, content = content)
    }
}
