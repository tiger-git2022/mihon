package eu.kanade.tachiyomi.data.sync

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * "Read before" mark set by the user on an entry (fork-only feature). Independent of the library:
 * it stays after the entry is removed from the library. An unmarked entry keeps its record with
 * [marked] = false, so the change reaches other devices.
 */
@Serializable
data class ReadMark(
    val key: String,
    val sourceId: Long,
    val mangaUrl: String,
    val title: String,
    val marked: Boolean,
    val updatedAt: Long,
    val deviceId: String = "",
    val deviceName: String = "",
)

@Serializable
data class MangaMarksFile(
    @EncodeDefault
    val version: Int = CURRENT_VERSION,
    val readMarks: List<ReadMark> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "mihon-marks.json"
    }
}

data class MangaMarkStatus(
    val read: Boolean = false,
) {
    companion object {
        val NONE = MangaMarkStatus()
        private val READ = MangaMarkStatus(read = true)

        fun of(read: Boolean) = if (read) READ else NONE
    }
}

/** Fast lookups for list items. */
class MangaMarkIndex(file: MangaMarksFile) {
    private val readKeys = file.readMarks.filter(ReadMark::marked).mapTo(HashSet(), ReadMark::key)

    fun status(sourceId: Long, mangaUrl: String): MangaMarkStatus {
        return MangaMarkStatus.of(read = MangaMarkKeys.key(sourceId, mangaUrl) in readKeys)
    }

    companion object {
        val EMPTY = MangaMarkIndex(MangaMarksFile())
    }
}

object MangaMarkKeys {
    // E-Hentai/ExHentai gallery URLs: /g/<gallery id>/<10 hex char token>/
    private val EH_GALLERY_REGEX = Regex("""/g/(\d+)/[0-9a-f]{10}""")

    fun galleryId(mangaUrl: String): Long? {
        return EH_GALLERY_REGEX.find(mangaUrl)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * E-Hentai galleries are keyed by gallery id, so a mark set on E-Hentai also shows on ExHentai
     * and on other E-Hentai sources.
     */
    fun key(sourceId: Long, mangaUrl: String): String {
        return galleryId(mangaUrl)?.let { "eh:$it" } ?: "$sourceId:$mangaUrl"
    }
}

internal object MangaMarksMerger {

    fun merge(local: MangaMarksFile, cloud: MangaMarksFile): MangaMarksFile {
        return MangaMarksFile(
            readMarks = LatestWinsMerger.merge(local.readMarks, cloud.readMarks, ReadMark::key, ReadMark::updatedAt),
        )
    }
}
