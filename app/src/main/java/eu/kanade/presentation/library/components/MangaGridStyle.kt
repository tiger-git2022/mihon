package eu.kanade.presentation.library.components

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import mihon.app.di.appGraph
import tachiyomi.presentation.core.util.collectAsState

/**
 * Fork: how the manga grids draw covers and titles, set per screen (library, browsing a source, global search) in
 * the covers and titles settings. The default keeps upstream's look.
 */
@Immutable
data class MangaGridStyle(
    /** Title lines, [ALL_LINES] for the whole title, null for what the item asks for. */
    val titleLines: Int? = null,
    /** Cover height as a fraction of the usual 2:3 cover; shorter covers are cropped. */
    val coverHeight: Float = 1f,
    /** List mode: large rows with the cover beside the title, see [MangaWideItem]. */
    val largeList: Boolean = false,
    /** Global search: wide cards with the cover beside the title. */
    val wideCards: Boolean = false,
    /** Global search: card width step, 0 narrow, 1 medium, 2 wide. */
    val cardWidth: Int = 0,
) {
    fun maxLines(default: Int): Int = when (titleLines) {
        null -> default
        ALL_LINES -> Int.MAX_VALUE
        else -> titleLines
    }

    val coverRatio: Float get() = MangaCover.Book.ratio / coverHeight

    /** Width of a usual global search card. */
    val narrowCardWidth: Dp get() = NarrowCardWidths[cardWidth.coerceIn(NarrowCardWidths.indices)]

    /** Width of a wide global search card. */
    val wideCardWidth: Dp get() = WideCardWidths[cardWidth.coerceIn(WideCardWidths.indices)]

    companion object {
        /** Stored as 0: the whole title. */
        const val ALL_LINES = 0

        val TitleLineChoices = listOf(2, 3, 4, 6, ALL_LINES)
        val ColumnChoices = listOf(0, 2, 3, 4, 5, 6)
        val CoverHeightChoices = listOf(100, 85, 70, 55)
        val CardWidthChoices = listOf(0, 1, 2)

        private val NarrowCardWidths = listOf(96.dp, 112.dp, 128.dp)
        private val WideCardWidths = listOf(240.dp, 280.dp, 320.dp)
    }
}

val LocalMangaGridStyle = staticCompositionLocalOf { MangaGridStyle() }

@Composable
fun rememberLibraryGridStyle(): MangaGridStyle {
    val preferences = rememberGridPreferences()
    val lines by preferences.libraryTitleLines.collectAsState()
    return remember(lines) { MangaGridStyle(titleLines = lines) }
}

@Composable
fun rememberBrowseGridStyle(): MangaGridStyle {
    val preferences = rememberGridPreferences()
    val lines by preferences.browseTitleLines.collectAsState()
    val height by preferences.browseCoverHeight.collectAsState()
    val largeList by preferences.browseLargeList.collectAsState()
    return remember(lines, height, largeList) {
        MangaGridStyle(titleLines = lines, coverHeight = height.coerceIn(30, 100) / 100f, largeList = largeList)
    }
}

@Composable
fun rememberSearchGridStyle(): MangaGridStyle {
    val preferences = rememberGridPreferences()
    val lines by preferences.searchTitleLines.collectAsState()
    val wide by preferences.searchWideCards.collectAsState()
    val width by preferences.searchCardWidth.collectAsState()
    return remember(lines, wide, width) { MangaGridStyle(titleLines = lines, wideCards = wide, cardWidth = width) }
}

/** The columns set for browsing a source in this orientation, else [libraryColumns]. */
@Composable
fun browseGridColumns(libraryColumns: GridCells): GridCells {
    val preferences = rememberGridPreferences()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns by (if (landscape) preferences.browseLandscapeColumns else preferences.browsePortraitColumns)
        .collectAsState()
    return if (columns > 0) GridCells.Fixed(columns) else libraryColumns
}

@Composable
internal fun rememberGridPreferences() = LocalContext.current.let { context ->
    remember { context.appGraph.glassPreferences }
}
