package mihon.presentation.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/** Fork: only opted-in page scaffolds provide a source. Never record the panels into their own source. */
val LocalGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** Glass for floating panels; the caller retains its original Material background without a source. */
@Composable
fun Modifier.glassPanel(
    shape: Shape = RectangleShape,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
    strongBlur: Boolean = false,
): Modifier {
    if (backdrop == null || !LocalLiquidGlassEnabled.current) return this
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tint = if (dark) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.64f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(if (strongBlur) 14.dp.toPx() else 6.dp.toPx())
            lens(18.dp.toPx(), 12.dp.toPx())
        },
        highlight = if (dark) {
            { Highlight(width = 1.dp, style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.4f))) }
        } else {
            null
        },
        shadow = {
            Shadow(radius = 16.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.15f))
        },
        onDrawSurface = { drawRect(tint) },
    )
}

/** Original colors everywhere except inside an opted-in panel. */
@Composable
fun glassContainerColor(original: Color): Color = if (LocalGlassBackdrop.current ==
    null
) {
    original
} else {
    Color.Transparent
}
