package eu.kanade.presentation.manga.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.data.preview.WebsitePreviewParser
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.manga.WebsitePreviewScreen
import kotlinx.coroutines.CancellationException
import mihon.app.di.appGraph
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Fork: no action without actual website thumbnails; inspecting metadata never requests page images. */
@Composable
internal fun rememberWebsitePreviewActions(manga: Manga): List<AppBar.OverflowAction> {
    val context = LocalContext.current
    val graph = context.appGraph
    val available by produceState(false, manga.id, manga.url, manga.source) {
        value = false
        value = try {
            val source = graph.sourceManager.getOrStub(manga.source) as? HttpSource
                ?: return@produceState
            val url = source.getMangaUrl(manga.toSManga())
            if (!WebsitePreviewParser.supports(url)) return@produceState
            graph.websitePreviews.load(source, url).thumbnails.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
    if (!available) return emptyList()
    val navigator = LocalNavigator.currentOrThrow
    return listOf(
        AppBar.OverflowAction(
            title = stringResource(MR.strings.website_page_previews),
            onClick = { navigator.push(WebsitePreviewScreen(manga.id)) },
        ),
    )
}
