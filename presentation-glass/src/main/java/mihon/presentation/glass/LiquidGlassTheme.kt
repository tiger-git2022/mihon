package mihon.presentation.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/** Fork: opt-in state for glass components. Upstream Material components do not read this value. */
val LocalLiquidGlassEnabled = compositionLocalOf { false }

/**
 * Provides the current opt-in state without replacing MaterialTheme or changing its colors.
 * Future glass components must use their original Material counterpart when this value is false.
 * Previews and compositions without a provider also keep the original components.
 */
@Composable
fun LiquidGlassTheme(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLiquidGlassEnabled provides enabled, content = content)
}
