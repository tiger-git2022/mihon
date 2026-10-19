package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.glass.LocalGlassPageContent
import eu.kanade.presentation.library.components.LibrarySyncProgressBar
import eu.kanade.tachiyomi.ui.library.rememberLibrarySyncAction
import kotlinx.coroutines.launch
import mihon.presentation.glass.LocalGlassBackdrop
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

    val tabRow: @Composable (inGlass: Boolean) -> Unit = { inGlass ->
        PrimaryTabRow(
            selectedTabIndex = state.currentPage,
            modifier = Modifier.zIndex(1f),
            // Fork: inside the glass header the tabs are part of the glass, without a band of their own.
            containerColor = if (inGlass) Color.Transparent else TabRowDefaults.primaryContainerColor,
            divider = { if (!inGlass) HorizontalDivider() },
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
                // Fork: sync progress right below the toolbar, above the tabs, so it never meets their indicator
                if (showSyncProgress) LibrarySyncProgressBar(rememberLibrarySyncAction())
                // Fork: with glass, the tabs belong to the header; the lists scroll below both.
                if (LocalGlassBackdrop.current != null) tabRow(true)
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        val isGlass = LocalGlassPageContent.current
        val layoutDirection = LocalLayoutDirection.current
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
            if (!isGlass) tabRow(false)

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content(
                    PaddingValues(
                        top = if (isGlass) contentPadding.calculateTopPadding() else 0.dp,
                        start = if (isGlass) contentPadding.calculateStartPadding(layoutDirection) else 0.dp,
                        end = if (isGlass) contentPadding.calculateEndPadding(layoutDirection) else 0.dp,
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    snackbarHostState,
                )
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
