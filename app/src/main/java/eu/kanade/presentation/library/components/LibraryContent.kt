package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.glass.LocalGlassPageContent
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: suspend () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
) {
    // Fork: lists scroll underneath the glass header; category tabs remain pinned above the list.
    val glass = LocalGlassPageContent.current
    val density = LocalDensity.current
    var tabsHeight by remember { mutableStateOf(0.dp) }
    val pagerState = rememberPagerState(currentPage) { categories.size }

    val scope = rememberCoroutineScope()
    var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

    val tabs: @Composable () -> Unit = {
        if (showPageTabs && categories.isNotEmpty() && (categories.size > 1 || !categories.first().isSystemCategory)) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            LibraryTabs(
                categories = categories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                },
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = if (glass) {
                Modifier.fillMaxSize()
            } else {
                Modifier.padding(
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                )
            },
        ) {
            if (!glass) tabs()

            PullRefresh(
                refreshing = isRefreshing,
                enabled = selection.isEmpty(),
                onRefresh = {
                    scope.launch {
                        if (!onRefresh()) return@launch
                        // Fake refresh status but hide it after a second as it's a long running task
                        isRefreshing = true
                        delay(1.seconds)
                        isRefreshing = false
                    }
                },
            ) {
                // Fork: title lines setting
                CompositionLocalProvider(LocalMangaGridStyle provides rememberLibraryGridStyle()) {
                    LibraryPager(
                        state = pagerState,
                        contentPadding = PaddingValues(
                            top = if (glass) contentPadding.calculateTopPadding() + tabsHeight else 0.dp,
                            start = if (glass) {
                                contentPadding.calculateStartPadding(LocalLayoutDirection.current)
                            } else {
                                0.dp
                            },
                            end = if (glass) contentPadding.calculateEndPadding(LocalLayoutDirection.current) else 0.dp,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                        hasActiveFilters = hasActiveFilters,
                        selection = selection,
                        searchQuery = searchQuery,
                        onGlobalSearchClicked = onGlobalSearchClicked,
                        getCategoryForPage = { page -> categories[page] },
                        getDisplayMode = getDisplayMode,
                        getColumnsForOrientation = getColumnsForOrientation,
                        getItemsForCategory = getItemsForCategory,
                        onClickManga = { category, manga ->
                            if (selection.isNotEmpty()) {
                                onToggleSelection(category, manga)
                            } else {
                                onClickManga(manga.manga.id)
                            }
                        },
                        onLongClickManga = onToggleRangeSelection,
                        onClickContinueReading = onContinueReadingClicked,
                    )
                }
            }

            LaunchedEffect(pagerState.currentPage) {
                onChangeCurrentPage(pagerState.currentPage)
            }
        }
        if (glass) {
            Box(
                Modifier.padding(
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                ).onSizeChanged { tabsHeight = with(density) { it.height.toDp() } },
            ) { tabs() }
        }
    }
}
