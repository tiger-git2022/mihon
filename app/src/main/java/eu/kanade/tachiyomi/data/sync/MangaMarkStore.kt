package eu.kanade.tachiyomi.data.sync

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import kotlin.time.Clock

/**
 * Local copy of the manga marks ([MangaMarksFile]), kept in a file of its own so it stays out of
 * the database (and of upstream schema migrations).
 */
@Inject
@SingleIn(AppScope::class)
class MangaMarkStore(
    context: Context,
) {
    private val file = File(context.filesDir, "tiger/manga-marks.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    private val _marks = MutableStateFlow(MangaMarksFile())
    val marks: StateFlow<MangaMarksFile> = _marks.asStateFlow()

    val index: StateFlow<MangaMarkIndex> = _marks
        .map(::MangaMarkIndex)
        .stateIn(scope, SharingStarted.Eagerly, MangaMarkIndex.EMPTY)

    init {
        scope.launch {
            mutex.withLock {
                _marks.value = read()
                loaded.complete(Unit)
            }
        }
    }

    suspend fun get(): MangaMarksFile {
        loaded.await()
        return _marks.value
    }

    suspend fun update(transform: (MangaMarksFile) -> MangaMarksFile): MangaMarksFile {
        loaded.await()
        return mutex.withLock {
            val updated = transform(_marks.value)
            withContext(Dispatchers.IO) { write(updated) }
            _marks.value = updated
            updated
        }
    }

    suspend fun setRead(sourceId: Long, mangaUrl: String, title: String, marked: Boolean, device: SyncDevice) {
        val key = MangaMarkKeys.key(sourceId, mangaUrl)
        val mark = ReadMark(
            key = key,
            sourceId = sourceId,
            mangaUrl = mangaUrl,
            title = title,
            marked = marked,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
            deviceId = device.id,
            deviceName = device.name,
        )
        update { marks -> marks.copy(readMarks = marks.readMarks.filterNot { it.key == key } + mark) }
    }

    private fun read(): MangaMarksFile {
        if (!file.exists()) return MangaMarksFile()
        return runCatching { json.decodeFromString(MangaMarksFile.serializer(), file.readText()) }
            .onFailure { logcat(LogPriority.ERROR, it) { "Could not read manga marks" } }
            .getOrDefault(MangaMarksFile())
    }

    private fun write(marks: MangaMarksFile) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(MangaMarksFile.serializer(), marks))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
