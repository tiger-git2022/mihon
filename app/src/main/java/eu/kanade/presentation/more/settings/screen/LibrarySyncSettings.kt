package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
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
    val activity = remember(context) { context.findActivity() }
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(service) {
        runCatching { service.refreshAccount() }
            .onFailure { logcat(LogPriority.ERROR, it) }
    }

    val status = if (accountName.isBlank()) {
        stringResource(MR.strings.library_sync_not_signed_in)
    } else {
        stringResource(MR.strings.library_sync_signed_in_as, accountName)
    }

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
                        title = if (isBusy) {
                            stringResource(MR.strings.library_sync_syncing)
                        } else {
                            stringResource(MR.strings.library_sync_now)
                        },
                        onClick = action {
                            runCatching {
                                withContext(Dispatchers.IO) { service.sync() }
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
                                logcat(LogPriority.ERROR, error)
                                context.toast(
                                    context.stringResource(
                                        MR.strings.library_sync_failed,
                                        error.message.orEmpty(),
                                    ),
                                )
                            }
                        },
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
