package mihon.presentation.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Fork: a page-owned content source for future glass components, allocated only while enabled.
 * Pass the page background so refraction does not sample transparent gaps, as in EhViewer.
 * Record only the content behind the glass; recording the glass itself would create a feedback loop.
 */
@Composable
fun rememberGlassBackdrop(background: Color): LayerBackdrop? {
    if (!LocalLiquidGlassEnabled.current) return null

    val onDraw: ContentDrawScope.() -> Unit = remember(background) {
        {
            drawRect(background)
            drawContent()
        }
    }
    return rememberLayerBackdrop(onDraw = onDraw)
}

/** Records page content for the glass in front of it; leaves the original drawing path intact when off. */
fun Modifier.glassContent(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop)
