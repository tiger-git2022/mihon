package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.Log
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
import eu.kanade.tachiyomi.data.sync.LibrarySyncProgress
import eu.kanade.tachiyomi.data.sync.LibrarySyncResult
import eu.kanade.tachiyomi.data.sync.SettingsBackupStage
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * State of the OneDrive library sync toolbar action (fork-only feature).
 *
 * Lives in its own file so that [LibraryTab] only needs a few lines of changes,
 * which keeps merges with upstream simple.
 */
@Stable
class LibrarySyncActionState(
    val isSyncing: Boolean,
    val onClick: () -> Unit,
    /** Current sync step and progress, e.g. "Syncing (1/4): Library 37/120 (30%)". */
    val progressText: String?,
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

    LaunchedEffect(librarySyncService) {
        runCatching { librarySyncService.refreshAccount() }
            .onFailure { Log.e("LibrarySync", "Could not refresh the OneDrive account", it) }
    }

    return LibrarySyncActionState(
        isSyncing = isSyncing || progress != null,
        progressText = progressText,
        onClick = onClick@{
            if (accountName.isBlank()) {
                navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage))
                return@onClick
            }
            if (isSyncing || progress != null) return@onClick

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
        },
    )
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
        LibrarySyncProgress.Step.SETTINGS_BACKUP -> when (progress.stage) {
            SettingsBackupStage.UPLOAD -> stringResource(MR.strings.library_sync_step_settings_upload)
            else -> stringResource(MR.strings.library_sync_step_settings_create)
        }
    }
    val detail = progress.percent
        ?.let { stringResource(MR.strings.library_sync_progress_count, stepName, progress.done, progress.total, it) }
        ?: stepName
    return stringResource(
        MR.strings.library_sync_progress,
        progress.step.number,
        LibrarySyncProgress.STEP_COUNT,
        detail,
    )
}
