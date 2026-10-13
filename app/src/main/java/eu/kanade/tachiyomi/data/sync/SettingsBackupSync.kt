package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreWorker
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps one settings backup per device in the OneDrive app folder (fork-only feature). Each sync
 * uploads this device's settings; any device's backup can be restored.
 *
 * Backups use the regular backup format with app, source and extension repo settings only.
 * Private settings (logins, tokens) are left out. Sync state uses app state preferences, which
 * backups never contain, so a restore does not change the OneDrive account or this device's id.
 */
@Inject
class SettingsBackupSync(
    private val context: Context,
    private val backupCreatorFactory: BackupCreator.Factory,
    private val oneDriveSyncClient: OneDriveSyncClient,
) {

    suspend fun upload(device: SyncDevice) {
        val fileName = fileName(device)
        val bytes = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "tiger-settings-backup.tachibk")
            try {
                file.delete()
                file.createNewFile()
                backupCreatorFactory.create(isAutoBackup = false).backup(Uri.fromFile(file), BACKUP_OPTIONS)
                file.readBytes()
            } finally {
                file.delete()
            }
        }
        oneDriveSyncClient.writeBytes(fileName, bytes)

        // Remove backups this device uploaded under an earlier name
        oneDriveSyncClient.listFiles(FILE_PREFIX)
            .filter { parse(it)?.deviceIdPrefix == device.id.idPrefix() && it.name != fileName }
            .forEach { oneDriveSyncClient.deleteFile(it.name) }
    }

    suspend fun list(): List<SettingsBackup> {
        return oneDriveSyncClient.listFiles(FILE_PREFIX)
            .mapNotNull(::parse)
            .sortedByDescending(SettingsBackup::updatedAt)
    }

    /** Downloads [backup] and starts restoring it like a regular backup restore. */
    suspend fun restore(backup: SettingsBackup) {
        val bytes = oneDriveSyncClient.readBytes(backup.fileName)
            ?: throw OneDriveSyncException("The settings backup no longer exists on OneDrive")
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "tiger-settings-restore.tachibk").apply { writeBytes(bytes) }
        }
        BackupRestoreWorker.start(context.workManager, Uri.fromFile(file), RESTORE_OPTIONS)
    }

    private fun parse(file: OneDriveFile): SettingsBackup? {
        val match = FILE_REGEX.matchEntire(file.name) ?: return null
        return SettingsBackup(
            fileName = file.name,
            deviceName = match.groupValues[1],
            deviceIdPrefix = match.groupValues[2],
            updatedAt = file.lastModified,
        )
    }

    private fun fileName(device: SyncDevice): String {
        val name = device.name.map { if (it.isLetterOrDigit() || it in " -_") it else '_' }
            .joinToString("")
            .trim()
            .take(40)
            .ifBlank { "device" }
        return "$FILE_PREFIX$name-${device.id.idPrefix()}.tachibk"
    }

    private fun String.idPrefix() = filter { it.isLetterOrDigit() }.take(8).lowercase()

    private companion object {
        const val FILE_PREFIX = "mihon-settings-"
        val FILE_REGEX = Regex("""mihon-settings-(.*)-([0-9a-z]{8})\.tachibk""")

        val BACKUP_OPTIONS = BackupOptions(
            libraryEntries = false,
            categories = false,
            chapters = false,
            tracking = false,
            history = false,
            readEntries = false,
            appSettings = true,
            extensionStores = true,
            sourceSettings = true,
            privateSettings = false,
        )

        val RESTORE_OPTIONS = RestoreOptions(
            libraryEntries = false,
            categories = false,
            appSettings = true,
            extensionStores = true,
            sourceSettings = true,
        )
    }
}

data class SettingsBackup(
    val fileName: String,
    val deviceName: String,
    val deviceIdPrefix: String,
    /** Epoch milliseconds, 0 if unknown. */
    val updatedAt: Long,
)
