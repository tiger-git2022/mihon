package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressThrottleTest {

    private var now = 0L
    private val published = mutableListOf<LibrarySyncProgress?>()
    private val throttle = ProgressThrottle<LibrarySyncProgress>(
        samePart = LibrarySyncProgress::isSamePart,
        isFinal = { it.total > 0 && it.done >= it.total },
        clock = { now },
        publish = { published += it },
    )

    private fun download(done: Int, total: Int = 100) = LibrarySyncProgress(
        LibrarySyncProgress.Step.EHVIEWER_FAVORITES,
        done = done,
        total = total,
        favoritesStage = EhFavoritesSyncStage.DOWNLOAD,
    )

    @Test
    fun `progress within a part is published at most once a second`() {
        throttle.update(download(0))
        now += 200
        throttle.update(download(10))
        now += 500
        throttle.update(download(20))
        assertEquals(listOf(download(0)), published)
        assertEquals(download(20), throttle.latest)
        now += 300
        throttle.update(download(30))
        assertEquals(listOf(download(0), download(30)), published)
    }

    @Test
    fun `a new part, the end of a part and the end of the sync are published right away`() {
        throttle.update(download(0))
        throttle.update(download(100))
        val read = download(0).copy(favoritesStage = EhFavoritesSyncStage.READ)
        throttle.update(read)
        val marks = LibrarySyncProgress(LibrarySyncProgress.Step.MARKS)
        throttle.update(marks)
        val upload = marks.copy(phase = LibrarySyncProgress.Phase.UPLOAD)
        throttle.update(upload)
        throttle.update(null)
        assertEquals(listOf(download(0), download(100), read, marks, upload, null), published)
    }
}
