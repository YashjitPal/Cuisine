package app.cuisine

import android.app.Application
import app.cuisine.data.MediaRepository
import app.cuisine.data.MediaThumbFetcher
import app.cuisine.data.MediaThumbKeyer
import app.cuisine.data.SocialStore
import app.cuisine.faces.FaceEngine
import app.cuisine.faces.FaceIndexer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder

class CuisineApplication : Application(), SingletonImageLoader.Factory {
    lateinit var media: MediaRepository
        private set
    lateinit var social: SocialStore
        private set
    lateinit var faces: FaceIndexer
        private set

    override fun onCreate() {
        super.onCreate()
        media = MediaRepository(this)
        social = SocialStore(this)
        faces = FaceIndexer(this, media.media, FaceEngine(this))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbFetcher.Factory())
                add(MediaThumbKeyer())
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(160)
            .build()
}
