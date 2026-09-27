package app.cuisine.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.Media
import app.cuisine.data.Post
import app.cuisine.data.plural
import app.cuisine.data.rememberFullRequest
import app.cuisine.data.timeAgo
import app.cuisine.data.timeAgoShort
import app.cuisine.ui.components.Avatar
import app.cuisine.ui.components.BindPlayer
import app.cuisine.ui.components.HeartBurst
import app.cuisine.ui.components.RingState
import app.cuisine.ui.components.RingedAvatar
import app.cuisine.ui.components.VideoSurface
import app.cuisine.ui.components.heroSource
import app.cuisine.ui.components.mediaHeroKey
import app.cuisine.ui.theme.heart
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Immutable
class FeedPlayback(val player: ExoPlayer, val muted: Boolean, val onToggleMute: () -> Unit)

@Composable
fun PostCard(
    post: Post,
    liked: Boolean,
    saved: Boolean,
    noteCount: Int,
    authorRing: RingState,
    playback: FeedPlayback?,
    onLike: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onNotes: () -> Unit,
    onAuthor: () -> Unit,
    onOpen: (Int) -> Unit,
    onMore: () -> Unit,
    onEditCaption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { post.media.size }
    var burst by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        PostHeader(post, authorRing, onAuthor, onMore)
        PostMedia(
            post = post,
            pagerState = pagerState,
            burst = burst,
            playback = playback,
            onDoubleTap = {
                burst++
                if (!liked) onLike(true)
            },
            onTap = onOpen,
        )
        PostActions(
            liked = liked,
            saved = saved,
            pagerState = pagerState,
            count = post.media.size,
            onLike = { onLike(!liked) },
            onNotes = onNotes,
            onShare = onShare,
            onSave = onSave,
        )
        PostFooter(post, liked, noteCount, onNotes, onEditCaption)
    }
}

@Composable
private fun PostHeader(post: Post, ring: RingState, onAuthor: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (post.isCollab) {
            CollabAvatars(
                first = post.authors[0],
                second = post.authors[1],
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAuthor),
            )
        } else {
            RingedAvatar(
                author = post.author,
                size = 42.dp,
                ring = ring,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAuthor),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAuthor),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (post.authors.size) {
                        1 -> post.author.handle
                        2 -> "${post.authors[0].handle} and ${post.authors[1].handle}"
                        else -> "${post.authors[0].handle} and ${post.authors.size - 1} others"
                    },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "  ·  ${timeAgoShort(post.takenAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = post.place,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMore, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
        }
    }
}

