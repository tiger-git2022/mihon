package eu.kanade.presentation.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.sync.ChangeSyncCountdown
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Schedule

// Share of the way to the new value each frame: a change fills the ring and the dial up again smoothly
private const val REFILL_STEP = 0.18f

/**
 * The clock of changes waiting for a sync (fork-only feature), with how long they still wait. The ring is the minute
 * after the last change, filled up again by every change; the dial the five minutes the changes wait at most, it only
 * shrinks. Near the five minutes a new change no longer fills the ring.
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
            batch = follow(batch, countdown.batchLeft(now))
        }
    }
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(24.dp)) {
            // The dial, inside the circle of the clock icon below
            val dial = 6.5.dp.toPx()
            if (batch > 0f) {
                drawArc(
                    color = accent.copy(alpha = 0.4f),
                    startAngle = -90f,
                    sweepAngle = 360f * batch,
                    useCenter = true,
                    topLeft = Offset(center.x - dial, center.y - dial),
                    size = Size(dial * 2, dial * 2),
                )
            }
            val stroke = 2.dp.toPx()
            val radius = size.minDimension / 2 - stroke / 2
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(content.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (minute > 0f) {
                drawArc(
                    accent,
                    -90f,
                    360f * minute,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Icon(imageVector = MaterialSymbols.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/** Down with the time at once, up again (a new change) a step each frame. */
private fun follow(shown: Float, target: Float) =
    if (shown < 0f || target < shown) target else shown + (target - shown) * REFILL_STEP
