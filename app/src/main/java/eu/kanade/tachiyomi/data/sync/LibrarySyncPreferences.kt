package eu.kanade.tachiyomi.data.sync

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

// All keys are app state keys: they belong to this installation and are never part of backups.
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
}

data class SyncDevice(
    val id: String,
    val name: String,
)
