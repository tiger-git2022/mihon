package eu.kanade.tachiyomi.ui.library

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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
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

    LaunchedEffect(librarySyncService) {
        runCatching { librarySyncService.refreshAccount() }
            .onFailure { Log.e("LibrarySync", "Could not refresh the OneDrive account", it) }
    }

    return LibrarySyncActionState(
        isSyncing = isSyncing,
        onClick = onClick@{
            if (accountName.isBlank()) {
                navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage))
                return@onClick
            }
            if (isSyncing) return@onClick

            isSyncing = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { librarySyncService.sync() }
                }.onSuccess { result ->
                    context.toast(
                        context.stringResource(
                            MR.strings.library_sync_success,
                            result.added,
                            result.removed,
                            result.total,
                        ),
                    )
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
