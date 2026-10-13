package eu.kanade.presentation.browse.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Schedule
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

// Global search history list (fork-only feature), kept in its own file to avoid merge conflicts.
@Composable
internal fun GlobalSearchHistoryContent(
    history: List<String>,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(contentPadding = contentPadding) {
        item(key = "search-history-header") {
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
            key = { it },
        ) { query ->
            ListItem(
                headlineContent = {
                    Text(
                        text = query,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(query) }
                    .animateItem(),
                leadingContent = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Schedule,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(query) }) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Delete,
                            contentDescription = stringResource(MR.strings.action_delete),
                        )
                    }
                },
            )
        }
    }
}
