package eu.kanade.tachiyomi.data.sync

import java.io.File

/**
 * Fork: a sync file as last synced with OneDrive ([fileName] in the app folder), with the eTag OneDrive gave it.
 *
 * While the file on OneDrive still has that eTag, the sync takes this copy instead of downloading it, and when the
 * merge with this device's records gives the same records it uploads nothing: a sync without changes costs one listing
 * of the app folder instead of a download and an upload of every file. For the library the copy is also the baseline
 * of the three-way merge (the state both sides last agreed on).
 */
class SyncFileCache(dir: File, val fileName: String) {
    private val file = File(dir, fileName)
    private val eTagFile = File(dir, "$fileName.etag")

    /** The copy, null if there is none. */
    fun contents(): String? = runCatching { file.takeIf(File::exists)?.readText() }.getOrNull()

    fun eTag(): String? = runCatching { eTagFile.takeIf(File::exists)?.readText() }
        .getOrNull()
        ?.takeIf(String::isNotBlank)

    /** The copy, if the file on OneDrive still has the eTag it had when it was saved ([cloudETag] from a listing). */
    fun contentsIfCurrent(cloudETag: String?): String? {
        if (cloudETag == null || eTag() != cloudETag) return null
        return contents()
    }

    /**
     * Keeps [contents] as the copy of the file on OneDrive, whose eTag is [eTag] (null if unknown: the next sync
     * downloads the file again). The old eTag goes first, so an interrupted save never pairs it with other contents.
     */
    fun save(contents: String, eTag: String?) {
        eTagFile.delete()
        file.parentFile?.mkdirs()
        writeAtomically(file, contents)
        if (eTag != null) writeAtomically(eTagFile, eTag)
    }

    /** The next sync downloads the file again, the copy stays the baseline. */
    fun forgetETag() {
        eTagFile.delete()
    }

    fun clear() {
        eTagFile.delete()
        file.delete()
    }

    private fun writeAtomically(target: File, text: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw java.io.IOException("Could not write ${target.name}")
        }
    }
}
