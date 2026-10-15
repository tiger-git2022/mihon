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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Fork: progress of a running sync that shows it still works without moving the progress itself. The bar keeps its
 * length and a light band of a fixed width sweeps over its done part every few seconds; the ring keeps its arc, redrawn
 * from 0 every few seconds. With animations turned off in the system settings, both stay still.
 */

/**
 * Strength of the animations, as chosen in the animation picker: a wide screen (a tablet) gets a thicker bar, a wider
 * and brighter band and a thicker ring. A band of a fixed width stays as bright on a long bar as on a short one.
 */
class SyncAnimationStyle(
    val barHeight: Dp,
    val bandWidth: Dp,
    val shimmerMillis: Int,
    val shimmerStrength: Float,
    val ringStroke: Dp,
    val redrawMillis: Int,
    val redrawStrength: Float,
)

private val PhoneStyle = SyncAnimationStyle(4.5.dp, 96.dp, 2_200, 1.1f, 2.75.dp, 2_600, 1.1f)
private val TabletStyle = SyncAnimationStyle(6.dp, 120.dp, 2_200, 1.25f, 3.dp, 2_600, 1.25f)

/** [TabletStyle] from 600 dp wide, like the Material window size classes. */
@Composable
fun syncAnimationStyle(): SyncAnimationStyle =
    if (LocalConfiguration.current.screenWidthDp >= 600) TabletStyle else PhoneStyle

// The band sweeps in SHIMMER_SWEEP_PART of the time, then a pause. The light is white: a little of it shows on the dark
// bar of a light theme, the light bar of a dark theme needs more.
private const val SHIMMER_SWEEP_PART = 0.7f
private const val SHIMMER_ALPHA_ON_DARK = 0.55f
private const val SHIMMER_ALPHA_ON_LIGHT = 0.9f

// The ring: the done arc faded, and a layer over it drawn from 0 in REDRAW_DRAW_PART of the time while it brightens to
// REDRAW_PEAK_ALPHA, held, then faded out
private const val RING_BASE_ALPHA = 0.3f
private const val RING_TRACK_ALPHA = 0.18f
private const val REDRAW_DRAW_PART = 0.7f
private const val REDRAW_HOLD_END = REDRAW_DRAW_PART + 0.12f
private const val REDRAW_PEAK_ALPHA = 1f

/** A bar [progress] long whose done part a light sweeps over. */
@Composable
fun ShimmerLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    style: SyncAnimationStyle = syncAnimationStyle(),
) {
    val sweep = if (animationsEnabled()) infiniteFraction(style.shimmerMillis) else null
    val height = style.barHeight
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
            if (t > SHIMMER_SWEEP_PART) return@clipPath
            // A band of a fixed width (at most the done part), from before its start to past its end, eased in and out
            val band = style.bandWidth.toPx().coerceAtMost(done).coerceAtLeast(8.dp.toPx())
            val start = -band + FastOutSlowInEasing.transform(t / SHIMMER_SWEEP_PART) * (done + band)
            val base = if (color.luminance() > 0.4f) SHIMMER_ALPHA_ON_LIGHT else SHIMMER_ALPHA_ON_DARK
            val alpha = (base * style.shimmerStrength).coerceAtMost(1f)
            drawRect(
                // A bright narrow core
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.3f to Color.White.copy(alpha = alpha * 0.2f),
                    0.44f to Color.White.copy(alpha = alpha * 0.85f),
                    0.5f to Color.White.copy(alpha = alpha),
                    0.56f to Color.White.copy(alpha = alpha * 0.85f),
                    0.7f to Color.White.copy(alpha = alpha * 0.2f),
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

/** A ring filled to [progress], its arc redrawn from 0 every few seconds. */
@Composable
fun SoftRedrawRingProgress(
    progress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    style: SyncAnimationStyle = syncAnimationStyle(),
    strokeWidth: Dp = style.ringStroke,
    trackColor: Color = color.copy(alpha = color.alpha * RING_TRACK_ALPHA),
) {
    val peak = (REDRAW_PEAK_ALPHA * style.redrawStrength).coerceAtMost(1f)
    val redraw = if (animationsEnabled()) infiniteFraction(style.redrawMillis) else null
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
        val alpha = peak * when {
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
