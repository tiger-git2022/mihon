package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import tachiyomi.domain.manga.model.Manga

@Serializable
data class LibrarySyncFile(
    @EncodeDefault
    val version: Int = CURRENT_VERSION,
    val updatedAt: Long = 0L,
    val entries: List<LibrarySyncEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class LibrarySyncEntry(
    val sourceId: Long,
    val mangaUrl: String,
    val favorite: Boolean,
    val modifiedAt: Long,
    val manga: LibrarySyncManga,
) {
    val key: LibrarySyncKey
        get() = LibrarySyncKey(sourceId, mangaUrl)
}

data class LibrarySyncKey(
    val sourceId: Long,
    val mangaUrl: String,
)

@Serializable
data class LibrarySyncManga(
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long = 0L,
    val thumbnailUrl: String? = null,
    val updateStrategy: String = UpdateStrategy.ALWAYS_UPDATE.name,
    val initialized: Boolean = false,
) {
    fun toManga(sourceId: Long, mangaUrl: String, now: Long): Manga {
        val strategy = runCatching { enumValueOf<UpdateStrategy>(updateStrategy) }
            .getOrDefault(UpdateStrategy.ALWAYS_UPDATE)

        return Manga.create().copy(
            source = sourceId,
            url = mangaUrl,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status,
            thumbnailUrl = thumbnailUrl,
            updateStrategy = strategy,
            initialized = initialized,
            favorite = true,
            dateAdded = now,
        )
    }

    companion object {
        fun from(manga: Manga) = LibrarySyncManga(
            title = manga.title,
            artist = manga.artist,
            author = manga.author,
            description = manga.description,
            genre = manga.genre,
            status = manga.status,
            thumbnailUrl = manga.thumbnailUrl,
            updateStrategy = manga.updateStrategy.name,
            initialized = manga.initialized,
        )
    }
}
