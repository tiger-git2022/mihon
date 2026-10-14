package eu.kanade.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.SyncLinearProgressIndicator
import eu.kanade.tachiyomi.ui.library.LibrarySyncActionState

/**
 * Progress of a running OneDrive library sync below the library toolbar (fork-only feature): a bar for the whole
 * sync and the current step, e.g. "Syncing 6% (1/5): Library 37/120 (30%)". Its waves move while the sync runs.
 */
@Composable
fun LibrarySyncProgressBar(state: LibrarySyncActionState, modifier: Modifier = Modifier) {
    val text = state.progressText
    val progress = state.progress
    // Keeps showing the last progress while the bar hides
    val last = remember { arrayOfNulls<Pair<String, Float>>(1) }
    if (text != null && progress != null) last[0] = text to progress
    AnimatedVisibility(visible = text != null && progress != null, modifier = modifier) {
        val (shownText, shownProgress) = last[0] ?: return@AnimatedVisibility
        val animated by animateFloatAsState(shownProgress, label = "librarySyncProgress")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            SyncLinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
            Text(
                text = shownText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
