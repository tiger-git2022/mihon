package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.browse.components.rememberMangaMarkStatus
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Visibility
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.i18n.stringResource

/**
 * "Read before" toggle in the manga action row (fork-only feature). The mark is independent of
 * the library and synced through OneDrive.
 */
@Composable
internal fun RowScope.ReadMarkActionButton(manga: Manga) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { context.appGraph.librarySyncService }
    val autoSync = remember { context.appGraph.libraryAutoSync }
    val status = rememberMangaMarkStatus(manga.source, manga.url)

    MangaActionButton(
        title = stringResource(if (status.read) MR.strings.manga_mark_read else MR.strings.manga_mark_as_read),
        icon = MaterialSymbols.RoundedFilled.Visibility,
        color = if (status.read) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        },
        onClick = {
            scope.launch {
                runCatching { service.setRead(manga, !status.read) }
                    .onSuccess { autoSync.notifyChanged() }
                    .onFailure { context.toast(it.message) }
            }
        },
    )
}
