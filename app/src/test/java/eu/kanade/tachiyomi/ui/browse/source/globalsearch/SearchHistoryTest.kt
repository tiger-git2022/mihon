package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.tachiyomi.data.sync.SearchHistoryEntry
import eu.kanade.tachiyomi.data.sync.SyncDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchHistoryTest {

    private val phone = SyncDevice(id = "phone", name = "Phone")
    private val tablet = SyncDevice(id = "tablet", name = "Tablet")

    @Test
    fun `new query is trimmed and added first`() {
        val history = listOf(entry("One Piece", 1))

        val result = history.withSearchQuery("  Berserk  ", phone, now = 2)

        assertEquals(listOf("Berserk", "One Piece"), result.map { it.query })
        assertEquals(SearchHistoryEntry("Berserk", 2, "phone", "Phone"), result.first())
    }

    @Test
    fun `existing query is moved first without regard to case`() {
        val history = listOf(entry("One Piece", 3), entry("Berserk", 2), entry("Vagabond", 1))

        assertEquals(
            listOf("berserk", "One Piece", "Vagabond"),
            history.withSearchQuery("berserk", phone, now = 4).map { it.query },
        )
    }

    @Test
    fun `blank query leaves history unchanged`() {
        val history = listOf(entry("One Piece", 1))

        assertEquals(history, history.withSearchQuery("   ", phone, now = 2))
    }

    @Test
    fun `searching a deleted query brings it back`() {
        val history = listOf(entry("Berserk", 1).copy(deleted = true))

        val result = history.withSearchQuery("Berserk", phone, now = 2)

        assertEquals(listOf(SearchHistoryEntry("Berserk", 2, "phone", "Phone")), result)
    }

    @Test
    fun `deleted queries stay as deletion markers and are hidden`() {
        val history = listOf(entry("One Piece", 2), entry("Berserk", 1))

        val result = history.withoutSearchQuery("berserk", phone, now = 5)

        assertEquals(SearchHistoryEntry("Berserk", 5, "phone", "Phone", deleted = true), result[1])
        assertEquals(listOf("One Piece"), result.toSearchHistoryItems(phone).map { it.query })
    }

    @Test
    fun `items name the device only for queries from other devices`() {
        val history = listOf(
            entry("One Piece", 1),
            SearchHistoryEntry("Berserk", 2, "tablet", "Tablet"),
        )

        assertEquals(
            listOf(SearchHistoryItem("Berserk", "Tablet"), SearchHistoryItem("One Piece", null)),
            history.toSearchHistoryItems(phone),
        )
        assertEquals(
            listOf(SearchHistoryItem("Berserk", null), SearchHistoryItem("One Piece", "Phone")),
            history.toSearchHistoryItems(tablet),
        )
    }

    private fun entry(query: String, updatedAt: Long) = SearchHistoryEntry(query, updatedAt, "phone", "Phone")
}
