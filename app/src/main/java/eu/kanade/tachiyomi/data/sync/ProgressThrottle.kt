package eu.kanade.tachiyomi.data.sync

/**
 * Passes progress on to [publish] at most once per [intervalMillis] while it stays in the same part ([samePart]), so
 * a sync reporting every few kilobytes does not redraw the screen each frame (fork-only feature). A new part, a
 * [isFinal] value and null (done) are passed on right away.
 */
internal class ProgressThrottle<T : Any>(
    private val samePart: (T, T) -> Boolean,
    private val isFinal: (T) -> Boolean = { false },
    private val intervalMillis: Long = 1_000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val publish: (T?) -> Unit,
) {
    /** The value last set, published or not. */
    @Volatile
    var latest: T? = null
        private set

    private var published: T? = null
    private var publishedAt = 0L

    @Synchronized
    fun update(value: T?) {
        latest = value
        val previous = published
        val now = clock()
        val due = value == null ||
            previous == null ||
            !samePart(previous, value) ||
            isFinal(value) ||
            now - publishedAt >= intervalMillis
        if (!due) return
        published = value
        publishedAt = now
        publish(value)
    }
}
