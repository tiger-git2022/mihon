package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.data.preview.WebsiteThumbnail
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.manga.WebsitePreviewScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.CancellationException
import mihon.app.di.appGraph
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private const val DETAIL_THUMBNAILS = 20

/** Column counts offered for the preview grids. */
val WebsitePreviewColumnChoices = 2..6

/** Fork: the loaded first page of previews of a manga; null while loading or when its website has none. */
@Stable
class WebsitePreviewState(
    val manga: Manga,
    val chapter: Chapter,
    val thumbnails: List<WebsiteThumbnail>,
    val imageLoader: ImageLoader,
    val columns: Int,
    val onColumnsChange: (Int) -> Unit,
    val onPageClick: (Int) -> Unit,
    val onShowAll: () -> Unit,
)

/**
 * Fork: previews like the gallery details of EhViewer, below the chapters. Nothing is shown until the website returned
 * real thumbnails, so the details of other sources stay unchanged.
 */
@Composable
fun rememberWebsitePreviewState(manga: Manga, source: Source, chapters: List<Chapter>): WebsitePreviewState? {
    val httpSource = source as? HttpSource ?: return null
    val chapter = remember(chapters) { chapters.minByOrNull { it.chapterNumber } } ?: return null
    val context = LocalContext.current
    val graph = context.appGraph
    val repository = graph.websitePreviews
    val url = remember(httpSource.id, chapter.id, chapter.url) { repository.startUrl(httpSource, chapter.toSChapter()) }
        ?: return null
    val thumbnails by produceState(emptyList<WebsiteThumbnail>(), httpSource.id, url) {
        value = emptyList()
        value = try {
            repository.load(httpSource, url).thumbnails
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    val columnsPreference = remember { graph.glassPreferences.websitePreviewColumns }
    val columns by columnsPreference.collectAsState()
    if (thumbnails.isEmpty()) return null
    val navigator = LocalNavigator.currentOrThrow
    val imageLoader = rememberWebsitePreviewImageLoader(httpSource, url)
    return WebsitePreviewState(
        manga = manga,
        chapter = chapter,
        thumbnails = thumbnails,
        imageLoader = imageLoader,
        columns = columns.coerceIn(WebsitePreviewColumnChoices),
        onColumnsChange = columnsPreference::set,
        onPageClick = { index ->
            context.startActivity(
                ReaderActivity.newIntent(context, manga.id, chapter.id)
                    // ReaderViewModel restores this explicit page from its SavedStateHandle.
                    .putExtra("page_index", index),
            )
        },
        onShowAll = { navigator.push(WebsitePreviewScreen(manga.id)) },
    )
}

/** The previews as list rows: a header, the first [DETAIL_THUMBNAILS] pages in a grid, then "Show all". */
fun LazyListScope.websitePreviewItems(state: WebsitePreviewState?) {
    if (state == null) return
    item(key = "website_previews_header", contentType = "website_previews_header") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.website_page_previews),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            WebsitePreviewColumnsButton(state.columns, state.onColumnsChange)
        }
    }
    val shown = state.thumbnails.take(DETAIL_THUMBNAILS)
    shown.chunked(state.columns).forEachIndexed { row, items ->
        item(key = "website_previews_row_${state.columns}_$row", contentType = "website_previews_row") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (column in 0..<state.columns) {
                    val index = row * state.columns + column
                    val item = items.getOrNull(column)
                    Box(Modifier.weight(1f)) {
                        if (item != null) {
                            WebsitePreviewThumbnail(item, index, state.imageLoader) { state.onPageClick(index) }
                        }
                    }
                }
            }
        }
    }
    item(key = "website_previews_all", contentType = "website_previews_all") {
        OutlinedButton(
            onClick = state.onShowAll,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(MR.strings.website_previews_show_all))
        }
    }
}

/** A thumbnail with its page number below, as in EhViewer. */
@Composable
fun WebsitePreviewThumbnail(item: WebsiteThumbnail, index: Int, imageLoader: ImageLoader, onClick: () -> Unit) {
    val label = stringResource(MR.strings.website_preview_page_number, index + 1)
    Column(Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = item.imageUrl,
            imageLoader = imageLoader,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** "N columns", choosing among [WebsitePreviewColumnChoices]; shared by the details and the full grid. */
@Composable
fun WebsitePreviewColumnsButton(columns: Int, onColumnsChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(MR.strings.website_previews_columns, columns))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (choice in WebsitePreviewColumnChoices) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.website_previews_columns, choice)) },
                    onClick = {
                        expanded = false
                        onColumnsChange(choice)
                    },
                )
            }
        }
    }
}

/** Thumbnails load through the source's own client and headers, with the preview page as referer. */
@Composable
internal fun rememberWebsitePreviewImageLoader(source: HttpSource, referer: String): ImageLoader {
    val context = LocalContext.current
    val imageLoader = remember(source.id, referer) {
        val client = source.client.newBuilder().addInterceptor { chain ->
            val request = chain.request().newBuilder()
            for ((name, value) in source.headers) {
                if (chain.request().header(name) == null) request.addHeader(name, value)
            }
            if (chain.request().header("Referer") == null) request.header("Referer", referer)
            chain.proceed(request.build())
        }.build()
        ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }.build()
    }
    DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
    return imageLoader
}
