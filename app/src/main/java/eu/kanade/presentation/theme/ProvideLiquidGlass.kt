package eu.kanade.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import mihon.app.di.appGraph
import mihon.presentation.glass.LiquidGlassTheme
import tachiyomi.presentation.core.util.collectAsState

/** Fork: observe the persisted opt-in once per theme; toggling it updates the composition immediately. */
@Composable
internal fun ProvideLiquidGlass(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.appGraph.glassPreferences }
    val enabled by preferences.liquidGlass.collectAsState()
    LiquidGlassTheme(enabled = enabled, content = content)
}
