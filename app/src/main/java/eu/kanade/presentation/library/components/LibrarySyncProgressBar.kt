package eu.kanade.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.SyncLinearProgressIndicator
import eu.kanade.tachiyomi.ui.library.LibrarySyncActionState
import eu.kanade.tachiyomi.ui.library.LibrarySyncProgressLine

/**
 * Progress of a running OneDrive library sync below the library toolbar (fork-only feature): a bar for the whole
 * sync and the current step, the whole "Syncing 6% (1/5): Library 37/120 (30%)" where it fits, else "1/5 Library"
 * ending in "…" with "37/120  6%" whole on the right. A light sweeps over it while the sync runs.
 */
@Composable
fun LibrarySyncProgressBar(state: LibrarySyncActionState, modifier: Modifier = Modifier) {
    val text = state.progressLine
    val progress = state.progress
    // Keeps showing the last progress while the bar hides
    val last = remember { arrayOfNulls<Pair<LibrarySyncProgressLine, Float>>(1) }
    if (text != null && progress != null) last[0] = text to progress
    AnimatedVisibility(visible = text != null && progress != null, modifier = modifier) {
        val (shownText, shownProgress) = last[0] ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            SyncLinearProgressIndicator(fraction = shownProgress, modifier = Modifier.fillMaxWidth())
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val style = MaterialTheme.typography.bodySmall
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                // The whole text where it fits (a tablet), else the step cut short and the numbers whole
                val measurer = rememberTextMeasurer()
                val maxWidth = constraints.maxWidth
                val fits = remember(shownText.full, style, maxWidth) {
                    measurer.measure(text = shownText.full, style = style, maxLines = 1, softWrap = false)
                        .size.width <= maxWidth
                }
                // Digits of one width: the numbers do not jump while they count
                val numberStyle = style.copy(fontFeatureSettings = "tnum")
                // At a large font scale the count may not fit beside the step: the percent alone then
                val numbers = remember(shownText.numbers, numberStyle, maxWidth) {
                    val width = measurer
                        .measure(text = shownText.numbers, style = numberStyle, maxLines = 1, softWrap = false)
                        .size.width
                    if (width > maxWidth * 0.6f) shownText.numbers.substringAfterLast("  ") else shownText.numbers
                }
                if (fits) {
                    Text(text = shownText.full, style = style, color = color, maxLines = 1)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shownText.label,
                            style = style,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = numbers,
                            style = numberStyle,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
