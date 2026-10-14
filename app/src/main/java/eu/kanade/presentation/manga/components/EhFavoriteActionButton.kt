package eu.kanade.presentation.manga.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import eu.kanade.presentation.browse.components.EhFavorite
import eu.kanade.presentation.browse.components.ehFavoriteLabel
import eu.kanade.presentation.browse.components.rememberEhFavorite
import eu.kanade.tachiyomi.data.sync.EhIndexedGallery
import eu.kanade.tachiyomi.util.system.toast
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Favorite
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Shown in the manga action row when the manga is favorited in the EhViewer fork (fork-only feature); opens the list
 * of the matching EhViewer galleries.
 */
@Composable
internal fun RowScope.EhFavoriteActionButton(manga: Manga) {
    val authors = remember(manga.author, manga.artist) { listOfNotNull(manga.author, manga.artist) }
    val favorite = rememberEhFavorite(manga.url, manga.title, authors) ?: return
    val label = ehFavoriteLabel(favorite, maxNameLength = BUTTON_NAME_LENGTH)
    if (label.isEmpty()) return
    var showDialog by rememberSaveable { mutableStateOf(false) }

    MangaActionButton(
        title = label,
        icon = MaterialSymbols.RoundedFilled.Favorite,
        color = MaterialTheme.colorScheme.primary,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        EhFavoriteDialog(favorite, onDismissRequest = { showDialog = false })
    }
}

@Composable
private fun EhFavoriteDialog(favorite: EhFavorite, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.ehviewer_favorite_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (favorite.match.byGalleryId) {
                            MR.strings.ehviewer_favorite_by_gallery_id
                        } else {
                            MR.strings.ehviewer_favorite_by_title
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                favorite.match.galleries.forEach { gallery ->
                    GalleryItem(
                        gallery = gallery,
                        favorite = favorite,
                        onOpen = {
                            val url = "https://e-hentai.org/g/${gallery.gid}/${gallery.token}/"
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                                .onFailure { context.toast(it.message) }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
private fun GalleryItem(gallery: EhIndexedGallery, favorite: EhFavorite, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = gallery.titleJpn?.takeIf { it.isNotBlank() } ?: gallery.title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!gallery.titleJpn.isNullOrBlank() && !gallery.title.isNullOrBlank()) {
            Text(text = gallery.title, style = MaterialTheme.typography.bodySmall)
        }
        val places = listOfNotNull(
            stringResource(MR.strings.ehviewer_favorite_local_full).takeIf { gallery.local },
            stringResource(MR.strings.ehviewer_favorite_cloud_full, favorite.slotName(gallery.slot)).takeIf {
                gallery.inCloud
            },
        )
        Text(
            text = places.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (gallery.token.isNotBlank()) {
            TextButton(onClick = onOpen) {
                Text(stringResource(MR.strings.ehviewer_favorite_open))
            }
        }
    }
}

private const val BUTTON_NAME_LENGTH = 4
