package eu.kanade.presentation.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import mihon.presentation.glass.GlassEdge
import mihon.presentation.glass.LocalLiquidGlassEnabled
import mihon.presentation.glass.glassBar
import mihon.presentation.glass.glassContent
import mihon.presentation.glass.rememberGlassBackdrop

/** Fork: glass navigation floats over the home pages; the disabled branch is the upstream scaffold. */
@Composable
fun GlassNavigationScaffold(
    navigationSuiteType: NavigationSuiteType,
    state: NavigationSuiteScaffoldState,
    navigationSuiteColors: NavigationSuiteColors,
    navigationItemVerticalArrangement: Arrangement.Vertical,
    navigationItems: @Composable () -> Unit,
    contentBehindNavigation: Boolean,
    content: @Composable () -> Unit,
) {
    if (!LocalLiquidGlassEnabled.current) {
        NavigationSuiteScaffold(
            navigationSuiteType = navigationSuiteType,
            state = state,
            navigationSuiteColors = navigationSuiteColors,
            navigationItemVerticalArrangement = navigationItemVerticalArrangement,
            navigationItems = navigationItems,
            content = content,
        )
        return
    }

    val rail = navigationSuiteType == NavigationSuiteType.NavigationRail
    val visible = state.targetValue == NavigationSuiteScaffoldValue.Visible
    val backdrop = rememberGlassBackdrop(MaterialTheme.colorScheme.background)
    var size by remember(rail) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val padding = with(density) {
        when {
            !visible -> PaddingValues(0.dp)
            rail -> PaddingValues(start = size.width.toDp())
            else -> PaddingValues(bottom = size.height.toDp())
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassContent(backdrop)) {
            // Other home pages keep the original reserved space. Opted-in lists reserve scroll padding instead.
            Box(
                Modifier.fillMaxSize()
                    .then(if (contentBehindNavigation) Modifier else Modifier.padding(padding))
                    .consumeWindowInsets(padding),
            ) {
                CompositionLocalProvider(
                    LocalGlassNavigationPadding provides if (contentBehindNavigation) padding else PaddingValues(0.dp),
                ) { content() }
            }
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(if (rail) Alignment.CenterStart else Alignment.BottomCenter),
        ) {
            // Fork: flush with the screen edge, only the side facing the page is outlined.
            Box(
                Modifier.onSizeChanged { size = it }
                    .then(if (rail) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()),
            ) {
                NavigationSuite(
                    modifier = Modifier.glassBar(if (rail) GlassEdge.End else GlassEdge.Top, backdrop = backdrop),
                    navigationSuiteType = navigationSuiteType,
                    colors = NavigationSuiteDefaults.colors(
                        navigationBarContainerColor = Color.Transparent,
                        navigationRailContainerColor = Color.Transparent,
                    ),
                    verticalArrangement = navigationItemVerticalArrangement,
                    content = navigationItems,
                )
            }
        }
    }
}
