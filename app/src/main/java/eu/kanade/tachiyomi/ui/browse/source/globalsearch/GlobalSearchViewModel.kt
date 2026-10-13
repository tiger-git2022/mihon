package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.sync.LibrarySyncPreferences
import eu.kanade.tachiyomi.data.sync.SearchHistoryEntry
import eu.kanade.tachiyomi.data.sync.SearchHistoryMerger
import eu.kanade.tachiyomi.data.sync.SyncDevice
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock

@AssistedInject
class GlobalSearchViewModel(
    @Assisted initialQuery: String,
    @Assisted initialExtensionFilter: String?,
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    preferences: SourcePreferences,
    private val searchHistoryPreferences: GlobalSearchHistoryPreferences,
    private val librarySyncPreferences: LibrarySyncPreferences,
) : SearchViewModel(
    initialState = State(
        searchQuery = initialQuery,
        searchHistory = searchHistoryPreferences.entries.get()
            .toSearchHistoryItems(librarySyncPreferences.currentDevice()),
        sourceFilter = if (sourcePreferences.pinnedSources.get().isEmpty()) {
            SourceFilter.All
        } else {
            SourceFilter.PinnedOnly
        },
    ),
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
    preferences = preferences,
) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(initialQuery: String, initialExtensionFilter: String?): GlobalSearchViewModel
    }

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }
    }

    override suspend fun getEnabledSources(): List<Source> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }

    // region Fork: global search history
    init {
        // Also picks up entries synced from other devices
        searchHistoryPreferences.entries.changes()
            .onEach { entries ->
                val items = entries.toSearchHistoryItems(librarySyncPreferences.currentDevice())
                updateState { it.copy(searchHistory = items) }
            }
            .launchIn(viewModelScope)
    }

    override fun search() {
        val query = state.value.searchQuery?.trim().orEmpty()
        if (query.isEmpty()) return

        updateSearchQuery(query)
        updateSearchHistory { entries, device, now -> entries.withSearchQuery(query, device, now) }
        super.search()
    }

    fun removeSearchHistory(query: String) {
        updateSearchHistory { entries, device, now -> entries.withoutSearchQuery(query, device, now) }
    }

    fun clearSearchHistory() {
        updateSearchHistory { entries, device, now -> entries.withoutAnySearchQuery(device, now) }
    }

    private fun updateSearchHistory(
        transform: (List<SearchHistoryEntry>, SyncDevice, Long) -> List<SearchHistoryEntry>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val entries = SearchHistoryMerger.prune(
            transform(searchHistoryPreferences.entries.get(), librarySyncPreferences.currentDevice(), now),
            now,
        )
        searchHistoryPreferences.entries.set(entries)
    }
    // endregion
}
