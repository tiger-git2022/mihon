package eu.kanade.presentation.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Fork: progress of a running sync that shows it still works without moving the progress itself. The bar keeps its
 * length and a soft light sweeps over its done part every few seconds; the ring keeps its arc, redrawn faintly from 0
 * every few seconds. With animations turned off in the system settings, both stay still.
 */

// One sweep and a pause; the light is soft so the bar does not draw the eye
private const val SHIMMER_MILLIS = 2_400
private const val SHIMMER_SWEEP = 2f / 3
private const val SHIMMER_ALPHA = 0.25f
private const val REDRAW_MILLIS = 3_200

/** A bar [progress] long whose done part a light sweeps over. */
@Composable
fun ShimmerLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    height: Dp = 4.dp,
) {
    val sweep = if (animationsEnabled()) infiniteFraction(SHIMMER_MILLIS) else null
    Canvas(modifier = modifier.height(height)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val done = size.width * progress().coerceIn(0f, 1f)
        if (done <= 0f) return@Canvas
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, done, size.height, radius)) }) {
            drawRect(color = color, size = Size(done, size.height))
            val t = sweep?.value ?: return@clipPath
            if (t > SHIMMER_SWEEP) return@clipPath
            // A band half as long as the done part, from before its start to past its end, eased in and out
            val band = done * 0.5f
            val start = -band + FastOutSlowInEasing.transform(t / SHIMMER_SWEEP) * (done + band)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.35f to Color.White.copy(alpha = SHIMMER_ALPHA * 0.6f),
                    0.5f to Color.White.copy(alpha = SHIMMER_ALPHA),
                    0.65f to Color.White.copy(alpha = SHIMMER_ALPHA * 0.6f),
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
    trackColor: Color = color.copy(alpha = color.alpha * 0.2f),
) {
    val redraw = if (animationsEnabled()) infiniteFraction(REDRAW_MILLIS) else null
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val p = progress().coerceIn(0f, 1f)
        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(
            color.copy(alpha = color.alpha * 0.62f),
            -90f,
            360f * p,
            false,
            topLeft,
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val t = redraw?.value ?: return@Canvas
        // Drawn from 0 to the progress in 72% of the time, held, then faded out
        val drawn = if (t < 0.72f) FastOutSlowInEasing.transform(t / 0.72f) * p else p
        val alpha = when {
            t < 0.12f -> 0.10f + (0.34f - 0.10f) * (t / 0.12f)
            t < 0.72f -> 0.34f + (0.42f - 0.34f) * ((t - 0.12f) / 0.6f)
            t < 0.84f -> 0.42f
            else -> 0.42f * (1f - (t - 0.84f) / 0.16f)
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

/** False with "Remove animations" (animator duration scale 0). */
@Composable
private fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}
