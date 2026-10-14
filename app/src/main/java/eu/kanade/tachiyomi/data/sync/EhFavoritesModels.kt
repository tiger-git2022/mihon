package eu.kanade.tachiyomi.data.sync

import kotlinx.serialization.Serializable

/**
 * `ehviewer/export/favorites-index.json.gz` written by the EhViewer fork (tiger-git2022/ehviewer-onedrive,
 * docs/onedrive-sync-design.md, section 12) into the same OneDrive app folder: the local favorites and the
 * E-Hentai cloud favorites of EhViewer. Read only here.
 */
@Serializable
data class EhFavoritesIndexFile(
    val version: Int = 0,
    val updatedAt: Long = 0,
    val deviceName: String = "",
    val slotNames: List<String> = emptyList(),
    val galleries: List<EhIndexedGallery> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val PATH = "ehviewer/export/favorites-index.json.gz"
    }
}

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
