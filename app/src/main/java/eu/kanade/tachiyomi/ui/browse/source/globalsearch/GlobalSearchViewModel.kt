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
import eu.kanade.tachiyomi.data.sync.EhFavoritesStore
import eu.kanade.tachiyomi.data.sync.KanaRomaji
import eu.kanade.tachiyomi.data.sync.LibraryAutoSync
import eu.kanade.tachiyomi.data.sync.LibrarySyncPreferences
import eu.kanade.tachiyomi.data.sync.LibrarySyncService
import eu.kanade.tachiyomi.data.sync.SearchHistoryEntry
import eu.kanade.tachiyomi.data.sync.SearchHistoryMerger
import eu.kanade.tachiyomi.data.sync.SyncDevice
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
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
    private val libraryAutoSync: LibraryAutoSync,
    private val ehFavoritesStore: EhFavoritesStore,
    private val librarySyncService: LibrarySyncService,
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

    // The other spelling of an artist, circle or title the EhViewer favorites list both ways, else the romanized
    // kana of a name nobody favorited
    override suspend fun alternativeQuery(query: String): String? {
        val aliases = if (librarySyncPreferences.showEhViewerFavorites.get()) {
            // Read from disk after a cold start, downloaded by a running sync: a source answering first would
            // otherwise find no aliases
            ehFavoritesStore.awaitLoaded()
            // A sync downloads the favorites in its last step (first sign-in, account switch): an empty list may be
            // about to arrive
            if (ehFavoritesStore.index.value.file.galleries.isEmpty() && librarySyncService.progress.value != null) {
                withTimeoutOrNull(SYNC_WAIT_MILLIS) { librarySyncService.progress.first { it == null } }
            }
            ehFavoritesStore.index.value.aliases
        } else {
            null
        }
        return aliases?.alternative(query) ?: KanaRomaji.romanize(query)
    }

    fun removeSearchHistory(query: String) {
        updateSearchHistory { entries, device, now -> entries.withoutSearchQuery(query, device, now) }
    }

    fun clearSearchHistory() {
        val now = Clock.System.now().toEpochMilliseconds()
        searchHistoryPreferences.update { _, clearedAt -> SearchHistoryState(emptyList(), maxOf(clearedAt, now)) }
        libraryAutoSync.notifyChanged()
    }

    private fun updateSearchHistory(
        transform: (List<SearchHistoryEntry>, SyncDevice, Long) -> List<SearchHistoryEntry>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val device = librarySyncPreferences.currentDevice()
        searchHistoryPreferences.update { entries, clearedAt ->
            SearchHistoryState(SearchHistoryMerger.prune(transform(entries, device, now), now), clearedAt)
        }
        libraryAutoSync.notifyChanged()
    }
    // endregion

    private companion object {
        const val SYNC_WAIT_MILLIS = 60_000L
    }
}
