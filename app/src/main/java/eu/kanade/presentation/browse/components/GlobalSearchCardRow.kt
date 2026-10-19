package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.LocalMangaGridStyle
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.library.components.MangaWideItem
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
) {
    if (titles.isEmpty()) {
        EmptyResultItem()
        return
    }

    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(titles) {
            val title by getManga(it)
            MangaItem(
                title = title.title,
                cover = title.asMangaCover(),
                mangaUrl = title.url,
                authors = listOfNotNull(title.author, title.artist),
                isFavorite = title.favorite,
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
            )
        }
    }
}

@Composable
private fun MangaItem(
    title: String,
    cover: MangaCover,
    mangaUrl: String,
    authors: List<String>,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val badges: @Composable RowScope.() -> Unit = {
        InLibraryBadge(enabled = isFavorite)
        MangaMarkBadges(cover.sourceId, mangaUrl)
        EhFavoriteBadges(mangaUrl, title, authors)
    }
    val coverAlpha = if (isFavorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f
    // Fork: wide cards with the title beside the cover, or the usual cards in the set width
    val style = LocalMangaGridStyle.current
    if (style.wideCards) {
        MangaWideItem(
            coverData = cover,
            title = title,
            onClick = onClick,
            onLongClick = onLongClick,
            coverWidth = 72.dp,
            titleMaxLines = 6,
            titleStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(style.wideCardWidth),
            coverAlpha = coverAlpha,
            badge = badges,
        )
        return
    }
    Box(modifier = Modifier.width(style.narrowCardWidth)) {
        MangaComfortableGridItem(
            title = title,
            titleMaxLines = 3,
            coverData = cover,
            coverBadgeStart = badges,
            coverAlpha = coverAlpha,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
private fun EmptyResultItem() {
    Text(
        text = stringResource(MR.strings.no_results_found),
        modifier = Modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
    )
}
