package eu.kanade.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.SyncLinearProgressIndicator
import eu.kanade.tachiyomi.ui.library.LibrarySyncActionState
import eu.kanade.tachiyomi.ui.library.LibrarySyncProgressLine

/**
 * Progress of a running OneDrive library sync below the library toolbar (fork-only feature): a bar for the whole
 * sync and the current step, e.g. "1/5 Library · comparing" with "37/120  6%" on the right. The numbers always show
 * whole, the step ends in "…" when the line is too narrow. A light sweeps over it while the sync runs.
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = MaterialTheme.typography.bodySmall
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = shownText.label,
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Digits of one width: the numbers do not jump while they count
                Text(
                    text = shownText.numbers,
                    style = style.copy(fontFeatureSettings = "tnum"),
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
