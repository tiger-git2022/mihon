package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.sync.LibrarySyncPreferences
import eu.kanade.tachiyomi.data.sync.SettingsBackup
import eu.kanade.tachiyomi.ui.library.librarySyncMessage
import eu.kanade.tachiyomi.ui.library.librarySyncProgressText
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.DateFormat
import java.util.Date

/**
 * OneDrive library sync settings group (fork-only feature), shown in [SettingsDataScreen].
 *
 * Lives in its own file so that [SettingsDataScreen] only needs a one-line change,
 * which keeps merges with upstream simple.
 */
@Composable
internal fun getLibrarySyncGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.appGraph.librarySyncPreferences }
    val service = remember { context.appGraph.librarySyncService }
    val redirectUri = remember { runCatching { service.redirectUri }.getOrDefault("-") }
    val accountName by preferences.accountName.collectAsState()
    val autoSync by preferences.autoSync.collectAsState()
    val activity = remember(context) { context.findActivity() }
    var isBusy by remember { mutableStateOf(false) }
    // Also shows a sync started from the library toolbar
    val syncProgress by service.progress.collectAsStateWithLifecycle()

    LaunchedEffect(service) {
        runCatching { service.refreshAccount() }
            .onFailure { logcat(LogPriority.ERROR, it) }
    }

    val status = if (accountName.isBlank()) {
        stringResource(MR.strings.library_sync_not_signed_in)
    } else {
        stringResource(MR.strings.library_sync_signed_in_as, accountName)
    }

    var restoreDialog by remember { mutableStateOf<RestoreDialog?>(null) }
    SettingsRestoreDialog(
        state = restoreDialog,
        onStateChange = { restoreDialog = it },
        loadBackups = { withContext(Dispatchers.IO) { service.listSettingsBackups() } },
        onRestore = { backup ->
            restoreDialog = null
            scope.launch {
                runCatching { service.restoreSettings(backup) }
                    .onSuccess { context.toast(MR.strings.library_sync_restore_started) }
                    .onFailure { error ->
                        logcat(LogPriority.ERROR, error)
                        context.toast(context.stringResource(MR.strings.library_sync_failed, error.message.orEmpty()))
                    }
            }
        },
    )

    // TextPreference has no "enabled" flag, so a null click handler disables the item.
    fun action(block: suspend () -> Unit): (() -> Unit)? {
        if (isBusy) return null
        return {
            isBusy = true
            scope.launch {
                block()
                isBusy = false
            }
        }
    }

    return Preference.PreferenceGroup(
        title = stringResource(MR.strings.library_sync),
        preferenceItems = buildList {
            add(Preference.PreferenceItem.InfoPreference(status))
            if (accountName.isBlank()) {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = if (isBusy) {
                            stringResource(MR.strings.library_sync_signing_in)
                        } else {
                            stringResource(MR.strings.library_sync_sign_in)
                        },
                        onClick = activity?.let { activity ->
                            action {
                                runCatching { service.signIn(activity) }
                                    .onSuccess {
                                        context.toast(MR.strings.library_sync_sign_in_success)
                                    }
                                    .onFailure { error ->
                                        logcat(LogPriority.ERROR, error)
                                        context.toast(
                                            context.stringResource(
                                                MR.strings.library_sync_sign_in_failed,
                                                error.message.orEmpty(),
                                            ),
                                        )
                                    }
                            }
                        },
                    ),
                )
            } else {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = when {
                            syncProgress != null -> librarySyncProgressText(syncProgress!!)
                            isBusy -> stringResource(MR.strings.library_sync_syncing)
                            else -> stringResource(MR.strings.library_sync_now)
                        },
                        widget = if (syncProgress != null) {
                            { LibrarySyncProgressIndicator(syncProgress?.percent) }
                        } else {
                            null
                        },
                        onClick = if (syncProgress != null) {
                            null
                        } else {
                            action {
                                runCatching {
                                    withContext(Dispatchers.IO) { service.sync() }
                                }.onSuccess { result ->
                                    context.toast(context.librarySyncMessage(result))
                                }.onFailure { error ->
                                    logcat(LogPriority.ERROR, error)
                                    context.toast(
                                        context.stringResource(
                                            MR.strings.library_sync_failed,
                                            error.message.orEmpty(),
                                        ),
                                    )
                                }
                            }
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.library_sync_restore_settings),
                        subtitle = stringResource(MR.strings.library_sync_restore_settings_summary),
                        onClick = if (isBusy) null else ({ restoreDialog = RestoreDialog.Loading }),
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.library_sync_sign_out),
                        onClick = action {
                            runCatching { service.signOut() }
                                .onSuccess {
                                    context.toast(MR.strings.library_sync_sign_out_success)
                                }
                                .onFailure { error ->
                                    logcat(LogPriority.ERROR, error)
                                    context.toast(
                                        context.stringResource(
                                            MR.strings.library_sync_sign_out_failed,
                                            error.message.orEmpty(),
                                        ),
                                    )
                                }
                        },
                    ),
                )
            }
            addAll(autoSyncItems(preferences, autoSync))
            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.showEhViewerFavorites,
                    title = stringResource(MR.strings.library_sync_ehviewer_favorites),
                    subtitle = stringResource(MR.strings.library_sync_ehviewer_favorites_summary),
                ),
            )
            add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = preferences.deviceName,
                    title = stringResource(MR.strings.library_sync_device_name),
                ),
            )
            add(Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.library_sync_info)))
            if (accountName.isBlank()) {
                add(
                    Preference.PreferenceItem.InfoPreference(
                        stringResource(MR.strings.library_sync_redirect_uri, redirectUri),
                    ),
                )
            }
        },
    )
}

