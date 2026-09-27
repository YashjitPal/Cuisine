package app.cuisine.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import app.cuisine.data.FaceBox
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter

/** A face found in a photo: where it is, and the embedding that identifies whose it is. */
class DetectedFace(val box: FaceBox, val embedding: FloatArray)

/**
 * Finds faces with ML Kit and turns each into a 192-dimension MobileFaceNet embedding. Two faces
 * of the same person have embeddings pointing the same way (high cosine similarity).
 */
class FaceEngine(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Mutex()

    private val detector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(MIN_FACE)
                .build(),
        )
    }

    private val interpreter: Interpreter by lazy {
        val fd = appContext.assets.openFd(MODEL)
        val model = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        Interpreter(model, Interpreter.Options().setNumThreads(2))
    }

    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(SIZE * SIZE)
    private val aligned = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Every usable face in [bitmap]. Throws if the detector isn't available on this device yet. */
    suspend fun analyze(bitmap: Bitmap): List<DetectedFace> {
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        if (faces.isEmpty()) return emptyList()
        return lock.withLock { faces.mapNotNull { embed(bitmap, it) } }
    }

    private fun embed(bitmap: Bitmap, face: Face): DetectedFace? {
        val box = face.boundingBox
        if (box.width() < MIN_PIXELS || abs(face.headEulerAngleY) > MAX_YAW) return null
        val eyes = listOfNotNull(face.getLandmark(FaceLandmark.LEFT_EYE), face.getLandmark(FaceLandmark.RIGHT_EYE))
            .map { it.position }.sortedBy { it.x }
        val mouth = listOfNotNull(face.getLandmark(FaceLandmark.MOUTH_LEFT), face.getLandmark(FaceLandmark.MOUTH_RIGHT))
            .map { it.position }.sortedBy { it.x }
        if (eyes.size < 2 || mouth.size < 2) return null

        val matrix = similarity(listOf(eyes[0], eyes[1], mouth[0], mouth[1]), TEMPLATE)
        val forward = run(bitmap, matrix, mirror = false)
        val mirrored = run(bitmap, matrix, mirror = true)
        val embedding = FloatArray(DIM) { forward[it] + mirrored[it] }.normalized()

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return DetectedFace(
            box = FaceBox(
                left = (box.left / w).coerceIn(0f, 1f),
                top = (box.top / h).coerceIn(0f, 1f),
                right = (box.right / w).coerceIn(0f, 1f),
                bottom = (box.bottom / h).coerceIn(0f, 1f),
            ),
            embedding = embedding,
        )
    }

    private fun run(bitmap: Bitmap, toTemplate: Matrix, mirror: Boolean): FloatArray {
        val canvas = Canvas(aligned)
        canvas.drawColor(android.graphics.Color.BLACK)
        val m = Matrix(toTemplate)
        if (mirror) m.postScale(-1f, 1f, SIZE / 2f, SIZE / 2f)
        canvas.drawBitmap(bitmap, m, paint)
        aligned.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p shr 8 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p and 0xFF) - 127.5f) / 128f)
        }
        input.rewind()
        val output = Array(1) { FloatArray(DIM) }
        interpreter.run(input, output)
        return output[0].normalized()
    }

    companion object {
        private const val MODEL = "mobilefacenet.tflite"
        const val DIM = 192
        private const val SIZE = 112
        private const val MIN_FACE = 0.06f
        private const val MIN_PIXELS = 36
        private const val MAX_YAW = 42f

        /** Eye and mouth-corner positions of the standard 112x112 ArcFace alignment, left to right. */
        private val TEMPLATE = listOf(
            PointF(38.2946f, 51.6963f),
            PointF(73.5318f, 51.5014f),
            PointF(41.5493f, 92.3655f),
            PointF(70.7299f, 92.2041f),
        )
    }
}

/** Least-squares rotation, uniform scale and translation taking [src] onto [dst]. */
private fun similarity(src: List<PointF>, dst: List<PointF>): Matrix {
    val n = src.size
    val sx = src.sumOf { it.x.toDouble() } / n
    val sy = src.sumOf { it.y.toDouble() } / n
    val dx = dst.sumOf { it.x.toDouble() } / n
    val dy = dst.sumOf { it.y.toDouble() } / n
    var dot = 0.0
    var cross = 0.0
    var norm = 0.0
    for (i in 0 until n) {
        val ax = src[i].x - sx
        val ay = src[i].y - sy
        val bx = dst[i].x - dx
        val by = dst[i].y - dy
        dot += ax * bx + ay * by
        cross += ax * by - ay * bx
        norm += ax * ax + ay * ay
    }
    val a = (dot / norm).toFloat()
    val b = (cross / norm).toFloat()
    val tx = (dx - (a * sx - b * sy)).toFloat()
    val ty = (dy - (b * sx + a * sy)).toFloat()
    return Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }
}

fun FloatArray.normalized(): FloatArray {
    var sum = 0f
    for (v in this) sum += v * v
    val len = sqrt(sum).takeIf { it > 0f } ?: return this
    return FloatArray(size) { this[it] / len }
}

/** Cosine similarity of two normalized embeddings. */
infix fun FloatArray.similarity(other: FloatArray): Float {
    var sum = 0f
    for (i in indices) sum += this[i] * other[i]
    return sum
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
