package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibrarySyncMergerTest {

    @Test
    fun `first sync combines favorites from both devices`() {
        val local = entry(1, "/local", favorite = true, modifiedAt = 10)
        val cloud = entry(2, "/cloud", favorite = true, modifiedAt = 20)

        val result = LibrarySyncMerger.merge(
            local = mapOf(local.key to local),
            cloud = LibrarySyncFile(entries = listOf(cloud)),
            baseline = null,
            now = 30,
        )

        assertEquals(setOf(local.key, cloud.key), result.entries.map { it.key }.toSet())
    }

    @Test
    fun `local removal wins when cloud has not changed since baseline`() {
        val baseline = entry(1, "/manga", favorite = true, modifiedAt = 10)
        val local = baseline.copy(favorite = false, modifiedAt = 20)

        val result = LibrarySyncMerger.merge(
            local = mapOf(local.key to local),
            cloud = LibrarySyncFile(entries = listOf(baseline)),
            baseline = LibrarySyncFile(entries = listOf(baseline)),
            now = 30,
        )

        assertEquals(false, result.entries.single().favorite)
    }

    @Test
    fun `cloud removal wins when local has not changed since baseline`() {
        val baseline = entry(1, "/manga", favorite = true, modifiedAt = 10)
        val cloud = baseline.copy(favorite = false, modifiedAt = 20)

        val result = LibrarySyncMerger.merge(
            local = mapOf(baseline.key to baseline),
            cloud = LibrarySyncFile(entries = listOf(cloud)),
            baseline = LibrarySyncFile(entries = listOf(baseline)),
            now = 30,
        )

        assertEquals(false, result.entries.single().favorite)
    }

    @Test
    fun `newer change wins initial conflict`() {
        val local = entry(1, "/manga", favorite = true, modifiedAt = 20)
        val cloud = local.copy(favorite = false, modifiedAt = 30)

        val result = LibrarySyncMerger.merge(
            local = mapOf(local.key to local),
            cloud = LibrarySyncFile(entries = listOf(cloud)),
            baseline = null,
            now = 40,
        )

        assertEquals(false, result.entries.single().favorite)
    }

    @Test
    fun `removal wins a timestamp tie`() {
        val local = entry(1, "/manga", favorite = true, modifiedAt = 20)
        val cloud = local.copy(favorite = false)

        val result = LibrarySyncMerger.merge(
            local = mapOf(local.key to local),
            cloud = LibrarySyncFile(entries = listOf(cloud)),
            baseline = null,
            now = 30,
        )

        assertEquals(false, result.entries.single().favorite)
    }

    private fun entry(sourceId: Long, url: String, favorite: Boolean, modifiedAt: Long) = LibrarySyncEntry(
        sourceId = sourceId,
        mangaUrl = url,
        favorite = favorite,
        modifiedAt = modifiedAt,
        manga = LibrarySyncManga(title = url),
    )
}
