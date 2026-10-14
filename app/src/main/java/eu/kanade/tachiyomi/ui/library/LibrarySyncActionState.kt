package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.data.sync.EhFavoritesSyncStage
import eu.kanade.tachiyomi.data.sync.LibrarySyncProgress
import eu.kanade.tachiyomi.data.sync.LibrarySyncResult
import eu.kanade.tachiyomi.data.sync.SettingsBackupStage
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Sync
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.util.Locale

/** What the sync action shows, the first that applies. */
enum class LibrarySyncState { RUNNING, PENDING, FAILED, SUCCEEDED, NOT_SYNCED }

/**
 * State of the OneDrive library sync toolbar action (fork-only feature): not synced yet, changes waiting for a sync,
 * syncing (a ring filling up with the progress), synced, or failed. Shown on the library, the sources and the global
 * search.
 *
 * Lives in its own file so that the upstream screens only need a few lines of changes,
 * which keeps merges with upstream simple.
 */
@Stable
class LibrarySyncActionState(
    val isSignedIn: Boolean,
    val state: LibrarySyncState,
    val isSyncing: Boolean,
    val onClick: () -> Unit,
    /** Current sync step and progress, e.g. "Syncing 6% (1/5): Library 37/120 (30%)". */
    val progressText: String?,
    /** Progress of the whole sync from 0 to 1, null when no sync runs. */
    val progress: Float?,
    /** The toolbar action showing [state]. */
    val action: AppBar.Action,
)

@Composable
fun rememberLibrarySyncAction(): LibrarySyncActionState {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val librarySyncPreferences = remember { context.appGraph.librarySyncPreferences }
    val librarySyncService = remember { context.appGraph.librarySyncService }
    val accountName by librarySyncPreferences.accountName.collectAsState()
    var isSyncing by remember { mutableStateOf(false) }
    val progress by librarySyncService.progress.collectAsStateWithLifecycle()
    val progressText = progress?.let { librarySyncProgressText(it) }
    val changesPending by librarySyncService.changesPending.collectAsStateWithLifecycle()
    val lastSyncTime by librarySyncPreferences.lastSyncTime.collectAsState()
    val lastSyncFailed by librarySyncPreferences.lastSyncFailed.collectAsState()

    LaunchedEffect(librarySyncService) {
        runCatching { librarySyncService.refreshAccount() }
            .onFailure { Log.e("LibrarySync", "Could not refresh the OneDrive account", it) }
    }

    val syncing = isSyncing || progress != null
    val state = when {
        syncing -> LibrarySyncState.RUNNING
        changesPending -> LibrarySyncState.PENDING
        lastSyncFailed && lastSyncTime > 0 -> LibrarySyncState.FAILED
        lastSyncTime > 0 -> LibrarySyncState.SUCCEEDED
        else -> LibrarySyncState.NOT_SYNCED
    }
    val isSignedIn = accountName.isNotBlank()
    val onClick: () -> Unit = onClick@{
        if (!isSignedIn) {
            navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage))
            return@onClick
        }
        if (isSyncing || progress != null) {
            // The step and how far it got
            context.toast(progressText ?: context.stringResource(MR.strings.library_sync_syncing))
            return@onClick
        }

        isSyncing = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { librarySyncService.sync() }
            }.onSuccess { result ->
                context.toast(context.librarySyncMessage(result))
            }.onFailure { error ->
                Log.e("LibrarySync", "Library sync failed", error)
                context.toast(
                    context.stringResource(
                        MR.strings.library_sync_failed,
                        error.message.orEmpty(),
                    ),
                )
            }
            isSyncing = false
        }
    }
    val fraction = progress?.fraction
    val title = when {
        !isSignedIn -> stringResource(MR.strings.library_sync_now)
        state == LibrarySyncState.RUNNING -> progressText ?: stringResource(MR.strings.library_sync_syncing)
        state == LibrarySyncState.PENDING -> stringResource(MR.strings.library_sync_status_pending)
        state == LibrarySyncState.FAILED -> stringResource(MR.strings.library_sync_status_failed)
        state == LibrarySyncState.SUCCEEDED -> stringResource(MR.strings.library_sync_status_succeeded)
        else -> stringResource(MR.strings.library_sync_status_never)
    }
    val action = AppBar.Action(
        title = title,
        icon = when (state) {
            LibrarySyncState.PENDING -> MaterialSymbols.Rounded.Schedule
            LibrarySyncState.FAILED -> MaterialSymbols.Rounded.Error
            LibrarySyncState.SUCCEEDED -> MaterialSymbols.Rounded.DoneAll
            else -> MaterialSymbols.Rounded.Sync
        },
        iconTint = MaterialTheme.colorScheme.error.takeIf { state == LibrarySyncState.FAILED },
        onClick = onClick,
        isLoading = syncing,
        progress = fraction,
    )

    return LibrarySyncActionState(
        isSignedIn = isSignedIn,
        state = state,
        isSyncing = syncing,
        onClick = onClick,
        progressText = progressText,
        progress = fraction,
        action = action,
    )
}

