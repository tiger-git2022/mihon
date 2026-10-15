package eu.kanade.tachiyomi.data.sync

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.library.librarySyncMessage
import eu.kanade.tachiyomi.ui.library.librarySyncProgressText
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import mihon.icons.materialsymbols.R as MaterialSymbolsR

/**
 * The sync started from the sync action or the settings (fork-only feature). Runs as expedited WorkManager work in a
 * foreground service with a progress notification, so it goes on after the app went to the background, where a
 * coroutine of the process alone may be frozen or killed. The result is a toast while the app is shown and a
 * notification otherwise.
 *
 * Work stopped by the system is run again by WorkManager, so the sync is never dropped silently.
 */
class LibraryManualSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val service = context.appGraph.librarySyncService

    override suspend fun doWork(): Result {
        service.setManualSyncRunning(true)
        // The result of an earlier sync no longer applies
        applicationContext.cancelNotification(ID_RESULT)
        setForegroundSafely()
        return try {
            coroutineScope {
                val progressUpdates = launch {
                    service.progress.filterNotNull().collect { progress ->
                        applicationContext.notify(ID_PROGRESS, progressNotification(progress))
                    }
                }
                val (message, failed) = try {
                    val result = service.sync()
                    applicationContext.librarySyncMessage(result) to (result.warning != null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                    applicationContext.stringResource(MR.strings.library_sync_failed, e.message.orEmpty()) to true
                }
                // Joined, so no update comes after the notification is removed
                progressUpdates.cancelAndJoin()
                showResult(message, failed)
            }
            // A failed sync is shown to the user, who decides whether to sync again
            Result.success()
        } finally {
            service.setManualSyncRunning(false)
            applicationContext.cancelNotification(ID_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            ID_PROGRESS,
            progressNotification(service.progress.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun progressNotification(progress: LibrarySyncProgress?): Notification {
        val context = applicationContext
        return context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.library_sync_notification_title))
            setContentText(
                progress?.let { context.librarySyncProgressText(it) }
                    ?: context.stringResource(MR.strings.library_sync_syncing),
            )
            setSmallIcon(MaterialSymbolsR.drawable.rounded_refresh)
            setContentIntent(openLibraryIntent(context))
            setOngoing(true)
            setOnlyAlertOnce(true)
            setProgress(100, progress?.let { (it.fraction * 100).toInt() } ?: 0, progress == null)
        }.build()
    }

    private suspend fun showResult(message: String, failed: Boolean) {
        val context = applicationContext
        val shown = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                .also { if (it) context.toast(message) }
        }
        if (shown) return
        context.notify(ID_RESULT, Notifications.CHANNEL_COMMON) {
            setContentTitle(context.stringResource(MR.strings.library_sync_notification_title))
            setContentText(message)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
            setSmallIcon(
                if (failed) {
                    MaterialSymbolsR.drawable.rounded_filled_warning
                } else {
                    MaterialSymbolsR.drawable.rounded_check
                },
            )
            setContentIntent(openLibraryIntent(context))
            setAutoCancel(true)
        }
    }

    companion object {
        private const val WORK_NAME = "LibrarySyncManual"

        // Kept here rather than in Notifications, which is an upstream file
        private const val ID_PROGRESS = -1101
        private const val ID_RESULT = -1102

        /**
         * A manual sync is queued or runs. Read from WorkManager, so it also covers work kept from a process that
         * ended, which [LibrarySyncService.manualSyncRunning] does not know about.
         */
        fun isQueued(context: Context): Flow<Boolean> = context.workManager
            .getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .map { infos -> infos.any { !it.state.isFinished } }
            .distinctUntilChanged()

        /** Starts a manual sync, unless one is queued or running already. */
        fun start(context: Context) {
            context.appGraph.librarySyncService.setManualSyncRunning(true)
            val request = OneTimeWorkRequestBuilder<LibraryManualSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_NAME)
                .build()
            context.workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun openLibraryIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Constants.SHORTCUT_LIBRARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
