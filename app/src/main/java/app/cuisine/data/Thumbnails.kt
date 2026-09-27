package app.cuisine.data

import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.ImageRequest
import coil3.request.Options
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/** A MediaStore-generated thumbnail; far cheaper than decoding the original for grids. */
data class MediaThumb(val uri: Uri, val id: Long, val px: Int)

class MediaThumbFetcher(private val data: MediaThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val signal = CancellationSignal()
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { signal.cancel() }
        try {
            val bitmap = options.context.contentResolver.loadThumbnail(data.uri, Size(data.px, data.px), signal)
            return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
        } finally {
            handle?.dispose()
        }
    }

    class Factory : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbFetcher(data, options)
    }
}

class MediaThumbKeyer : Keyer<MediaThumb> {
    override fun key(data: MediaThumb, options: Options): String = thumbKey(data.id, data.px)
}

private fun thumbKey(id: Long, px: Int) = "thumb:$id:$px"

/** Thumbnail sizes, kept to a few buckets so memory-cache entries get reused across screens. */
object ThumbSize {
    const val Small = 256
    const val Medium = 512
    const val Large = 1024
}

@Composable
fun rememberThumbRequest(media: Media, px: Int = ThumbSize.Small): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(media.id, media.uri, px) {
        ImageRequest.Builder(context)
            .data(MediaThumb(media.uri, media.id, px))
            .memoryCacheKey(thumbKey(media.id, px))
            .size(px)
            .build()
    }
}

/**
 * The full image (or a large video frame), shown over the already cached thumbnail so
 * transitions from grids never flash empty.
 */
@Composable
fun rememberFullRequest(media: Media, maxPx: Int = 2048, placeholderPx: Int = ThumbSize.Small): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(media.id, media.uri, maxPx, placeholderPx) {
        ImageRequest.Builder(context)
            .data(if (media.isVideo) MediaThumb(media.uri, media.id, ThumbSize.Large) else media.uri)
            .placeholderMemoryCacheKey(thumbKey(media.id, placeholderPx))
            .size(maxPx)
            .build()
    }
}
