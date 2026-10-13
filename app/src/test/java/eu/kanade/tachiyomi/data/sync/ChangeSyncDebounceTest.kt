package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChangeSyncDebounceTest {

    @Test
    fun `a new batch waits the full countdown`() {
        assertEquals(60_000L, ChangeSyncDebounce.delayMillis(now = 1_000, batchStartedAt = 1_000))
    }

    @Test
    fun `every change restarts the countdown`() {
        assertEquals(60_000L, ChangeSyncDebounce.delayMillis(now = 1_000 + 50_000, batchStartedAt = 1_000))
    }

    @Test
    fun `a batch waits at most five minutes from its first change`() {
        val start = 1_000L
        assertEquals(30_000L, ChangeSyncDebounce.delayMillis(now = start + 270_000, batchStartedAt = start))
        assertEquals(0L, ChangeSyncDebounce.delayMillis(now = start + 300_000, batchStartedAt = start))
        assertEquals(0L, ChangeSyncDebounce.delayMillis(now = start + 400_000, batchStartedAt = start))
    }
}
