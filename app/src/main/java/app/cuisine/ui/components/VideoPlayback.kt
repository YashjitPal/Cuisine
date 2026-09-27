package app.cuisine.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import app.cuisine.data.Media

/** An ExoPlayer tied to the composition; released when it leaves. */
@Composable
fun rememberPlayer(loop: Boolean = true, muted: Boolean = false): ExoPlayer {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                !muted,
            )
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** True while the host activity is in the foreground. */
@Composable
fun isResumed(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * Loads [media] into a shared [player] while this is composed and keeps playback in step with
 * [play]. Leaving the composition pauses, so whichever surface binds next takes over cleanly.
 */
@Composable
fun BindPlayer(player: ExoPlayer, media: Media?, play: Boolean) {
    val resumed = isResumed()
    DisposableEffect(player, media?.id) {
        if (media != null) {
            player.setMediaItem(MediaItem.fromUri(media.uri))
            player.prepare()
        }
        onDispose { player.pause() }
    }
    LaunchedEffect(player, media?.id, play, resumed) {
        player.playWhenReady = play && resumed && media != null
    }
}

@Composable
fun rememberIsPlaying(player: Player?): State<Boolean> {
    val state = remember(player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.value = isPlaying
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return state
}

/** True once the current item has played to its end (only meaningful without looping). */
@Composable
fun rememberPlaybackEnded(player: Player): State<Boolean> {
    val state = remember(player) { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.value = playbackState == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return state
}

/** A texture-backed video surface so it clips, fades and animates like any other composable. */
@Composable
fun VideoSurface(
    player: Player?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    poster: @Composable () -> Unit,
) {
    ContentFrame(
        player = player,
        modifier = modifier,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = contentScale,
        keepContentOnReset = false,
        shutter = poster,
    )
}