/** Instagram's collab mark: two overlapping avatars in the space of one. */
@Composable
private fun CollabAvatars(first: Author, second: Author, modifier: Modifier = Modifier) {
    Box(modifier.size(42.dp)) {
        Avatar(first, 30.dp, Modifier.align(Alignment.TopStart))
        Avatar(
            second,
            30.dp,
            Modifier
                .align(Alignment.BottomEnd)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}

@Composable
private fun PostMedia(
    post: Post,
    pagerState: PagerState,
    burst: Int,
    playback: FeedPlayback?,
    onDoubleTap: () -> Unit,
    onTap: (Int) -> Unit,
) {
    val ratio = post.media.first().aspectRatio.coerceIn(0.8f, 1.91f)
    Box(
        Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        HorizontalPager(
            state = pagerState,
            key = { post.media[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val media = post.media[page]
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(post.id, page) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() }, onTap = { onTap(page) })
                    },
            ) {
                val active = playback != null && media.isVideo && page == pagerState.currentPage
                if (active) {
                    BindPlayer(playback.player, media, play = true)
                    VideoSurface(player = playback.player, modifier = Modifier.fillMaxSize()) {
                        Poster(media)
                    }
                } else {
                    Poster(media, shared = page == pagerState.currentPage)
                }
                if (media.isVideo) {
                    VideoCorner(
                        playing = active,
                        muted = playback?.muted ?: true,
                        onToggleMute = { playback?.onToggleMute?.invoke() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    )
                }
            }
        }
        if (post.media.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1}/${post.media.size}",
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        HeartBurst(trigger = burst, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Poster(media: Media, shared: Boolean = false) {
    AsyncImage(
        model = rememberFullRequest(media, maxPx = 1440),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .heroSource(mediaHeroKey(media.id), corner = 28.dp, enabled = shared),
    )
}

@Composable
private fun VideoCorner(playing: Boolean, muted: Boolean, onToggleMute: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = playing, onClick = onToggleMute),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                when {
                    !playing -> R.drawable.ic_play_arrow_filled
                    muted -> R.drawable.ic_volume_off_filled
                    else -> R.drawable.ic_volume_up_filled
                },
            ),
            contentDescription = if (muted) "Unmute" else "Mute",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PostActions(
    liked: Boolean,
    saved: Boolean,
    pagerState: PagerState,
    count: Int,
    onLike: () -> Unit,
    onNotes: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(liked = liked, onClick = onLike)
            IconButton(onClick = onNotes, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_chat_bubble), contentDescription = "Notes", modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = onShare, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(R.drawable.ic_send),
                    contentDescription = "Share",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = -24f },
                )
            }
            Spacer(Modifier.weight(1f))
            SaveButton(saved = saved, onClick = onSave)
        }
        if (count > 1) {
            PagerDots(count = count, pagerState = pagerState, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun LikeButton(
    liked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    idleTint: Color = LocalContentColor.current,
) {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        scale.snapTo(if (liked) 0.5f else 0.8f)
        scale.animateTo(1f, spring(dampingRatio = if (liked) 0.3f else 0.6f, stiffness = 520f))
    }
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
        Icon(
            painter = painterResource(if (liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
            contentDescription = if (liked) "Unlike" else "Like",
            tint = if (liked) MaterialTheme.colorScheme.heart else idleTint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

@Composable
private fun SaveButton(saved: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(saved) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        scale.snapTo(0.6f)
        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
    }
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
            contentDescription = if (saved) "Remove from saved" else "Save",
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

/** The active page stretches into a pill; the rest stay as dots. */
@Composable
fun PagerDots(count: Int, pagerState: PagerState, modifier: Modifier = Modifier, activeColor: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = pagerState.currentPage == index
            val width by animateDpAsState(
                targetValue = if (selected) 18.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
                label = "dotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) activeColor else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(
                Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun PostFooter(post: Post, liked: Boolean, noteCount: Int, onNotes: () -> Unit, onEditCaption: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (liked) {
            Text(
                text = "Liked by you",
                style = MaterialTheme.typography.labelLargeEmphasized,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Caption(
            handle = post.author.handle,
            text = post.caption.ifBlank { autoCaption(post) },
            muted = post.caption.isBlank(),
            onLongPress = onEditCaption,
        )
        if (noteCount > 0) {
            Text(
                text = if (noteCount == 1) "View 1 note" else "View all $noteCount notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onNotes),
            )
        }
        Text(
            text = timeAgo(post.takenAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Caption(handle: String, text: String, muted: Boolean, onLongPress: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val body = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = body)) { append(handle) }
            append("  ")
            withStyle(SpanStyle(color = if (muted) soft else body)) { append(text) }
        },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .animateContentSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { expanded = !expanded }, onLongPress = { onLongPress() })
            },
    )
}

/** A light, human default for posts the user hasn't captioned: "Saturday evening · 4 photos". */
fun autoCaption(post: Post): String {
    val zoned = Instant.ofEpochMilli(post.takenAt).atZone(ZoneId.systemDefault())
    val part = when (zoned.hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }
    val today = LocalDate.now()
    val date = zoned.toLocalDate()
    val day = when {
        date == today -> "This $part"
        date == today.minusDays(1) -> if (part == "night") "Last night" else "Yesterday $part"
        today.toEpochDay() - date.toEpochDay() < 7 ->
            date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " $part"
        date.year == today.year ->
            "A $part in " + date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else -> "A $part in " + date.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${date.year}"
    }
    val photos = post.media.count { !it.isVideo }
    val videos = post.media.size - photos
    val count = when {
        post.media.size == 1 -> null
        videos == 0 -> plural(photos, "photo")
        photos == 0 -> plural(videos, "video")
        else -> "${plural(photos, "photo")}, ${plural(videos, "video")}"
    }
    return if (count == null) day else "$day · $count"
}
