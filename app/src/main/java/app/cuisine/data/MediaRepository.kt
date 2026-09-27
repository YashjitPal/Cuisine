package app.cuisine.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads photos and videos from MediaStore and keeps them fresh while the app is alive. */
class MediaRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _media = MutableStateFlow<List<Media>?>(null)

    /** Newest first; `null` until the first successful query. */
    val media: StateFlow<List<Media>?> = _media.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var observer: ContentObserver? = null

    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            refreshRequests.debounce(350).collectLatest {
                _media.value = runCatching { query() }.getOrElse { _media.value ?: emptyList() }
            }
        }
    }

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        _media.value = runCatching { query() }.getOrElse { _media.value ?: emptyList() }
    }

    fun startObserving() {
        if (observer != null) return
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }.also {
            resolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, it)
        }
    }

    /**
     * Moves items to the system trash. Returns an [IntentSender] when the user has to confirm,
     * which is always the case on Android 11+ for media the app doesn't own.
     */
    fun deleteRequest(items: List<Media>): IntentSender? {
        if (items.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createTrashRequest(resolver, items.map { it.uri }, true).intentSender
        }
        for (item in items) {
            try {
                resolver.delete(item.uri, null, null)
            } catch (e: RecoverableSecurityException) {
                return e.userAction.actionIntent.intentSender
            }
        }
        refresh()
        return null
    }

    private fun query(): List<Media> {
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            MediaColumns.DISPLAY_NAME,
            MediaColumns.MIME_TYPE,
            MediaColumns.WIDTH,
            MediaColumns.HEIGHT,
            MediaColumns.ORIENTATION,
            MediaColumns.DATE_TAKEN,
            MediaColumns.DATE_MODIFIED,
            MediaColumns.DATE_ADDED,
            MediaColumns.DURATION,
            MediaColumns.SIZE,
            MediaColumns.BUCKET_ID,
            MediaColumns.BUCKET_DISPLAY_NAME,
            MediaColumns.RELATIVE_PATH,
        )
        val selection = "${FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            FileColumns.MEDIA_TYPE_IMAGE.toString(),
            FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val out = ArrayList<Media>()
        resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            selection,
            args,
            "${MediaColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(FileColumns._ID)
            val type = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val name = c.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
            val mime = c.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
            val width = c.getColumnIndexOrThrow(MediaColumns.WIDTH)
            val height = c.getColumnIndexOrThrow(MediaColumns.HEIGHT)
            val orientation = c.getColumnIndexOrThrow(MediaColumns.ORIENTATION)
            val taken = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
            val modified = c.getColumnIndexOrThrow(MediaColumns.DATE_MODIFIED)
            val added = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
            val duration = c.getColumnIndexOrThrow(MediaColumns.DURATION)
            val size = c.getColumnIndexOrThrow(MediaColumns.SIZE)
            val bucketId = c.getColumnIndexOrThrow(MediaColumns.BUCKET_ID)
            val bucketName = c.getColumnIndexOrThrow(MediaColumns.BUCKET_DISPLAY_NAME)
            val path = c.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)
            while (c.moveToNext()) {
                val mediaId = c.getLong(id)
                val isVideo = c.getInt(type) == FileColumns.MEDIA_TYPE_VIDEO
                var w = c.getInt(width)
                var h = c.getInt(height)
                val rotation = c.getInt(orientation)
                if (rotation == 90 || rotation == 270) {
                    val t = w
                    w = h
                    h = t
                }
                val addedAt = c.getLong(added) * 1000
                val takenAt = c.getLong(taken).takeIf { it > 0 }
                    ?: (c.getLong(modified).takeIf { it > 0 }?.times(1000) ?: addedAt)
                val collection = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                out += Media(
                    id = mediaId,
                    uri = ContentUris.withAppendedId(collection, mediaId),
                    isVideo = isVideo,
                    name = c.getString(name).orEmpty(),
                    mimeType = c.getString(mime).orEmpty(),
                    width = w,
                    height = h,
                    takenAt = takenAt,
                    addedAt = addedAt.takeIf { it > 0 } ?: takenAt,
                    durationMs = c.getLong(duration),
                    sizeBytes = c.getLong(size),
                    bucketId = c.getLong(bucketId),
                    bucketName = c.getString(bucketName).orEmpty(),
                    relativePath = c.getString(path).orEmpty(),
                )
            }
        }
        out.sortByDescending { it.takenAt }
        return out
    }
}
