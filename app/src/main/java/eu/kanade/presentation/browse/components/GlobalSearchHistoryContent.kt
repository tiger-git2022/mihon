package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.sync.searchHistoryKey
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchHistoryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.presentation.glass.LocalGlassBackdrop
import mihon.presentation.glass.glassPanel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Fork: kept outside the recorded results so the panel never samples itself. */
@Composable
internal fun GlobalSearchHistoryOverlay(
    history: List<SearchHistoryItem>,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    val glass = LocalGlassBackdrop.current != null
    Box(
        Modifier.fillMaxSize().padding(contentPadding).imePadding()
            .glassPanel(shape = RoundedCornerShape(24.dp), strongBlur = true)
            .background(if (glass) Color.Transparent else MaterialTheme.colorScheme.surface),
    ) {
        GlobalSearchHistoryContent(history, PaddingValues(vertical = 12.dp), onClick, onDelete, onClear)
    }
}

// Global search history (fork-only feature), kept in its own file to avoid merge conflicts.
// Two staggered columns: each entry goes to the shorter column, so a long query leaves no gap beside it.
@Composable
internal fun GlobalSearchHistoryContent(
    history: List<SearchHistoryItem>,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        item(
            key = "search-history-header",
            span = StaggeredGridItemSpan.FullLine,
        ) {
            ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(MR.strings.recent_searches)) },
                trailingContent = {
                    TextButton(onClick = onClear) {
                        Text(stringResource(MR.strings.clear_search_history))
                    }
                },
            )
        }
        items(
            items = history,
            key = { searchHistoryKey(it.query) },
        ) { item ->
            SearchHistoryCell(
                item = item,
                onClick = { onClick(item.query) },
                onDelete = { onDelete(item.query) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun SearchHistoryCell(
    item: SearchHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (LocalGlassBackdrop.current != null) 0.55f else 1f,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    text = item.query,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                )
                if (item.deviceName != null) {
                    Text(
                        text = stringResource(MR.strings.sync_from_device, item.deviceName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Close,
                    contentDescription = stringResource(MR.strings.action_delete),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
