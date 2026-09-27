package app.cuisine.ui.reels

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import app.cuisine.ui.components.rememberExpandedSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import app.cuisine.R
import app.cuisine.data.Media
import app.cuisine.data.Reel
import app.cuisine.data.ThumbSize
import app.cuisine.data.formatDuration
import app.cuisine.data.rememberFullRequest
import app.cuisine.data.rememberThumbRequest
import app.cuisine.data.timeAgoShort
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.Route
import app.cuisine.ui.ViewerSource
import app.cuisine.ui.components.Avatar
import app.cuisine.ui.components.BindPlayer
import app.cuisine.ui.components.EmptyState
import app.cuisine.ui.components.HeartBurst
import app.cuisine.ui.components.LoadingState
import app.cuisine.ui.components.OnReselect
import app.cuisine.ui.components.SegmentedAction
import app.cuisine.ui.components.SegmentedList
import app.cuisine.ui.components.VideoSurface
import app.cuisine.ui.components.rememberPlayer
import app.cuisine.ui.components.shareMedia
import app.cuisine.ui.feed.CaptionSheet
import app.cuisine.ui.feed.LikeButton
import app.cuisine.ui.feed.NotesSheet
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ReelsScreen(vm: CuisineViewModel, navigate: (Route) -> Unit, reselect: Int) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val reels = feed?.reels
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            reels == null -> LoadingState()
            reels.isEmpty() -> MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    onSurface = Color.White,
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                EmptyState(
                    icon = R.drawable.ic_movie_filled,
                    title = "No reels yet",
                    body = "Videos you record show up here as reels, ready to watch back to back.",
                )
            }
            else -> ReelsPager(vm, reels, navigate, reselect)
        }
    }
}

@Composable
private fun ReelsPager(vm: CuisineViewModel, reels: List<Reel>, navigate: (Route) -> Unit, reselect: Int) {
    val social by vm.social.collectAsStateWithLifecycle()
    val me = vm.feed.collectAsStateWithLifecycle().value?.me
    val context = LocalContext.current
    val pager = rememberPagerState { reels.size }
    OnReselect(reselect) { pager.animateScrollToPage(0) }
    val player = rememberPlayer(loop = true)
    var muted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    var notesFor by remember { mutableStateOf<Reel?>(null) }
    var captionFor by remember { mutableStateOf<Reel?>(null) }
    var optionsFor by remember { mutableStateOf<Reel?>(null) }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pager,
            key = { reels[it].id },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val reel = reels[page]
            ReelPage(
                reel = reel,
                player = player,
                active = page == pager.settledPage,
                liked = reel.id in social.likes,
                noteCount = social.notes[reel.id]?.size ?: 0,
                onLike = { vm.toggleLike(reel.id, it) },
                onNotes = { notesFor = reel },
                onShare = { shareMedia(context, listOf(reel.media)) },
                onMore = { optionsFor = reel },
                onAuthor = { navigate(Route.Profile(reel.author.id)) },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 6.dp),
        ) {
            Text("Reels", style = MaterialTheme.typography.headlineSmallEmphasized, color = Color.White)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { muted = !muted }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(if (muted) R.drawable.ic_volume_off_filled else R.drawable.ic_volume_up_filled),
                    contentDescription = if (muted) "Unmute" else "Mute",
                    tint = Color.White,
                )
            }
        }
    }

    notesFor?.let { reel ->
        if (me != null) {
            NotesSheet(
                me = me,
                notes = social.notes[reel.id].orEmpty(),
                onAdd = { vm.addNote(reel.id, it) },
                onDelete = { vm.deleteNote(reel.id, it.id) },
                onDismiss = { notesFor = null },
            )
        }
    }
    captionFor?.let { reel ->
        CaptionSheet(initial = reel.caption, onDismiss = { captionFor = null }, onSave = { vm.setCaption(reel.id, it) })
    }
    optionsFor?.let { reel ->
        ReelOptionsSheet(
            onDismiss = { optionsFor = null },
            onOpen = { navigate(Route.Viewer(ViewerSource.Items(listOf(reel.media.id)), reel.media.id)) },
            onCaption = { captionFor = reel },
            onHide = { vm.hideFromFeed(listOf(reel.media)) },
        )
    }
}

