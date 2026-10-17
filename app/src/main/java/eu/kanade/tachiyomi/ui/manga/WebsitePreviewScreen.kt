package eu.kanade.tachiyomi.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.WebsitePreviewColumnChoices
import eu.kanade.presentation.manga.components.WebsitePreviewColumnsButton
import eu.kanade.presentation.manga.components.rememberWebsitePreviewImageLoader
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.preview.WebsiteThumbnail
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private data class PreviewData(val manga: Manga, val chapters: List<Chapter>, val source: HttpSource?)

/** Fork: page previews use server thumbnails; opening the grid never loads original images or changes read progress. */
data class WebsitePreviewScreen(val mangaId: Long) : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val graph = context.appGraph
        val navigator = LocalNavigator.currentOrThrow
        var retry by remember { mutableStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }
        val loadingErrorText = stringResource(MR.strings.internal_error)
        val data by produceState<PreviewData?>(null, mangaId, retry) {
            value = null
            error = null
            try {
                val interactor = graph.getMangaWithChapters
                val manga = interactor.awaitManga(mangaId)
                value = PreviewData(
                    manga,
                    interactor.awaitChapters(mangaId),
                    graph.sourceManager.getOrStub(manga.source) as? HttpSource,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: loadingErrorText
            }
        }
        val columnsPreference = remember { graph.glassPreferences.websitePreviewColumns }
        val columns by columnsPreference.collectAsState()
        Scaffold(topBar = {
            AppBar(
                title = stringResource(MR.strings.website_page_previews),
                navigateUp = navigator::pop,
                actions = { WebsitePreviewColumnsButton(columns, columnsPreference::set) },
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val loaded = data
                if (loaded == null) {
                    Text(error ?: stringResource(MR.strings.loading), modifier = Modifier.padding(16.dp))
                    if (error !=
                        null
                    ) {
                        TextButton(onClick = { retry++ }) { Text(stringResource(MR.strings.action_retry)) }
                    }
                    return@Column
                }
                val (manga, chapters, loadedSource) = loaded
                val source = loadedSource
                if (source == null) {
                    Text(stringResource(MR.strings.website_previews_unavailable), modifier = Modifier.padding(16.dp))
                    return@Column
                }
                if (chapters.isEmpty()) {
                    Text(stringResource(MR.strings.website_previews_no_chapters), modifier = Modifier.padding(16.dp))
                    return@Column
                }
                var selected by rememberSaveable { mutableLongStateOf(-1L) }
                val ordered = remember(chapters) { chapters.sortedBy { it.chapterNumber } }
                val chapter = ordered.firstOrNull { it.id == selected } ?: ordered.first()
                if (ordered.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        items(ordered, key = { it.id }) { item ->
                            FilterChip(selected = item.id == chapter.id, onClick = {
                                selected = item.id
                            }, label = { Text(item.name) })
                        }
                    }
                }
                val startUrl = remember(source.id, chapter.id, chapter.url) {
                    context.appGraph.websitePreviews.startUrl(source, chapter.toSChapter())
                }
                if (startUrl == null) {
                    Text(stringResource(MR.strings.website_previews_unavailable), modifier = Modifier.padding(16.dp))
                    return@Column
                }
                WebsitePreviewGrid(source, startUrl, columns.coerceIn(WebsitePreviewColumnChoices)) { index ->
                    context.startActivity(
                        ReaderActivity.newIntent(context, manga.id, chapter.id)
                            // ReaderViewModel already restores this explicit page from its SavedStateHandle.
                            .putExtra("page_index", index),
                    )
                }
            }
        }
    }
}

@Composable
private fun WebsitePreviewGrid(source: HttpSource, initialUrl: String, columns: Int, onPageClick: (Int) -> Unit) {
    androidx.compose.runtime.key(source.id, initialUrl) {
        WebsitePreviewGridContent(source, initialUrl, columns, onPageClick)
    }
}

@Composable
private fun WebsitePreviewGridContent(
    source: HttpSource,
    initialUrl: String,
    columns: Int,
    onPageClick: (Int) -> Unit,
) {
    val context = LocalContext.current
    val repository = context.appGraph.websitePreviews
    val scope = rememberCoroutineScope()
    var thumbnails by remember(initialUrl) { mutableStateOf<List<WebsiteThumbnail>>(emptyList()) }
    var nextUrl by remember(initialUrl) { mutableStateOf<String?>(initialUrl) }
    var loading by remember(initialUrl) { mutableStateOf(false) }
    var error by remember(initialUrl) { mutableStateOf<String?>(null) }
    val imageLoader = rememberWebsitePreviewImageLoader(source, initialUrl)

    fun loadNext() {
        val url = nextUrl ?: return
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val page = repository.load(source, url, refresh = thumbnails.isEmpty())
                thumbnails = (thumbnails + page.thumbnails).distinctBy { it.pageUrl }
                nextUrl = page.nextUrl.takeUnless { it == url || page.thumbnails.isEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "HTTP error"
            } finally {
                loading = false
            }
        }
    }
    // Dispose the old pagination scope on chapter changes; its responses must not enter the new chapter.
    androidx.compose.runtime.key(initialUrl) {
        LaunchedEffect(initialUrl) { loadNext() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(thumbnails, key = { _, item -> item.pageUrl }) { index, item ->
                var imageRetry by remember(item.imageUrl) { mutableIntStateOf(0) }
                var imageError by remember(item.imageUrl) { mutableStateOf(false) }
                Column(Modifier.clickable { onPageClick(index) }) {
                    androidx.compose.runtime.key(imageRetry) {
                        AsyncImage(
                            model = item.imageUrl,
                            imageLoader = imageLoader,
                            contentDescription = stringResource(MR.strings.website_preview_page_number, index + 1),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                            onError = { imageError = true },
                            onSuccess = { imageError = false },
                        )
                    }
                    if (imageError) {
                        TextButton(onClick = {
                            imageError = false
                            imageRetry++
                        }) {
                            Text(stringResource(MR.strings.action_retry))
                        }
                    }
                    Text(
                        stringResource(MR.strings.website_preview_page_number, index + 1),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            item {
                if (error != null) {
                    Column {
                        Text(error.orEmpty())
                        TextButton(onClick = ::loadNext) { Text(stringResource(MR.strings.action_retry)) }
                    }
                } else if (nextUrl != null) {
                    LaunchedEffect(nextUrl) { loadNext() }
                    Text(stringResource(MR.strings.loading))
                } else if (thumbnails.isEmpty()) {
                    Text(stringResource(MR.strings.website_previews_unavailable))
                }
            }
        }
    }
}
