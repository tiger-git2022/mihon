package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private const val STRIP_THUMBNAILS = 20

/**
 * Fork: the first page previews right on the details, like the gallery details of EhViewer. Shown only once the
 * website returned real thumbnails, so other sources keep their details unchanged; "All" opens the full grid.
 */
@Composable
fun WebsitePreviewStrip(manga: Manga, source: Source, chapters: List<Chapter>) {
    val httpSource = source as? HttpSource ?: return
    val chapter = remember(chapters) { chapters.minByOrNull { it.chapterNumber } } ?: return
    val context = LocalContext.current
    val repository = context.appGraph.websitePreviews
    val url = remember(httpSource.id, chapter.id, chapter.url) { repository.startUrl(httpSource, chapter.toSChapter()) }
        ?: return
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
    if (thumbnails.isEmpty()) return
    val navigator = LocalNavigator.currentOrThrow
    val imageLoader = rememberWebsitePreviewImageLoader(httpSource, url)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.website_page_previews),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { navigator.push(WebsitePreviewScreen(manga.id)) }) {
                Text(stringResource(MR.strings.website_previews_show_all))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(thumbnails.take(STRIP_THUMBNAILS), key = { _, item -> item.pageUrl }) { index, item ->
                val label = stringResource(MR.strings.website_preview_page_number, index + 1)
                AsyncImage(
                    model = item.imageUrl,
                    imageLoader = imageLoader,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(84.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            context.startActivity(
                                ReaderActivity.newIntent(context, manga.id, chapter.id)
                                    // ReaderViewModel restores this explicit page from its SavedStateHandle.
                                    .putExtra("page_index", index),
                            )
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
