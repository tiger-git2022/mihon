package eu.kanade.presentation.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.sync.ChangeSyncCountdown
import kotlin.math.ceil

// Share of the way to the new value each frame: a change fills the ring up again smoothly, a minute fades out smoothly
private const val REFILL_STEP = 0.18f

// The five minutes in steps of a minute
private const val BATCH_STEPS = 5

/**
 * The clock of changes waiting for a sync (fork-only feature), with how long they still wait. The outer ring is the
 * minute after the last change, filled up again by every change. The circle of the clock itself is the five minutes the
 * changes wait at most: one piece in one, a fifth shorter every minute, its lost part left faint. Near the five minutes
 * a new change no longer fills the ring.
 */
@Composable
fun SyncCountdownIcon(countdown: ChangeSyncCountdown, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val content = LocalContentColor.current
    // -1 before the first frame: shown at once, not filled up from nothing
    var minute by remember { mutableFloatStateOf(-1f) }
    var batch by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(countdown) {
        while (true) {
            withFrameMillis { }
            val now = SystemClock.elapsedRealtime()
            minute = follow(minute, countdown.minuteLeft(now))
            // Whole minutes: a fifth of the circle each
            val steps = ceil(countdown.batchLeft(now) * BATCH_STEPS) / BATCH_STEPS
            batch = if (batch < 0f) steps else batch + (steps - batch) * REFILL_STEP
        }
    }
    Canvas(modifier = modifier.size(24.dp)) {
        // The minute, outside
        val ring = 2.dp.toPx()
        val ringRadius = size.minDimension / 2 - ring / 2
        arc(content.copy(alpha = 0.18f), 1f, ringRadius, ring)
        if (minute > 0f) arc(accent, minute, ringRadius, ring)
        // The clock: its circle is the five minutes, then its hands
        val line = 1.6.dp.toPx()
        val clockRadius = 7.5.dp.toPx()
        arc(content.copy(alpha = 0.25f), 1f, clockRadius, line)
        if (batch > 0f) arc(content, batch, clockRadius, line)
        drawLine(content, center, center + Offset(0f, -4.5.dp.toPx()), line, StrokeCap.Round)
        drawLine(content, center, center + Offset(3.dp.toPx(), 1.8.dp.toPx()), line, StrokeCap.Round)
    }
}

/** An arc from the top, clockwise, [fraction] of a circle. */
private fun DrawScope.arc(color: Color, fraction: Float, radius: Float, width: Float) {
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * fraction,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width, cap = StrokeCap.Round),
    )
}

/** Down with the time at once, up again (a new change) a step each frame. */
private fun follow(shown: Float, target: Float) =
    if (shown < 0f || target < shown) target else shown + (target - shown) * REFILL_STEP
