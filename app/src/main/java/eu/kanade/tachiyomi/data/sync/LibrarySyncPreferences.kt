package eu.kanade.tachiyomi.data.sync

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

// The account, baseline and device keys are app state keys: they belong to this installation and are never
// part of backups.
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

    // Automatic sync, see LibraryAutoSync. User preferences, so they are part of settings backups.

    val autoSync = preferenceStore.getBoolean(AUTO_SYNC_KEY_PREFIX + "enabled", true)

    val autoSyncIntervalMinutes = preferenceStore.getInt(
        AUTO_SYNC_KEY_PREFIX + "interval_minutes",
        DEFAULT_AUTO_SYNC_INTERVAL_MINUTES,
    )

    val autoSyncWifiOnly = preferenceStore.getBoolean(AUTO_SYNC_KEY_PREFIX + "wifi_only", false)

    val syncOnChange = preferenceStore.getBoolean(AUTO_SYNC_KEY_PREFIX + "on_change", true)

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
        /** Prefix of the auto sync options; changing them is not a change of the synced settings. */
        const val AUTO_SYNC_KEY_PREFIX = "library_sync_auto_"

        const val DEFAULT_AUTO_SYNC_INTERVAL_MINUTES = 360

        /** WorkManager runs periodic work at most every 15 minutes. */
        val AUTO_SYNC_INTERVALS_MINUTES = listOf(15, 30, 60, 180, 360, 720, 1440)
    }
}

data class SyncDevice(
    val id: String,
    val name: String,
)