/** The sync action for a toolbar's actions row, nothing without a OneDrive account. */
@Composable
fun LibrarySyncActions() {
    val sync = rememberLibrarySyncAction()
    if (sync.isSignedIn) AppBarActions(listOf(sync.action))
}

internal fun Context.librarySyncMessage(result: LibrarySyncResult): String {
    val message = stringResource(MR.strings.library_sync_success, result.added, result.removed, result.total)
    return result.warning?.let { stringResource(MR.strings.library_sync_partial_failure, message, it) } ?: message
}

@Composable
internal fun librarySyncProgressText(progress: LibrarySyncProgress): String {
    val stepName = when (progress.step) {
        LibrarySyncProgress.Step.LIBRARY -> stringResource(MR.strings.library_sync_step_library)
        LibrarySyncProgress.Step.SEARCH_HISTORY -> stringResource(MR.strings.library_sync_step_search_history)
        LibrarySyncProgress.Step.MARKS -> stringResource(MR.strings.library_sync_step_marks)
        LibrarySyncProgress.Step.EHVIEWER_FAVORITES -> when (progress.favoritesStage) {
            EhFavoritesSyncStage.DOWNLOAD -> stringResource(MR.strings.library_sync_step_ehviewer_favorites_download)
            EhFavoritesSyncStage.READ -> stringResource(MR.strings.library_sync_step_ehviewer_favorites_read)
            null -> stringResource(MR.strings.library_sync_step_ehviewer_favorites)
        }
        LibrarySyncProgress.Step.SETTINGS_BACKUP -> when (progress.stage) {
            SettingsBackupStage.UPLOAD -> stringResource(MR.strings.library_sync_step_settings_upload)
            else -> stringResource(MR.strings.library_sync_step_settings_create)
        }
    }
    val detail = when {
        // Bytes as megabytes: "Downloading EhViewer favorites 1.2/3.4 MB (35%)"
        progress.favoritesStage == EhFavoritesSyncStage.DOWNLOAD ->
            progress.percent
                ?.let {
                    stringResource(
                        MR.strings.library_sync_progress_size,
                        stepName,
                        mb(progress.done),
                        mb(progress.total),
                        it,
                    )
                }
                ?: stringResource(MR.strings.library_sync_progress_size_unknown, stepName, mb(progress.done))
        else ->
            progress.percent
                ?.let {
                    stringResource(MR.strings.library_sync_progress_count, stepName, progress.done, progress.total, it)
                }
                ?: stepName
    }
    return stringResource(
        MR.strings.library_sync_progress,
        (progress.fraction * 100).toInt(),
        progress.step.number,
        LibrarySyncProgress.STEP_COUNT,
        detail,
    )
}

private fun mb(bytes: Int): String = String.format(Locale.ROOT, "%.1f", bytes / (1024f * 1024f))
