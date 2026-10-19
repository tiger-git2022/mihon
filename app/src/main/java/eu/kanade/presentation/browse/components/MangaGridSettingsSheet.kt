package eu.kanade.presentation.browse.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.library.components.MangaGridStyle
import eu.kanade.presentation.library.components.rememberGridPreferences
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Fork: covers and titles of the source grid ([browse]) or of the global search cards ([search]), in a sheet that
 * leaves the page above it in sight, so each change shows at once.
 */
@Composable
fun MangaGridSettingsSheet(
    onDismissRequest: () -> Unit,
    browse: Boolean,
    search: Boolean,
) {
    val preferences = rememberGridPreferences()
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.grid_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (browse) {
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                ChoiceRow(
                    label = if (landscape) MR.strings.grid_columns_landscape else MR.strings.grid_columns_portrait,
                    preference = with(preferences) { if (landscape) browseLandscapeColumns else browsePortraitColumns },
                    choices = MangaGridStyle.ColumnChoices,
                ) { if (it == 0) stringResource(MR.strings.grid_columns_library) else it.toString() }
                ChoiceRow(
                    label = MR.strings.grid_cover_height,
                    preference = preferences.browseCoverHeight,
                    choices = MangaGridStyle.CoverHeightChoices,
                ) { "$it%" }
                TitleLinesRow(preferences.browseTitleLines)
                ChoiceRow(
                    label = MR.strings.browse_list_style,
                    preference = preferences.browseLargeList,
                    choices = listOf(true, false),
                ) {
                    stringResource(if (it) MR.strings.browse_list_large else MR.strings.browse_list_small)
                }
                Text(
                    text = stringResource(MR.strings.grid_compact_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (search) {
                val wide by preferences.searchWideCards.collectAsState()
                ChoiceRow(
                    label = MR.strings.search_card_style,
                    preference = preferences.searchWideCards,
                    choices = listOf(true, false),
                ) {
                    stringResource(if (it) MR.strings.search_card_wide else MR.strings.search_card_tall)
                }
                ChoiceRow(
                    label = MR.strings.search_card_width,
                    preference = preferences.searchCardWidth,
                    choices = MangaGridStyle.CardWidthChoices,
                ) {
                    stringResource(
                        when (it) {
                            0 -> MR.strings.search_card_width_narrow
                            1 -> MR.strings.search_card_width_medium
                            else -> MR.strings.search_card_width_wide
                        },
                    )
                }
                // Wide cards show as many lines as fit beside the cover
                if (!wide) TitleLinesRow(preferences.searchTitleLines)
            }
        }
    }
}

/** Title lines, shared with the library display settings. */
@Composable
fun TitleLinesRow(preference: Preference<Int>) {
    ChoiceRow(
        label = MR.strings.grid_title_lines,
        preference = preference,
        choices = MangaGridStyle.TitleLineChoices,
    ) {
        if (it == MangaGridStyle.ALL_LINES) stringResource(MR.strings.grid_title_lines_all) else it.toString()
    }
}

@Composable
private fun <T> ChoiceRow(
    label: StringResource,
    preference: Preference<T>,
    choices: List<T>,
    choiceLabel: @Composable (T) -> String,
) {
    val value by preference.collectAsState()
    SettingsChipRow(label) {
        choices.forEach { choice ->
            FilterChip(
                selected = value == choice,
                onClick = { preference.set(choice) },
                label = { Text(choiceLabel(choice)) },
            )
        }
    }
}
