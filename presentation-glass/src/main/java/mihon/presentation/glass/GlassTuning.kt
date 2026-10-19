package mihon.presentation.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Fork: blur and tint of the glass, set in Settings → Appearance. [panelBlur] and [panelTint] are for the bars (top
 * bars, the navigation bar, the sync progress), [backdropBlur] and [backdropTint] for whole-screen glass in front of
 * the page (the search history). Blur is in dp, tint is how much of the surface color covers the glass (0 clear, 1
 * solid). The defaults are EhViewer's numbers.
 */
@Immutable
data class GlassTuning(
    val panelBlur: Float = DEFAULT_PANEL_BLUR,
    val panelTint: Float = DEFAULT_PANEL_TINT,
    val backdropBlur: Float = DEFAULT_BACKDROP_BLUR,
    val backdropTint: Float = DEFAULT_BACKDROP_TINT,
) {
    companion object {
        const val DEFAULT_PANEL_BLUR = 4f
        const val DEFAULT_PANEL_TINT = 0.2f
        const val DEFAULT_BACKDROP_BLUR = 10f
        const val DEFAULT_BACKDROP_TINT = 0.4f
    }
}

val LocalGlassTuning = staticCompositionLocalOf { GlassTuning() }
