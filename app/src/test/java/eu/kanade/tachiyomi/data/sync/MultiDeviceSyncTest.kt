package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MultiDeviceSyncTest {

    @Test
    fun `search history merge keeps the latest change of each query`() {
        val local = listOf(
            search("Berserk", updatedAt = 10, device = "phone"),
            search("Vagabond", updatedAt = 30, device = "phone", deleted = true),
        )
        val cloud = listOf(
            search("berserk", updatedAt = 20, device = "tablet"),
            search("Vagabond", updatedAt = 25, device = "tablet"),
            search("Monster", updatedAt = 5, device = "tablet"),
        )

        val merged = SearchHistoryMerger.merge(local, cloud, now = 40).associateBy { it.key }

        assertEquals("tablet", merged.getValue("berserk").deviceId)
        assertEquals(true, merged.getValue("vagabond").deleted)
        assertEquals(false, merged.getValue("monster").deleted)
    }

    @Test
    fun `clearing drops older entries from every device`() {
        val local = listOf(search("Berserk", updatedAt = 10))
        val cloud = listOf(search("Monster", updatedAt = 20, device = "tablet"), search("Vagabond", updatedAt = 40))

        val merged = SearchHistoryMerger.merge(local, cloud, now = 50, clearedAt = 30)

        assertEquals(listOf("Vagabond"), merged.map { it.query })
    }

    @Test
    fun `search history keeps a limited number of queries and drops old deletion markers`() {
        val now = SearchHistoryMerger.DELETION_RETENTION_MILLIS * 2
        val live = (1..SearchHistoryMerger.MAX_ENTRIES + 10).map { search("Query $it", updatedAt = now - it) }
        val oldDeletion = search("Old", updatedAt = 0, deleted = true)
        val recentDeletion = search("Recent", updatedAt = now - 1, deleted = true)

        val pruned = SearchHistoryMerger.prune(live + oldDeletion + recentDeletion, now)

        assertEquals(SearchHistoryMerger.MAX_ENTRIES, pruned.count { !it.deleted })
        assertEquals(listOf("Recent"), pruned.filter { it.deleted }.map { it.query })
        assertEquals("Query 1", pruned.first().query)
    }

    @Test
    fun `read mark merge keeps the latest change and a tie goes to the cloud`() {
        val local = listOf(mark("a", marked = true, updatedAt = 10), mark("b", marked = true, updatedAt = 10))
        val cloud = listOf(mark("a", marked = false, updatedAt = 20), mark("b", marked = false, updatedAt = 10))

        val merged = MangaMarksMerger.merge(MangaMarksFile(readMarks = local), MangaMarksFile(readMarks = cloud))

        assertEquals(listOf(false, false), merged.readMarks.sortedBy { it.key }.map { it.marked })
    }

    @Test
    fun `E-Hentai galleries are keyed by gallery id`() {
        assertEquals("eh:2345678", MangaMarkKeys.key(1L, "/g/2345678/0123456789/"))
        assertEquals("eh:2345678", MangaMarkKeys.key(2L, "https://exhentai.org/g/2345678/0123456789/"))
        assertEquals("3:/manga/one-piece", MangaMarkKeys.key(3L, "/manga/one-piece"))
        assertNull(MangaMarkKeys.galleryId("/g/12345/"))
    }

    @Test
    fun `mark index reports read entries`() {
        val index = MangaMarkIndex(
            MangaMarksFile(
                readMarks = listOf(
                    mark(MangaMarkKeys.key(1L, "/read"), marked = true, updatedAt = 1),
                    mark(MangaMarkKeys.key(1L, "/unread"), marked = false, updatedAt = 1),
                ),
            ),
        )

        assertEquals(true, index.status(1L, "/read").read)
        assertEquals(false, index.status(1L, "/unread").read)
        assertEquals(false, index.status(1L, "/unknown").read)
    }

    private fun search(query: String, updatedAt: Long, device: String = "phone", deleted: Boolean = false) =
        SearchHistoryEntry(query, updatedAt, device, device, deleted)

    private fun mark(key: String, marked: Boolean, updatedAt: Long) = ReadMark(
        key = key,
        sourceId = 1L,
        mangaUrl = key,
        title = key,
        marked = marked,
        updatedAt = updatedAt,
    )
}
