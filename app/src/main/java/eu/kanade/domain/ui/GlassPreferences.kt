package eu.kanade.domain.ui

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Fork-only appearance preferences (glass, page preview columns, covers and titles in the grids), kept out of
 * upstream UiPreferences.
 */
@Inject
@SingleIn(AppScope::class)
class GlassPreferences(preferenceStore: PreferenceStore) {
    val liquidGlass = preferenceStore.getBoolean("tiger_liquid_glass", false)

    // Blur in dp and opacity in percent of the glass, see GlassTuning; the defaults are EhViewer's numbers.
    val glassBarBlur = preferenceStore.getInt("tiger_glass_bar_blur", 4)
    val glassBarOpacity = preferenceStore.getInt("tiger_glass_bar_opacity", 20)
    val glassOverlayBlur = preferenceStore.getInt("tiger_glass_overlay_blur", 10)
    val glassOverlayOpacity = preferenceStore.getInt("tiger_glass_overlay_opacity", 40)

    /** Columns of the website page previews, on the details and in the full grid. */
    val websitePreviewColumns = preferenceStore.getInt("tiger_website_preview_columns", 4)

    // Covers and titles, see MangaGridStyle. A line count of 0 shows the whole title.

    /** Title lines below or over the covers in the library. */
    val libraryTitleLines = preferenceStore.getInt("tiger_library_title_lines", 0)

    /** Columns when browsing a source, 0 follows the library. */
    val browsePortraitColumns = preferenceStore.getInt("tiger_browse_columns_portrait", 0)
    val browseLandscapeColumns = preferenceStore.getInt("tiger_browse_columns_landscape", 0)

    /** Cover height when browsing a source, in percent of the usual 2:3 cover; shorter covers are cropped. */
    val browseCoverHeight = preferenceStore.getInt("tiger_browse_cover_height", 100)

    /** List mode when browsing a source: true for large rows like EhViewer's, false for upstream's small rows. */
    val browseLargeList = preferenceStore.getBoolean("tiger_browse_large_list", true)

    /** Title lines when browsing a source. */
    val browseTitleLines = preferenceStore.getInt("tiger_browse_title_lines", 0)

    /** Global search results: true for wide cards (small cover, title beside it), false for the usual cards. */
    val searchWideCards = preferenceStore.getBoolean("tiger_search_wide_cards", true)

    /** Global search card width: 0 narrow, 1 medium, 2 wide. */
    val searchCardWidth = preferenceStore.getInt("tiger_search_card_width", 0)

    /** Title lines on the usual global search cards. */
    val searchTitleLines = preferenceStore.getInt("tiger_search_title_lines", 3)
}
