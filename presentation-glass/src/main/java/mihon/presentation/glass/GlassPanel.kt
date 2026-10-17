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

/**
 * How a glass part looks. The same four looks, numbers and tints as EhViewer's LiquidGlass.kt, so both apps match.
 */
enum class GlassStyle(val tintAlpha: Float) {
    /** A small control (a button, a search field): clear glass bending strongly at its edges. */
    Control(0.2f),

    /** A large part with text (a top bar, the navigation bar): a little more blur for the text over covers. */
    Panel(0.2f),

    /** A drawer, a dialog or a floating list: blurred glass with a gentle wide edge and a deeper shadow. */
    Sheet(0.4f),

    /** A whole screen in front of the content: a heavy blur, no bending edges. */
    Backdrop(0.4f),
}

// A lit edge in dark themes only: in light themes it read as a white ring, a soft shadow outlines the glass instead.
private val DarkGlassEdge = Highlight(
    width = 1.5.dp,
    blurRadius = 1.dp,
    style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.6f)),
)
private val DarkSheetEdge = Highlight(
    width = 1.5.dp,
    blurRadius = 1.dp,
    style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.5f)),
)
private val LightGlassShadow =
    Shadow(radius = 20.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.12f))
private val SheetShadow = Shadow(radius = 20.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.3f))

/** Glass for floating parts; the caller keeps its original Material background without a source. */
@Composable
fun Modifier.glassPanel(
    shape: Shape = RectangleShape,
    style: GlassStyle = GlassStyle.Panel,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
): Modifier {
    if (backdrop == null || !LocalLiquidGlassEnabled.current) return this
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Light themes tint white: tinted with the gray surface colors, the glass was darker than the page.
    val tint = if (dark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = style.tintAlpha)
    } else {
        Color.White.copy(alpha = style.tintAlpha)
    }
    val edge = when {
        !dark || style == GlassStyle.Backdrop -> null
        style == GlassStyle.Sheet -> DarkSheetEdge
        else -> DarkGlassEdge
    }
    val shadow = when {
        style == GlassStyle.Backdrop -> null
        style == GlassStyle.Sheet -> SheetShadow
        dark -> Shadow.Default
        else -> LightGlassShadow
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            when (style) {
                GlassStyle.Control -> {
                    blur(1.5f.dp.toPx())
                    lens(12f.dp.toPx(), 20f.dp.toPx(), chromaticAberration = true)
                }
                GlassStyle.Panel -> {
                    blur(4f.dp.toPx())
                    lens(16f.dp.toPx(), 28f.dp.toPx(), chromaticAberration = true)
                }
                GlassStyle.Sheet -> {
                    blur(2.5f.dp.toPx())
                    lens(25f.dp.toPx(), 10f.dp.toPx())
                }
                GlassStyle.Backdrop -> blur(10f.dp.toPx())
            }
        },
        highlight = edge?.let { { it } },
        shadow = shadow?.let { { it } },
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
