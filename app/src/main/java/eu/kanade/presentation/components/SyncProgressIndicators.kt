package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Progress of a running OneDrive sync (fork-only feature). At 0 a filled part has no length and nothing moves, so
 * the endless indicator shows until the first step is done: a sync waiting on the network still reads as running.
 * With progress, the bar shimmers and the ring is redrawn softly, see SyncProgressAnimations.
 */

/** A bar for the progress of a running sync, [fraction] from 0 to 1, endless without it. */
@Composable
fun SyncLinearProgressIndicator(fraction: Float?, modifier: Modifier = Modifier) {
    if (fraction != null && fraction > 0f) {
        val animated by animateFloatAsState(
            fraction,
            ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "syncProgress",
        )
        // A light sweeps over the done part, see ShimmerLinearProgress
        ShimmerLinearProgress(progress = { animated }, modifier = modifier)
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

/** A small ring for a running sync, filled with [fraction] from 0 to 1, turning without it. */
@Composable
fun SyncRingProgressIndicator(
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Dp = syncAnimationStyle().ringStroke,
) {
    if (fraction != null && fraction > 0f) {
        val animated by animateFloatAsState(
            fraction,
            ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "syncRing",
        )
        // The done arc is redrawn now and then, see SoftRedrawRingProgress
        SoftRedrawRingProgress(progress = {
            animated
        }, color = color, modifier = modifier.size(size), strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = strokeWidth)
    }
}
