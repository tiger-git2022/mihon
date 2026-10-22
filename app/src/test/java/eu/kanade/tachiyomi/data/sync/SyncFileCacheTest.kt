package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SyncFileCacheTest {

    @TempDir
    lateinit var dir: File

    private val cache by lazy { SyncFileCache(File(dir, "cache"), "mihon-marks.json") }

    @Test
    fun `nothing is cached at first`() {
        assertNull(cache.contents())
        assertNull(cache.eTag())
        assertNull(cache.contentsIfCurrent("\"a,1\""))
    }

    @Test
    fun `the copy is used only while the cloud file has its eTag`() {
        cache.save("{}", "\"a,1\"")

        assertEquals("{}", cache.contentsIfCurrent("\"a,1\""))
        assertNull(cache.contentsIfCurrent("\"a,2\""))
        assertNull(cache.contentsIfCurrent(null))
    }

    @Test
    fun `a copy without an eTag is downloaded again but stays the baseline`() {
        cache.save("{\"v\":1}", "\"a,1\"")
        cache.save("{\"v\":2}", null)

        assertNull(cache.contentsIfCurrent("\"a,1\""))
        assertEquals("{\"v\":2}", cache.contents())
    }

    @Test
    fun `forgetting the eTag keeps the copy`() {
        cache.save("{}", "\"a,1\"")
        cache.forgetETag()

        assertNull(cache.contentsIfCurrent("\"a,1\""))
        assertEquals("{}", cache.contents())
    }

    @Test
    fun `clear removes the copy and the eTag`() {
        cache.save("{}", "\"a,1\"")
        cache.clear()

        assertNull(cache.contents())
        assertNull(cache.eTag())
    }
}
