package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibrarySyncProgressTest {

    @Test
    fun `fraction counts the steps done and the known part of the current one`() {
        assertEquals(0f, LibrarySyncProgress(LibrarySyncProgress.Step.LIBRARY).fraction, DELTA)
        assertEquals(
            0.1f,
            LibrarySyncProgress(LibrarySyncProgress.Step.LIBRARY, done = 50, total = 100).fraction,
            DELTA,
        )
        assertEquals(0.4f, LibrarySyncProgress(LibrarySyncProgress.Step.MARKS).fraction, DELTA)
        // More entries than counted never go past the step
        assertEquals(
            0.2f,
            LibrarySyncProgress(LibrarySyncProgress.Step.LIBRARY, done = 120, total = 100).fraction,
            DELTA,
        )
    }

    @Test
    fun `uploading the settings backup is half of its step`() {
        val upload = LibrarySyncProgress(LibrarySyncProgress.Step.SETTINGS_BACKUP, stage = SettingsBackupStage.UPLOAD)
        assertEquals(0.7f, upload.fraction, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
