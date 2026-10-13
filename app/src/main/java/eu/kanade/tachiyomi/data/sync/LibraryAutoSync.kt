package eu.kanade.tachiyomi.data.sync

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.system.connectivityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.repository.MangaRepository
import java.util.concurrent.TimeUnit

/**
 * Automatic OneDrive sync (fork-only feature), with the same rules as the EhViewer fork
 * (tiger-git2022/ehviewer-onedrive, docs/onedrive-sync-design.md, section 6):
 * - a periodic WorkManager sync at the chosen interval, optionally on unmetered networks only;
 * - one sync when the app starts;
 * - a debounced sync after local changes of the library, search history, "read before" marks or settings.
 *
 * Writes made by the sync itself never count as changes: the library is compared with its state right after the
 * last sync, and search history and marks are reported by the screens where the user changes them.
 */
@Inject
@SingleIn(AppScope::class)
class LibraryAutoSync(
    private val context: Context,
    private val preferences: LibrarySyncPreferences,
    private val service: LibrarySyncService,
    private val mangaRepository: MangaRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private var debounceJob: Job? = null

    /** [SystemClock.elapsedRealtime] of the first change of the batch waiting for a sync, 0 without one. */
    private var batchStartedAt = 0L

    /** A change arrived while a sync ran; it is synced after it. */
    private var pendingAfterSync = false

    @Volatile
    private var inForeground = false

    private var launchSyncDone = false

    /** Library entries as last seen by [observeLibrary], null while not observed. */
    @Volatile
    private var knownFavorites: Set<LibrarySyncKey>? = null

    /** The library changed while a sync ran, by the sync itself or by the user. */
    @Volatile
    private var libraryChangedDuringSync = false

    /**
     * Only the main process syncs. The crash screen runs in its own process, where WorkManager is not initialized.
     */
    private val isMainProcess: Boolean by lazy {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            context.getSystemService<ActivityManager>()?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
        processName == null || processName == context.packageName
    }

    // SharedPreferences keeps listeners weakly, so this one is held here
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && inForeground && isSyncedSetting(key)) notifyChanged()
    }

    val isAllowed: Boolean
        get() = preferences.accountId.get().isNotBlank() && preferences.autoSync.get()

    private val isChangeSyncAllowed: Boolean
        get() = isAllowed && preferences.syncOnChange.get()

    private fun takeCatchUpRequest(): Boolean = synchronized(lock) {
        preferences.catchUpPending.get().also { if (it) preferences.catchUpPending.set(false) }
    }

    /** Called once from the Application. */
    fun init() {
        if (!isMainProcess) return
        merge(
            preferences.accountId.changes(),
            preferences.autoSync.changes(),
            preferences.autoSyncIntervalMinutes.changes(),
            preferences.autoSyncWifiOnly.changes(),
        )
            .onEach { updateWork() }
            .launchIn(scope)
        observeLibrary()
        service.finished
            .onEach { onSyncFinished() }
            .launchIn(scope)
        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(settingsListener)
    }

    fun onAppForeground() {
        if (!isMainProcess) return
        inForeground = true
        val launchSync = synchronized(lock) {
            !launchSyncDone.also { launchSyncDone = true }
        }
        if (launchSync && isAllowed) {
            scope.launch { syncNowOrLater(fromChange = false) }
        }
    }

    /** The app went to the background: WorkManager syncs the changes the countdown has not synced yet. */
    fun onAppBackground() {
        if (!isMainProcess) return
        inForeground = false
        val pending = synchronized(lock) {
            val pending = debounceJob != null || pendingAfterSync
            debounceJob?.cancel()
            debounceJob = null
            batchStartedAt = 0L
            pendingAfterSync = false
            pending
        }
        if (pending && isChangeSyncAllowed) enqueueCatchUpWork(fromChange = true)
    }

    /** Called by the screens where the user changes synced data. */
    fun notifyChanged() {
        if (!isChangeSyncAllowed) return
        synchronized(lock) {
            if (service.isSyncing) {
                pendingAfterSync = true
                return
            }
            if (!inForeground) {
                enqueueCatchUpWork(fromChange = true)
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (batchStartedAt == 0L) batchStartedAt = now
            val wait = ChangeSyncDebounce.delayMillis(now, batchStartedAt)
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(wait)
                synchronized(lock) {
                    debounceJob = null
                    batchStartedAt = 0L
                }
                // The options may have been turned off during the countdown
                if (!isChangeSyncAllowed) return@launch
                // Not a child of the countdown, so a later change cannot cancel the running sync
                scope.launch { syncNowOrLater(fromChange = true) }
            }
        }
    }

    private suspend fun syncNowOrLater(fromChange: Boolean) {
        if (!isNetworkAllowed()) {
            enqueueCatchUpWork(fromChange)
            return
        }
        try {
            val result = service.sync()
            // A failed search history, marks or settings step: WorkManager retries with backoff
            if (result.warning != null) enqueueCatchUpWork(fromChange)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OneDriveSyncAuthException) {
            logcat(LogPriority.WARN, e)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            // WorkManager retries with backoff
            enqueueCatchUpWork(fromChange)
        }
    }

    /**
     * The sync of [LibrarySyncWorker]. The options are checked again here, since they may have changed after the
     * work was queued.
     */
    internal suspend fun runWork(catchUpFromChange: Boolean, runAttemptCount: Int): WorkOutcome {
        if (!isAllowed || (catchUpFromChange && !preferences.syncOnChange.get())) return WorkOutcome.SUCCESS
        // Queued with a network constraint from before "only on Wi-Fi" was turned on
        if (!isNetworkAllowed()) return WorkOutcome.RETRY
        val pending = takeCatchUpRequest()
        // Catch-up work is appended behind running work; an earlier one in the chain already synced these changes
        if (catchUpFromChange && !pending) return WorkOutcome.SUCCESS
        val outcome = try {
            // Background syncs show nothing
            val result = service.sync()
            if (result.warning == null) WorkOutcome.SUCCESS else retryOrFail(runAttemptCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OneDriveSyncAuthException) {
            // Signing in again needs the user, retrying cannot help
            logcat(LogPriority.WARN, e)
            WorkOutcome.FAILURE
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            retryOrFail(runAttemptCount)
        }
        // The retry still has these changes to sync
        if (outcome == WorkOutcome.RETRY && pending) preferences.catchUpPending.set(true)
        return outcome
    }

    private fun retryOrFail(runAttemptCount: Int): WorkOutcome {
        return if (runAttemptCount < MAX_WORK_RETRIES) WorkOutcome.RETRY else WorkOutcome.FAILURE
    }

    private suspend fun onSyncFinished() {
        val rerun = synchronized(lock) {
            pendingAfterSync.also { pendingAfterSync = false }
        }
        // Library changes made while the sync ran were not counted, see observeLibrary. Only checked when the
        // library was written meanwhile: a library that differs from the synced state without any write (if the
        // sync could not apply an entry) must not start a sync after every sync.
        val libraryWritten = libraryChangedDuringSync.also { libraryChangedDuringSync = false }
        if (rerun || (libraryWritten && libraryChangedSinceSync())) notifyChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLibrary() {
        combine(
            preferences.accountId.changes(),
            preferences.autoSync.changes(),
            preferences.syncOnChange.changes(),
        ) { accountId, autoSync, syncOnChange -> accountId.isNotBlank() && autoSync && syncOnChange }
            .distinctUntilChanged()
            .flatMapLatest { observe -> if (observe) favoriteKeys() else flowOf<Set<LibrarySyncKey>?>(null) }
            .onEach { keys ->
                val previous = knownFavorites
                knownFavorites = keys
                if (keys == null || previous == null || keys == previous) return@onEach
                if (service.isSyncing) {
                    libraryChangedDuringSync = true
                    return@onEach
                }
                // A late emission of what the sync itself wrote matches the state after the sync
                if (libraryChangedSinceSync()) notifyChanged()
            }
            .launchIn(scope)
    }

    private fun favoriteKeys(): Flow<Set<LibrarySyncKey>> {
        return mangaRepository.getLibraryMangaAsFlow()
            .map { library -> library.mapTo(HashSet()) { LibrarySyncKey(it.manga.source, it.manga.url) } }
            .distinctUntilChanged()
    }

    private suspend fun libraryChangedSinceSync(): Boolean {
        val synced = service.lastSyncedFavorites ?: return true
        val current = mangaRepository.getFavorites().mapTo(HashSet()) { LibrarySyncKey(it.source, it.url) }
        return current != synced
    }

    private fun isSyncedSetting(key: String): Boolean {
        // App state keys (sync options included) are not part of the settings backup
        return !Preference.isAppState(key) && !Preference.isPrivate(key)
    }

    private fun isNetworkAllowed(): Boolean {
        val connectivityManager = context.connectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !preferences.autoSyncWifiOnly.get() ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(
            if (preferences.autoSyncWifiOnly.get()) NetworkType.UNMETERED else NetworkType.CONNECTED,
        )
        .build()

    /** Schedules or cancels the periodic sync after a change of the account or the auto sync options. */
    private fun updateWork() {
        val workManager = WorkManager.getInstance(context)
        if (isAllowed) {
            val interval = preferences.autoSyncIntervalMinutes.get()
                .coerceAtLeast(LibrarySyncPreferences.AUTO_SYNC_INTERVALS_MINUTES.first())
                .toLong()
            val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setInitialDelay(interval, TimeUnit.MINUTES)
                .addTag(PERIODIC_WORK_NAME)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(CATCH_UP_WORK_NAME)
        }
    }

    /**
     * Syncs once the network allows it, also after the app went to the background. [fromChange] tells a sync of
     * changes, which is skipped if "sync after changes" is turned off meanwhile, from a retry of a failed sync.
     */
    private fun enqueueCatchUpWork(fromChange: Boolean) {
        preferences.catchUpPending.set(true)
        val request = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setConstraints(constraints())
            .setInputData(workDataOf(LibrarySyncWorker.KEY_FROM_CHANGE to fromChange))
            .addTag(CATCH_UP_WORK_NAME)
            .build()
        // Appended behind a running catch-up, which may have read the pending flag already
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CATCH_UP_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    internal enum class WorkOutcome { SUCCESS, RETRY, FAILURE }

    private companion object {
        const val PERIODIC_WORK_NAME = "LibrarySyncPeriodic"
        const val CATCH_UP_WORK_NAME = "LibrarySyncCatchUp"
        const val MAX_WORK_RETRIES = 3
    }
}

/** Runs the periodic sync and the catch-up sync of changes made before the app went to the background. */
class LibrarySyncWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val outcome = applicationContext.appGraph.libraryAutoSync.runWork(
            catchUpFromChange = inputData.getBoolean(KEY_FROM_CHANGE, false),
            runAttemptCount = runAttemptCount,
        )
        return when (outcome) {
            LibraryAutoSync.WorkOutcome.SUCCESS -> Result.success()
            LibraryAutoSync.WorkOutcome.RETRY -> Result.retry()
            LibraryAutoSync.WorkOutcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val KEY_FROM_CHANGE = "from_change"
    }
}

/**
 * Debounce of change triggered syncs: every change restarts a 60 s countdown, and a batch waits at most 5 min from
 * its first change.
 */
internal object ChangeSyncDebounce {
    const val DEBOUNCE_MILLIS = 60_000L
    const val MAX_WAIT_MILLIS = 5 * 60_000L

    fun delayMillis(now: Long, batchStartedAt: Long): Long {
        return minOf(DEBOUNCE_MILLIS, batchStartedAt + MAX_WAIT_MILLIS - now).coerceAtLeast(0)
    }
}