@Composable
private fun ReelPage(
    reel: Reel,
    player: ExoPlayer,
    active: Boolean,
    liked: Boolean,
    noteCount: Int,
    onLike: (Boolean) -> Unit,
    onNotes: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    onAuthor: () -> Unit,
) {
    val media = reel.media
    var burst by remember { mutableIntStateOf(0) }
    var paused by rememberSaveable(reel.id) { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) paused = false }
    val scale = if (media.aspectRatio < 0.75f) ContentScale.Crop else ContentScale.Fit
    if (active) BindPlayer(player, media, play = !paused)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(reel.id) {
                detectTapGestures(
                    onTap = { paused = !paused },
                    onDoubleTap = {
                        burst++
                        if (!liked) onLike(true)
                    },
                )
            },
    ) {
        val poster: @Composable () -> Unit = {
            AsyncImage(
                model = rememberFullRequest(media, maxPx = 1440),
                contentDescription = null,
                contentScale = scale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (active) VideoSurface(player = player, modifier = Modifier.fillMaxSize(), contentScale = scale, poster = poster) else poster()

        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )

        AnimatedVisibility(
            visible = paused,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(targetScale = 1.3f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_play_arrow_filled),
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
        HeartBurst(trigger = burst, modifier = Modifier.align(Alignment.Center), size = 128.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 34.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LikeButton(liked = liked, onClick = { onLike(!liked) }, iconSize = 30.dp, idleTint = Color.White)
                RailLabel(if (liked) "Liked" else "Like")
            }
            RailAction(R.drawable.ic_chat_bubble, if (noteCount > 0) "$noteCount" else "Notes", onNotes)
            RailAction(R.drawable.ic_send, "Share", onShare, rotation = -24f)
            RailAction(R.drawable.ic_more_vert, null, onMore)
            Spacer(Modifier.height(6.dp))
            SpinningDisc(media = media, spinning = active && !paused)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, bottom = 34.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAuthor,
                ),
            ) {
                Avatar(reel.author, 34.dp, Modifier.border(1.5.dp, Color.White, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when (reel.authors.size) {
                        1 -> reel.author.handle
                        2 -> "${reel.authors[0].handle} and ${reel.authors[1].handle}"
                        else -> "${reel.author.handle} and ${reel.authors.size - 1} others"
                    },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        reel.authors.size > 1 -> "Collab"
                        reel.author.isMe -> "Yours"
                        else -> "Shared"
                    },
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = Color.White,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = reel.caption.ifBlank { "${timeAgoShort(media.takenAt)} · ${formatDuration(media.durationMs)} · ${media.bucketName.ifBlank { "Library" }}" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Original audio · ${reel.author.handle}      ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .width(180.dp)
                        .basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }

        if (active) {
            ReelProgress(
                player = player,
                playing = !paused,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun RailAction(@DrawableRes icon: Int, label: String?, onClick: () -> Unit, rotation: Float = 0f) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        if (label != null) RailLabel(label)
    }
}

@Composable
private fun RailLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMediumEmphasized, color = Color.White)
}

/** The "audio" disc: the clip itself on a slowly turning cookie. */
@Composable
private fun SpinningDisc(media: Media, spinning: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        while (spinning) {
            rotation.animateTo(rotation.value + 360f, tween(7000, easing = LinearEasing))
        }
    }
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(shape)
            .background(Color.DarkGray)
            .border(2.dp, Color.White, shape),
    ) {
        AsyncImage(
            model = rememberThumbRequest(media, ThumbSize.Small),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Material's wavy progress: the wave rolls while playing and settles flat when paused. */
@Composable
private fun ReelProgress(player: ExoPlayer, playing: Boolean, modifier: Modifier = Modifier) {
    val state = rememberProgressStateWithTickInterval(player, tickIntervalMs = 60)
    val amplitude = remember { Animatable(1f) }
    LaunchedEffect(playing) { amplitude.animateTo(if (playing) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
    LinearWavyProgressIndicator(
        progress = {
            val duration = state.durationMs
            if (duration > 0) (state.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        },
        amplitude = { amplitude.value },
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.28f),
        modifier = modifier,
    )
}

@Composable
private fun ReelOptionsSheet(onDismiss: () -> Unit, onOpen: () -> Unit, onCaption: () -> Unit, onHide: () -> Unit) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    fun pick(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SegmentedList(
            actions = listOf(
                SegmentedAction(R.drawable.ic_open_in_new, "Open in library") { pick(onOpen) },
                SegmentedAction(R.drawable.ic_edit, "Edit caption") { pick(onCaption) },
                SegmentedAction(R.drawable.ic_visibility_off, "Hide from reels", "Still kept in your library") { pick(onHide) },
            ),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        )
    }
}
