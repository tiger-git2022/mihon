package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.sync.searchHistoryKey
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchHistoryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

// Global search history (fork-only feature), kept in its own file to avoid merge conflicts.
// Two columns; long queries wrap instead of being cut off.
@Composable
internal fun GlobalSearchHistoryContent(
    history: List<SearchHistoryItem>,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        item(
            key = "search-history-header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            ListItem(
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
