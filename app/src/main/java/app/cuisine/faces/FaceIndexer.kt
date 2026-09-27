package app.cuisine.faces

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.runtime.Immutable
import app.cuisine.data.FaceBox
import app.cuisine.data.Media
import com.google.mlkit.common.MlKitException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Faces found in each media item; an empty list means it was looked at and had none. */
@Immutable
class FaceIndex(val faces: Map<Long, List<DetectedFace>>) {
    companion object {
        val Empty = FaceIndex(emptyMap())
    }
}

@Immutable
data class IndexProgress(
    val done: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
    /** Google Play services hasn't got the face detector yet (or can't). */
    val waitingForDetector: Boolean = false,
)

/**
 * Looks for faces across the whole library in the background, newest additions first, and keeps
 * the results in app storage that isn't backed up (it can always be rebuilt).
 */
class FaceIndexer(context: Context, private val library: StateFlow<List<Media>?>, private val engine: FaceEngine) {
    private val resolver = context.applicationContext.contentResolver
    private val file = File(context.noBackupFilesDir, "faces.bin")
    // One at a time: the index map is only touched from here.
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)
    private val _index = MutableStateFlow(FaceIndex.Empty)
    val index: StateFlow<FaceIndex> = _index.asStateFlow()
    private val _progress = MutableStateFlow(IndexProgress())
    val progress: StateFlow<IndexProgress> = _progress.asStateFlow()
    private val working = HashMap<Long, List<DetectedFace>>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            runCatching { read() }.getOrNull()?.let(working::putAll)
            publish()
            library.filterNotNull().collectLatest { all -> indexAll(all) }
        }
    }

    /** Faces in [media], from the index or looked up right away. */
    suspend fun facesOf(media: List<Media>): Map<Long, List<DetectedFace>> = withContext(serial) {
        media.associate { m ->
            val known = working[m.id]
            m.id to (known ?: lookUp(m))
        }
    }

    /** Analyses [media] now; if the detector isn't ready, reports no faces without remembering that. */
    private suspend fun lookUp(media: Media): List<DetectedFace> = try {
        analyze(media).also { working[media.id] = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun indexAll(all: List<Media>) {
        val ids = all.mapTo(HashSet()) { it.id }
        if (working.keys.retainAll(ids)) save()
        val pending = all.filter { it.id !in working }.sortedByDescending { it.addedAt }
        var done = all.size - pending.size
        _progress.value = IndexProgress(done, all.size, running = pending.isNotEmpty())
        var unsaved = 0
        try {
            for (m in pending) {
                working[m.id] = analyzeWhenReady(m, done, all.size)
                done++
                unsaved++
                _progress.value = IndexProgress(done, all.size, running = done < all.size)
                if (unsaved >= BATCH) {
                    publish()
                    save()
                    unsaved = 0
                }
            }
        } finally {
            if (unsaved > 0) {
                publish()
                save()
            }
        }
        _progress.value = IndexProgress(done, all.size, running = false)
    }

    private suspend fun analyzeWhenReady(media: Media, done: Int, total: Int): List<DetectedFace> {
        while (true) {
            try {
                return analyze(media)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MlKitException) {
                if (e.errorCode != MlKitException.UNAVAILABLE) return emptyList()
                // Play services is still fetching the detector; try again shortly.
                _progress.value = IndexProgress(done, total, running = true, waitingForDetector = true)
                delay(RETRY_MS)
            } catch (e: Exception) {
                return emptyList()
            }
        }
    }

    private suspend fun analyze(media: Media): List<DetectedFace> {
        val bitmap = runCatching { resolver.loadThumbnail(media.uri, Size(EDGE, EDGE), null) }.getOrNull() ?: return emptyList()
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        return try {
            engine.analyze(soft)
        } finally {
            if (soft !== bitmap) soft.recycle()
            bitmap.recycle()
        }
    }

    private fun publish() {
        _index.value = FaceIndex(HashMap(working))
    }

    private fun save() {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(FaceEngine.DIM)
                out.writeInt(working.size)
                for ((id, faces) in working) {
                    out.writeLong(id)
                    out.writeInt(faces.size)
                    for (f in faces) {
                        out.writeFloat(f.box.left)
                        out.writeFloat(f.box.top)
                        out.writeFloat(f.box.right)
                        out.writeFloat(f.box.bottom)
                        for (v in f.embedding) out.writeFloat(v)
                    }
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun read(): Map<Long, List<DetectedFace>>? {
        if (!file.exists()) return null
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != FaceEngine.DIM) return null
            val count = input.readInt()
            val out = HashMap<Long, List<DetectedFace>>(count)
            repeat(count) {
                val id = input.readLong()
                val n = input.readInt()
                out[id] = List(n) {
                    val box = FaceBox(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())
                    DetectedFace(box, FloatArray(FaceEngine.DIM) { input.readFloat() })
                }
            }
            return out
        }
    }

    private companion object {
        const val MAGIC = 0x43464931 // "CFI1"
        const val EDGE = 1024
        const val BATCH = 12
        const val RETRY_MS = 15_000L
    }
}
