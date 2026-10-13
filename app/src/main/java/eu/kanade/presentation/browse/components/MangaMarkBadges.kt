package eu.kanade.presentation.browse.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.data.sync.MangaMarkStatus
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Visibility
import tachiyomi.presentation.core.components.Badge

/**
 * Cover badge for the fork's "read before" mark (eye). Kept in its own file to avoid merge
 * conflicts.
 */
@Composable
internal fun MangaMarkBadges(sourceId: Long, mangaUrl: String) {
    val status = rememberMangaMarkStatus(sourceId, mangaUrl)
    if (status.read) {
        Badge(
            imageVector = MaterialSymbols.RoundedFilled.Visibility,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
fun rememberMangaMarkStatus(sourceId: Long, mangaUrl: String): MangaMarkStatus {
    val context = LocalContext.current
    val store = remember { context.appGraph.mangaMarkStore }
    val index by store.index.collectAsStateWithLifecycle()
    return remember(index, sourceId, mangaUrl) { index.status(sourceId, mangaUrl) }
}
