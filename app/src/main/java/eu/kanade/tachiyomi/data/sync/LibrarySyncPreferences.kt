package eu.kanade.tachiyomi.data.sync

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

// All keys are app state keys: they belong to this installation and are never part of backups, so restoring
// another device's settings keeps this device's sync options.
@Inject
@SingleIn(AppScope::class)
class LibrarySyncPreferences(
    preferenceStore: PreferenceStore,
) {
    val accountId = preferenceStore.getString(
        Preference.appStateKey("library_sync_account_id"),
        "",
    )

    val accountName = preferenceStore.getString(
        Preference.appStateKey("library_sync_account_name"),
        "",
    )

    val baseline = preferenceStore.getString(
        Preference.appStateKey("library_sync_baseline"),
        "",
    )

    private val deviceId = preferenceStore.getString(
        Preference.appStateKey("library_sync_device_id"),
        "",
    )

    /** Name shown next to records (search history, marks) created on this device. */
    val deviceName = preferenceStore.getString(
        Preference.appStateKey("library_sync_device_name"),
        defaultDeviceName(),
    )

    // Automatic sync, see LibraryAutoSync

    val autoSync = preferenceStore.getBoolean(Preference.appStateKey("library_sync_auto_enabled"), true)

    val autoSyncIntervalMinutes = preferenceStore.getInt(
        Preference.appStateKey("library_sync_auto_interval_minutes"),
        DEFAULT_AUTO_SYNC_INTERVAL_MINUTES,
    )

    val autoSyncWifiOnly = preferenceStore.getBoolean(Preference.appStateKey("library_sync_auto_wifi_only"), false)

    val syncOnChange = preferenceStore.getBoolean(Preference.appStateKey("library_sync_auto_on_change"), true)

    /**
     * Catch-up syncs in WorkManager: the number of the latest request, and the latest request a finished sync covered.
     * Both only grow, so a request is never mistaken for an older one. Kept on disk, since the queued work outlives
     * the process.
     */
    val catchUpRequested = preferenceStore.getLong(Preference.appStateKey("library_sync_catch_up_requested"), 0L)
    val catchUpSynced = preferenceStore.getLong(Preference.appStateKey("library_sync_catch_up_synced"), 0L)

    fun currentDevice(): SyncDevice {
        val id = deviceId.get().ifBlank {
            UUID.randomUUID().toString().also(deviceId::set)
        }
        return SyncDevice(
            id = id,
            name = deviceName.get().trim().ifBlank { defaultDeviceName() },
        )
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }.trim().ifBlank { "Android" }
    }

    companion object {
        const val DEFAULT_AUTO_SYNC_INTERVAL_MINUTES = 360

        /** WorkManager runs periodic work at most every 15 minutes. */
        val AUTO_SYNC_INTERVALS_MINUTES = listOf(15, 30, 60, 180, 360, 720, 1440)
    }
}

data class SyncDevice(
    val id: String,
    val name: String,
)
