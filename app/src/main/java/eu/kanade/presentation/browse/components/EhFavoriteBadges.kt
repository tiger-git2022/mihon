package eu.kanade.presentation.browse.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.data.sync.EhFavoriteMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Cover badge for manga favorited in the EhViewer fork: "本地" for a local favorite, "云:<slot name>" for an
 * E-Hentai cloud favorite (fork-only feature, see [eu.kanade.tachiyomi.data.sync.EhFavoritesStore]). Kept in its own
 * file to avoid merge conflicts.
 */
@Composable
internal fun EhFavoriteBadges(manga: Manga) {
    EhFavoriteBadges(manga.url, manga.title, listOfNotNull(manga.author, manga.artist))
}

@Composable
internal fun EhFavoriteBadges(mangaUrl: String, title: String, authors: List<String> = emptyList()) {
    val favorite = rememberEhFavorite(mangaUrl, title, authors) ?: return
    val label = ehFavoriteLabel(favorite, maxNameLength = BADGE_NAME_LENGTH)
    if (label.isEmpty()) return
    Badge(
        text = label,
        color = MaterialTheme.colorScheme.secondaryContainer,
        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/**
 * The EhViewer favorites matching a manga with the cloud slot names, null if none or if the marks are turned off.
 * Looked up off the main thread: a result the index already knows is shown right away, so badges do not flicker when
 * a list recomposes, and while a new index is searched the previous result for the manga stays.
 */
@Composable
fun rememberEhFavorite(mangaUrl: String, title: String, authors: List<String> = emptyList()): EhFavorite? {
    val context = LocalContext.current
    val graph = remember { context.appGraph }
    val enabled by graph.librarySyncPreferences.showEhViewerFavorites.collectAsState()
    val index = graph.ehFavoritesStore.index.collectAsStateWithLifecycle().value
    val known = remember(enabled, index, mangaUrl, title, authors) {
        if (!enabled) {
            KnownEhFavorite(null)
        } else {
            index.cached(mangaUrl, title, authors)
                ?.let { lookup -> KnownEhFavorite(lookup.match?.let { EhFavorite(it, index.slotNames) }) }
        }
    }
    val found = remember(mangaUrl, title, authors) { mutableStateOf(known?.favorite) }
    LaunchedEffect(known, index, mangaUrl, title, authors) {
        found.value = if (known != null) {
            known.favorite
        } else {
            withContext(Dispatchers.Default) { index.find(mangaUrl, title, authors) }
                ?.let { EhFavorite(it, index.slotNames) }
        }
    }
    return if (known != null) known.favorite else found.value
}

/** A lookup that needs no search, see [rememberEhFavorite]. */
private class KnownEhFavorite(val favorite: EhFavorite?)

data class EhFavorite(val match: EhFavoriteMatch, val slotNames: List<String>) {
    fun slotName(slot: Int): String = slotNames.getOrNull(slot)?.takeIf { it.isNotBlank() } ?: "${slot + 1}"
}

/** "本地 · 云:Slot A/Slot B", slot names cut to [maxNameLength] characters. */
@Composable
fun ehFavoriteLabel(favorite: EhFavorite, maxNameLength: Int = Int.MAX_VALUE): String {
    val local = stringResource(MR.strings.ehviewer_favorite_local)
    val slots = favorite.match.slots.joinToString("/") { slot ->
        val name = favorite.slotName(slot)
        if (name.length > maxNameLength) name.take(maxNameLength) + "…" else name
    }
    val cloud = stringResource(MR.strings.ehviewer_favorite_cloud, slots)
    return listOfNotNull(
        local.takeIf { favorite.match.local },
        cloud.takeIf { favorite.match.slots.isNotEmpty() },
    ).joinToString(" · ")
}

private const val BADGE_NAME_LENGTH = 6
