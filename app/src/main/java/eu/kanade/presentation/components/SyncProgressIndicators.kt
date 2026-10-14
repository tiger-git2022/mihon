package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Progress of a running OneDrive sync (fork-only feature). The waves move for as long as the sync runs, also when
 * the progress stands still for a while (a large step, a slow network): the defaults of Material flatten them near
 * the start and the end. At 0 a filled part has no length to wave, so the endless indicator shows until the first
 * step is done.
 */

private const val RING_AMPLITUDE = 0.6f
private val RingWavelength = 7.dp

/** A bar for the progress of a running sync, [fraction] from 0 to 1, endless without it. */
@Composable
fun SyncLinearProgressIndicator(fraction: Float?, modifier: Modifier = Modifier) {
    if (fraction != null && fraction > 0f) {
        val animated by animateFloatAsState(
            fraction,
            WavyProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "syncProgress",
        )
        LinearWavyProgressIndicator(
            progress = { animated },
            modifier = modifier,
            amplitude = { 1f },
        )
    } else {
        LinearWavyProgressIndicator(modifier = modifier)
    }
}

/** A small ring for a running sync, filled with [fraction] from 0 to 1, turning without it. */
@Composable
fun SyncRingProgressIndicator(
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Dp = 2.5.dp,
) {
    val stroke = Stroke(width = with(LocalDensity.current) { strokeWidth.toPx() }, cap = StrokeCap.Round)
    if (fraction != null && fraction > 0f) {
        val animated by animateFloatAsState(
            fraction,
            WavyProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "syncRing",
        )
        CircularWavyProgressIndicator(
            progress = { animated },
            modifier = modifier.size(size),
            color = color,
            trackColor = color.copy(alpha = 0.25f),
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { RING_AMPLITUDE },
            wavelength = RingWavelength,
            waveSpeed = RingWavelength,
        )
    } else {
        CircularWavyProgressIndicator(
            modifier = modifier.size(size),
            color = color,
            trackColor = Color.Transparent,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = RING_AMPLITUDE,
            wavelength = RingWavelength,
            waveSpeed = RingWavelength,
        )
    }
}
