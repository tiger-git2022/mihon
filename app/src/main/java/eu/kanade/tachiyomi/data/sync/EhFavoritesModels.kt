package eu.kanade.tachiyomi.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * `ehviewer/export/favorites/manifest.json` written by the EhViewer fork (tiger-git2022/ehviewer-onedrive,
 * docs/onedrive-sync-design.md) into the same OneDrive app folder: the local favorites and the E-Hentai cloud
 * favorites of EhViewer, split by gallery id into [shards]. Read only here.
 */
@Serializable
data class EhFavoritesManifest(
    val version: Int = 0,
    val updatedAt: Long = 0,
    val deviceId: String = "",
    val deviceName: String = "",
    val ehUserKey: String? = null,
    val cloudUpdatedAt: Long = 0,
    val cloudRefreshedAt: Long = 0,
    /** Names of the 10 cloud favorite slots, empty if unknown. */
    val slotNames: List<String> = emptyList(),
    /** Galleries in all shards. */
    val count: Int = 0,
    /** Sorted by [EhFavoritesShardRef.minGid]. */
    val shards: List<EhFavoritesShardRef> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val FOLDER = "ehviewer/export/favorites"
        const val PATH = "$FOLDER/manifest.json"

        private val HASH = Regex("[0-9a-f]{32}")

        fun isValidHash(hash: String) = HASH.matches(hash)

        fun shardFileName(hash: String) = "shard-$hash.json.gz"

        fun shardPath(hash: String) = "$FOLDER/${shardFileName(hash)}"
    }
}

/**
 * A shard of [EhFavoritesManifest]: the galleries with [minGid] <= gid < the `minGid` of the next shard, in the
 * immutable file [EhFavoritesManifest.shardPath] named by [hash] (the start of the SHA-256 of its JSON). A shard with
 * a known hash never needs to be downloaded or indexed again.
 */
@Serializable
data class EhFavoritesShardRef(
    val hash: String,
    val minGid: Long = 0,
    val count: Int = 0,
    /** Size of the gzipped file in bytes. */
    val size: Long = 0,
)

/** Contents of a shard file, galleries sorted by gid. */
@Serializable
data class EhFavoritesShardFile(
    val version: Int = 0,
    val galleries: List<EhIndexedGallery> = emptyList(),
)

@Serializable
data class EhIndexedGallery(
    val gid: Long,
    val token: String = "",
    val title: String? = null,
    val titleJpn: String? = null,
    val uploader: String? = null,
    val category: Int = 0,
    val local: Boolean = false,
    /** E-Hentai cloud favorite slot 0..9, -1 if not a cloud favorite. */
    val slot: Int = -1,
) {
    val inCloud: Boolean
        get() = slot in 0..9
}

/** Reading of the EhViewer favorites files; a shard is streamed, never held as one string. */
object EhFavoritesFormat {
    private val json = Json { ignoreUnknownKeys = true }
    private const val BUFFER_SIZE = 64 * 1024

    fun decodeManifest(text: String): EhFavoritesManifest {
        val manifest = json.decodeFromString(EhFavoritesManifest.serializer(), text)
        checkVersion(manifest.version)
        if (!manifest.shards.all { EhFavoritesManifest.isValidHash(it.hash) }) {
            throw LibrarySyncException("The EhViewer favorites list is invalid")
        }
        return manifest
    }

    /**
     * Reads a gzipped shard. The stream is read to its end, so the gzip checksum of a damaged file is checked too.
     */
    fun decodeShard(gzipped: InputStream): EhFavoritesShardFile {
        GZIPInputStream(gzipped.buffered(BUFFER_SIZE), BUFFER_SIZE).use { input ->
            val shard = json.decodeFromStream(EhFavoritesShardFile.serializer(), input)
            val rest = ByteArray(BUFFER_SIZE)
            var read = 0
            while (read >= 0) read = input.read(rest)
            checkVersion(shard.version)
            return shard
        }
    }

    private fun checkVersion(version: Int) {
        if (version > EhFavoritesManifest.CURRENT_VERSION) {
            throw LibrarySyncException("The EhViewer favorites were written by a newer EhViewer version")
        }
    }
}

/** EhViewer favorites matching a manga: by gallery id for E-Hentai sources, else by title. */
data class EhFavoriteMatch(
    val galleries: List<EhIndexedGallery>,
    val byGalleryId: Boolean,
) {
    val local: Boolean
        get() = galleries.any(EhIndexedGallery::local)

    val slots: List<Int>
        get() = galleries.filter(EhIndexedGallery::inCloud).map(EhIndexedGallery::slot).distinct().sorted()
}

/** A lookup [EhFavoritesIndex] has already made, see [EhFavoritesIndex.cached]; [match] is null if none matched. */
class EhFavoriteLookup(val match: EhFavoriteMatch?)
