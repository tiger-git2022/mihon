package eu.kanade.presentation.components

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Fork: progress of a running sync that shows it still works without moving the progress itself. The bar keeps its
 * length and a soft light sweeps over its done part every few seconds; the ring keeps its arc, redrawn faintly from 0
 * every few seconds. With animations turned off in the system settings, both stay still.
 */

// One sweep and a pause. The light is white: a little of it shows on the dark bar of a light theme, the light bar of a
// dark theme needs more.
private const val SHIMMER_SWEEP_MILLIS = 1_600
private const val SHIMMER_PAUSE_MILLIS = 900
private const val SHIMMER_ALPHA_ON_DARK = 0.3f
private const val SHIMMER_ALPHA_ON_LIGHT = 0.65f

// The ring: the done arc a bit faded, and a layer over it drawn from 0 in REDRAW_DRAW_PART of the time while it
// brightens to REDRAW_PEAK_ALPHA, held, then faded out
private const val RING_BASE_ALPHA = 0.5f
private const val RING_TRACK_ALPHA = 0.18f
private const val REDRAW_MILLIS = 3_200
private const val REDRAW_DRAW_PART = 0.7f
private const val REDRAW_HOLD_END = REDRAW_DRAW_PART + 0.12f
private const val REDRAW_PEAK_ALPHA = 0.7f

/** A bar [progress] long whose done part a light sweeps over. */
@Composable
fun ShimmerLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    height: Dp = 4.dp,
) {
    val sweep = if (animationsEnabled()) infiniteFraction(SHIMMER_SWEEP_MILLIS + SHIMMER_PAUSE_MILLIS) else null
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // From the start of the line: the right in right-to-left layouts, like the Material indicator
    Canvas(
        modifier = modifier
            .height(height)
            .progressSemantics(progress().coerceIn(0f, 1f))
            .graphicsLayer { if (rtl) scaleX = -1f },
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val done = size.width * progress().coerceIn(0f, 1f)
        if (done <= 0f) return@Canvas
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, done, size.height, radius)) }) {
            drawRect(color = color, size = Size(done, size.height))
            val t = sweep?.value ?: return@clipPath
            val sweepPart = SHIMMER_SWEEP_MILLIS.toFloat() / (SHIMMER_SWEEP_MILLIS + SHIMMER_PAUSE_MILLIS)
            if (t > sweepPart) return@clipPath
            // A band half as long as the done part, from before its start to past its end, eased in and out
            val band = (done * 0.5f).coerceAtLeast(8.dp.toPx())
            val start = -band + FastOutSlowInEasing.transform(t / sweepPart) * (done + band)
            val alpha = if (color.luminance() > 0.4f) SHIMMER_ALPHA_ON_LIGHT else SHIMMER_ALPHA_ON_DARK
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.2f to Color.White.copy(alpha = alpha * 0.35f),
                    0.35f to Color.White.copy(alpha = alpha * 0.85f),
                    0.5f to Color.White.copy(alpha = alpha),
                    0.65f to Color.White.copy(alpha = alpha * 0.85f),
                    0.8f to Color.White.copy(alpha = alpha * 0.35f),
                    1f to Color.Transparent,
                    startX = start,
                    endX = start + band,
                ),
                topLeft = Offset(start, 0f),
                size = Size(band, size.height),
            )
        }
    }
}

/** A ring filled to [progress], its arc redrawn faintly from 0 every few seconds. */
@Composable
fun SoftRedrawRingProgress(
    progress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp,
    trackColor: Color = color.copy(alpha = color.alpha * RING_TRACK_ALPHA),
) {
    val redraw = if (animationsEnabled()) infiniteFraction(REDRAW_MILLIS) else null
    Canvas(modifier = modifier.progressSemantics(progress().coerceIn(0f, 1f))) {
        val stroke = strokeWidth.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val p = progress().coerceIn(0f, 1f)
        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(
            color.copy(alpha = color.alpha * RING_BASE_ALPHA),
            -90f,
            360f * p,
            false,
            topLeft,
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val t = redraw?.value ?: return@Canvas
        val drawn = if (t < REDRAW_DRAW_PART) FastOutSlowInEasing.transform(t / REDRAW_DRAW_PART) * p else p
        val alpha = REDRAW_PEAK_ALPHA * when {
            t < 0.12f -> 0.24f + (0.81f - 0.24f) * (t / 0.12f)
            t < REDRAW_DRAW_PART -> 0.81f + (1f - 0.81f) * ((t - 0.12f) / (REDRAW_DRAW_PART - 0.12f))
            t < REDRAW_HOLD_END -> 1f
            else -> 1f - (t - REDRAW_HOLD_END) / (1f - REDRAW_HOLD_END)
        }
        if (drawn > 0f) {
            drawArc(
                color.copy(alpha = color.alpha * alpha),
                -90f,
                360f * drawn,
                false,
                topLeft,
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun infiniteFraction(
    millis: Int,
): State<Float> = rememberInfiniteTransition(label = "syncProgress").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
    label = "syncProgressFraction",
)

/** False with "Remove animations" (animator duration scale 0); follows changes to that setting. */
@Composable
private fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    val enabled = remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled.value = read()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        resolver.registerContentObserver(uri, false, observer)
        // The setting may have changed between the first read and registering
        enabled.value = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled.value
}
