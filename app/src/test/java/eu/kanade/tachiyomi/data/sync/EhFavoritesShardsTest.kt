package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class EhFavoritesShardsTest {

    private val normalizer = EhTitleNormalizer()

    private val promise = EhIndexedGallery(gid = 10, titleJpn = "[作者A] 放課後の約束", local = true)
    private val secret = EhIndexedGallery(gid = 20, title = "[Sakka B] Kanojo no Himitsu", slot = 1)
    private val homework = EhIndexedGallery(gid = 3_000, titleJpn = "[作者C] 夏休みの宿題", slot = 2)
    private val holiday = EhIndexedGallery(gid = 3_500, title = "[Sakka D] Natsuyasumi no Omoide", local = true)

    private val hashA = EhFavoritesTestIndexes.hash(0xa)
    private val hashB = EhFavoritesTestIndexes.hash(0xb)
    private val hashC = EhFavoritesTestIndexes.hash(0xc)

    private fun shard(hash: String, vararg galleries: EhIndexedGallery) =
        EhFavoritesShardIndex.build(hash, galleries.toList(), normalizer)

    private fun EhFavoritesIndex.gids(title: String, url: String = "/manga/1") =
        find(url, title)?.galleries?.map { it.gid }

    @Test
    fun `unchanged shards are reused, only new ones are indexed`() {
        val indexed = mutableListOf<String>()
        val first = EhFavoritesIndex.EMPTY.updated(listOf("Slot"), listOf(hashA, hashB)) { hash ->
            indexed += hash
            if (hash == hashA) shard(hash, promise, secret) else shard(hash, homework)
        }
        assertEquals(listOf(hashA, hashB), indexed)
        assertEquals(3, first.size)
        assertEquals(listOf(20L), first.gids("Kanojo no Himitsu"))
        assertEquals(listOf(3_000L), first.gids("夏休みの宿題"))

        // EhViewer added a favorite to the second gid range: only that shard is new
        indexed.clear()
        val second = first.updated(listOf("Slot"), listOf(hashA, hashC)) { hash ->
            indexed += hash
            shard(hash, homework, holiday)
        }
        assertEquals(listOf(hashC), indexed)
        assertSame(first.shards[0], second.shards[0])
        assertTrue(second.hasShard(hashA) && second.hasShard(hashC))
        assertFalse(second.hasShard(hashB))
        assertEquals(listOf(3_500L), second.gids("Natsuyasumi no Omoide"))
        assertEquals(listOf(10L), second.gids("放課後の約束"))
        // The old index keeps answering until it is replaced
        assertNull(first.gids("Natsuyasumi no Omoide"))
    }

    @Test
    fun `gallery ids are found in their shard`() {
        val index = EhFavoritesIndex(
            emptyList(),
            listOf(shard(hashB, homework, holiday), shard(hashA, promise, secret)),
            normalizer,
        )
        assertEquals(listOf(10L), index.gids("anything", url = "/g/10/0123456789/"))
        assertEquals(listOf(20L), index.gids("anything", url = "/g/20/0123456789/"))
        assertEquals(listOf(3_500L), index.gids("anything", url = "/g/3500/0123456789/"))
        assertTrue(index.find("/g/3500/0123456789/", "anything")!!.byGalleryId)
        assertNull(index.gids("anything", url = "/g/5/0123456789/"))
        assertNull(index.gids("anything", url = "/g/2000/0123456789/"))
        assertEquals(listOf(10L, 20L, 3_000L, 3_500L), index.galleries.map { it.gid }.toList())
    }

    @Test
    fun `galleries of a shard are kept by gid`() {
        val shard = shard(hashA, secret, promise)
        assertEquals(listOf(10L, 20L), shard.galleries.map { it.gid })
        assertEquals(0, shard.indexOfGid(10))
        assertEquals(-1, shard.indexOfGid(15))
    }

    @Test
    fun `shards are read streamed, unknown keys ignored`() {
        val json = """
            {"version":2,"extra":1,"galleries":[
              {"gid":10,"token":"abc","title":"T","titleJpn":"J","uploader":"u","category":2,"local":true,"slot":-1,
               "titlesLookedUp":true,"future":{"a":1}},
              {"gid":20,"slot":3}
            ]}
        """.trimIndent()
        val shard = EhFavoritesFormat.decodeShard(gzip(json).inputStream())
        assertEquals(listOf(10L, 20L), shard.galleries.map { it.gid })
        assertEquals("abc", shard.galleries[0].token)
        assertTrue(shard.galleries[0].local)
        assertTrue(shard.galleries[1].inCloud)
    }

    @Test
    fun `a damaged or newer shard is rejected`() {
        val bytes = gzip("""{"version":2,"galleries":[{"gid":10}]}""")
        // A changed byte fails the gzip checksum, even after the JSON was read
        val damaged = bytes.copyOf().also { it[it.size - 6] = (it[it.size - 6] + 1).toByte() }
        assertThrows(Exception::class.java) { EhFavoritesFormat.decodeShard(damaged.inputStream()) }
        assertThrows(LibrarySyncException::class.java) {
            EhFavoritesFormat.decodeShard(gzip("""{"version":3,"galleries":[]}""").inputStream())
        }
    }

    @Test
    fun `manifest is read with unknown keys ignored`() {
        val manifest = EhFavoritesFormat.decodeManifest(
            """
            {"version":2,"updatedAt":1760000000000,"deviceId":"d","deviceName":"Phone","cloudUpdatedAt":0,
             "cloudRefreshedAt":0,"slotNames":["A","B"],"count":3,"newKey":[1,2],
             "shards":[{"hash":"$hashA","minGid":0,"count":2,"size":100,"more":true},
                       {"hash":"$hashB","minGid":1000,"count":1,"size":50}]}
            """.trimIndent(),
        )
        assertNull(manifest.ehUserKey)
        assertEquals(listOf("A", "B"), manifest.slotNames)
        assertEquals(listOf(0L, 1000L), manifest.shards.map { it.minGid })
        assertEquals("ehviewer/export/favorites/shard-$hashA.json.gz", EhFavoritesManifest.shardPath(hashA))
    }

    @Test
    fun `a newer or invalid manifest is rejected`() {
        assertThrows(LibrarySyncException::class.java) { EhFavoritesFormat.decodeManifest("""{"version":3}""") }
        assertThrows(LibrarySyncException::class.java) {
            EhFavoritesFormat.decodeManifest("""{"version":2,"shards":[{"hash":"../../secret"}]}""")
        }
    }

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }
}
