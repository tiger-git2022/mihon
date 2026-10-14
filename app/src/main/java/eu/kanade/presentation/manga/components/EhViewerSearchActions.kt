package eu.kanade.presentation.manga.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.data.sync.CrossAppSearch
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * "Search in EhViewer" entries of the manga menu (fork-only feature): the title without its bracket groups, and the
 * artist when one is known.
 */
@Composable
fun rememberEhViewerSearchActions(manga: Manga): List<AppBar.OverflowAction> {
    val context = LocalContext.current
    val title = remember(manga.title) { CrossAppSearch.coreTitle(manga.title) }
    val artist = remember(manga.title, manga.author, manga.artist) {
        CrossAppSearch.artist(manga.title, listOfNotNull(manga.artist, manga.author))
    }
    val notInstalled = stringResource(MR.strings.ehviewer_not_installed)
    fun search(query: String) {
        if (!CrossAppSearch.searchInEhViewer(context, query)) context.toast(notInstalled)
    }
    return listOfNotNull(
        AppBar.OverflowAction(
            title = stringResource(MR.strings.ehviewer_search_title),
            onClick = { search(title) },
        ),
        artist?.let {
            AppBar.OverflowAction(
                title = stringResource(MR.strings.ehviewer_search_artist, it),
                onClick = { search(it) },
            )
        },
    )
}
