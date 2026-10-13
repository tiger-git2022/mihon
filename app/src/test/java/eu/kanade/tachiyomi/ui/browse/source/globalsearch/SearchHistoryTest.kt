package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchHistoryTest {

    @Test
    fun `new query is trimmed and added first`() {
        val history = listOf("One Piece")

        assertEquals(listOf("Berserk", "One Piece"), history.withSearchQuery("  Berserk  "))
    }

    @Test
    fun `existing query is moved first without regard to case`() {
        val history = listOf("One Piece", "Berserk", "Vagabond")

        assertEquals(
            listOf("berserk", "One Piece", "Vagabond"),
            history.withSearchQuery("berserk"),
        )
    }

    @Test
    fun `blank query leaves history unchanged`() {
        val history = listOf("One Piece")

        assertEquals(history, history.withSearchQuery("   "))
    }

    @Test
    fun `history keeps all existing entries`() {
        val history = (1..100).map { "Query $it" }

        assertEquals(listOf("Newest") + history, history.withSearchQuery("Newest"))
    }
}
