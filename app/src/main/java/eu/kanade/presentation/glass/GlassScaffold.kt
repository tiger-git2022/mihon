package eu.kanade.presentation.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import mihon.presentation.glass.LocalGlassBackdrop
import mihon.presentation.glass.glassContent
import mihon.presentation.glass.glassPanel
import mihon.presentation.glass.rememberGlassBackdrop
import tachiyomi.presentation.core.components.material.Scaffold

/** Navigation overlays reserve scroll padding, while allowing lists to draw behind the glass. */
internal val LocalGlassNavigationPadding = compositionLocalOf { PaddingValues(0.dp) }
internal val LocalGlassPageContent = compositionLocalOf { false }

/** Fork: page content is the sole backdrop source; toolbar and history overlays are separate siblings. */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBarScrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    enabled: Boolean = true,
    overlay: @Composable (PaddingValues) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = if (enabled) rememberGlassBackdrop(MaterialTheme.colorScheme.background) else null
    val navigationPadding = LocalGlassNavigationPadding.current
    val direction = LocalLayoutDirection.current
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Scaffold(
            modifier = modifier,
            topBarScrollBehavior = topBarScrollBehavior,
            topBar = { behavior ->
                Box(
                    Modifier.fillMaxWidth().padding(
                        start = navigationPadding.calculateStartPadding(direction),
                    ).glassPanel(
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    ),
                ) { topBar(behavior) }
            },
            bottomBar = bottomBar,
            // Fork: notifications must stay above the bar and beside the rail, like the page content.
            snackbarHost = { Box(Modifier.padding(navigationPadding)) { snackbarHost() } },
        ) { padding ->
            val combinedPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + navigationPadding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction) + navigationPadding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding() + navigationPadding.calculateBottomPadding(),
            )
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalGlassBackdrop provides null,
                    LocalGlassPageContent provides (backdrop != null),
                ) {
                    Box(Modifier.fillMaxSize().glassContent(backdrop)) { content(combinedPadding) }
                }
                overlay(combinedPadding)
            }
        }
    }
}
