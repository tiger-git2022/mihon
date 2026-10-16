package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.glass.LocalGlassPageContent
import eu.kanade.presentation.library.components.LibrarySyncProgressBar
import eu.kanade.tachiyomi.ui.library.rememberLibrarySyncAction
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.glass.GlassScaffold as Scaffold

@Composable
fun TabbedScreen(
    titleRes: StringResource,
    tabs: List<TabContent>,
    state: PagerState = rememberPagerState { tabs.size },
    searchQuery: String? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    glass: Boolean = false, // Fork: only Browse opts in
    showSyncProgress: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fork: scoped glass panels, original Material rendering when disabled.
    Scaffold(
        enabled = glass,
        topBar = {
            val tab = tabs[state.currentPage]
            val searchEnabled = tab.searchEnabled

            Column {
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(titleRes)) },
                    searchEnabled = searchEnabled,
                    searchQuery = if (searchEnabled) searchQuery else null,
                    onChangeSearchQuery = onChangeSearchQuery,
                    actions = { AppBarActions(tab.actions) },
                )
                if (showSyncProgress) LibrarySyncProgressBar(rememberLibrarySyncAction())
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        val isGlass = LocalGlassPageContent.current
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        var tabsHeight by remember { mutableStateOf(0.dp) }
        val tabRow: @Composable () -> Unit = {
            PrimaryTabRow(
                selectedTabIndex = state.currentPage,
                modifier = Modifier.zIndex(1f),
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = state.currentPage == index,
                        onClick = { scope.launch { state.animateScrollToPage(index) } },
                        text = { TabText(text = stringResource(tab.titleRes), badgeCount = tab.badgeNumber) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = if (isGlass) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.padding(
                        top = contentPadding.calculateTopPadding(),
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    )
                },
            ) {
                if (!isGlass) tabRow()

                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    tabs[page].content(
                        PaddingValues(
                            top = if (isGlass) contentPadding.calculateTopPadding() + tabsHeight else 0.dp,
                            start = if (isGlass) {
                                contentPadding.calculateStartPadding(layoutDirection)
                            } else {
                                0.dp
                            },
                            end = if (isGlass) contentPadding.calculateEndPadding(layoutDirection) else 0.dp,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                        snackbarHostState,
                    )
                }
            }
            if (isGlass) {
                Box(
                    Modifier.padding(
                        top = contentPadding.calculateTopPadding(),
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ).onSizeChanged { tabsHeight = with(density) { it.height.toDp() } },
                ) { tabRow() }
            }
        }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val actions: List<AppBar.AppBarAction> = listOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
)