/** Automatic sync options, see [eu.kanade.tachiyomi.data.sync.LibraryAutoSync]. */
@Composable
private fun autoSyncItems(
    preferences: LibrarySyncPreferences,
    autoSync: Boolean,
): List<Preference.PreferenceItem<out Any, out Any>> {
    val intervals = LibrarySyncPreferences.AUTO_SYNC_INTERVALS_MINUTES.associateWith { minutes ->
        if (minutes < 60) {
            stringResource(MR.strings.library_sync_auto_interval_minutes, minutes)
        } else {
            stringResource(MR.strings.library_sync_auto_interval_hours, minutes / 60)
        }
    }
    return listOf(
        Preference.PreferenceItem.SwitchPreference(
            preference = preferences.autoSync,
            title = stringResource(MR.strings.library_sync_auto),
            subtitle = stringResource(MR.strings.library_sync_auto_summary),
        ),
        Preference.PreferenceItem.ListPreference(
            preference = preferences.autoSyncIntervalMinutes,
            entries = intervals,
            title = stringResource(MR.strings.library_sync_auto_interval),
            subtitleProvider = { value, entries ->
                stringResource(MR.strings.library_sync_auto_interval_summary, entries[value].orEmpty())
            },
            visible = autoSync,
        ),
        Preference.PreferenceItem.SwitchPreference(
            preference = preferences.syncOnChange,
            title = stringResource(MR.strings.library_sync_on_change),
            subtitle = stringResource(MR.strings.library_sync_on_change_summary),
            visible = autoSync,
        ),
        Preference.PreferenceItem.SwitchPreference(
            preference = preferences.autoSyncWifiOnly,
            title = stringResource(MR.strings.library_sync_wifi_only),
            subtitle = stringResource(MR.strings.library_sync_wifi_only_summary),
            visible = autoSync,
        ),
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private sealed interface RestoreDialog {
    data object Loading : RestoreDialog
    data class Backups(val backups: List<SettingsBackup>) : RestoreDialog
    data class Failed(val message: String) : RestoreDialog
    data class Confirm(val backup: SettingsBackup) : RestoreDialog
}

@Composable
private fun SettingsRestoreDialog(
    state: RestoreDialog?,
    onStateChange: (RestoreDialog?) -> Unit,
    loadBackups: suspend () -> List<SettingsBackup>,
    onRestore: (SettingsBackup) -> Unit,
) {
    if (state == null) return
    val dismiss = { onStateChange(null) }

    if (state == RestoreDialog.Loading) {
        LaunchedEffect(Unit) {
            onStateChange(
                runCatching { loadBackups() }.fold(
                    onSuccess = { RestoreDialog.Backups(it) },
                    onFailure = { RestoreDialog.Failed(it.message ?: it.toString()) },
                ),
            )
        }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(MR.strings.library_sync_restore_settings)) },
        text = {
            when (state) {
                RestoreDialog.Loading -> CircularProgressIndicator()
                is RestoreDialog.Failed -> Text(state.message)
                is RestoreDialog.Backups -> if (state.backups.isEmpty()) {
                    Text(stringResource(MR.strings.library_sync_no_settings_backups))
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        state.backups.forEach { backup ->
                            ListItem(
                                headlineContent = { Text(backup.deviceName) },
                                supportingContent = { Text(backup.updatedAt.toDateTimeString()) },
                                modifier = Modifier.clickable { onStateChange(RestoreDialog.Confirm(backup)) },
                            )
                        }
                    }
                }
                is RestoreDialog.Confirm -> Text(
                    stringResource(
                        MR.strings.library_sync_restore_settings_confirm,
                        state.backup.deviceName,
                        state.backup.updatedAt.toDateTimeString(),
                    ),
                )
            }
        },
        confirmButton = {
            if (state is RestoreDialog.Confirm) {
                TextButton(onClick = { onRestore(state.backup) }) {
                    Text(stringResource(MR.strings.action_restore))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private fun Long.toDateTimeString(): String {
    if (this <= 0L) return "-"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
}

@Composable
private fun LibrarySyncProgressIndicator(percent: Int?) {
    val modifier = Modifier
        .padding(start = 16.dp)
        .size(24.dp)
    if (percent != null) {
        CircularProgressIndicator(progress = { percent / 100f }, modifier = modifier, strokeWidth = 3.dp)
    } else {
        CircularProgressIndicator(modifier = modifier, strokeWidth = 3.dp)
    }
}
