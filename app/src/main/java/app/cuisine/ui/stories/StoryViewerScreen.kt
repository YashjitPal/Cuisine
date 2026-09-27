package app.cuisine.ui.stories

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import app.cuisine.R
import app.cuisine.data.Feed
import app.cuisine.data.FeedBuilder
import app.cuisine.data.Library
import app.cuisine.data.Media
import app.cuisine.data.Story
import app.cuisine.data.StoryKind
import app.cuisine.data.ThumbSize
import app.cuisine.data.dayTitle
import app.cuisine.data.rememberFullRequest
import app.cuisine.data.rememberThumbRequest
import app.cuisine.data.timeAgoShort
import app.cuisine.data.yearsAgo
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.ExitOverlayImmediately
import app.cuisine.ui.LocalOverlayExit
import app.cuisine.ui.Route
import app.cuisine.ui.StorySource
import app.cuisine.ui.ViewerSource
import app.cuisine.ui.components.Avatar
import app.cuisine.ui.components.BindPlayer
import app.cuisine.ui.components.LocalHeroRegistry
import app.cuisine.ui.components.SystemBarIcons
import app.cuisine.ui.components.VideoSurface
import app.cuisine.ui.components.rememberPlaybackEnded
import app.cuisine.ui.components.rememberPlayer
import app.cuisine.ui.components.shareMedia
import app.cuisine.ui.components.storyHeroKey
import app.cuisine.ui.feed.LikeButton
import coil3.compose.AsyncImage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PhotoMillis = 5_000

@Composable
fun StoryViewerScreen(vm: CuisineViewModel, route: Route.Stories, onBack: () -> Unit, navigate: (Route) -> Unit) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val social by vm.social.collectAsStateWithLifecycle()
    val f = feed
    val lib = library
    if (f == null || lib == null) {
        ExitOverlayImmediately()
        return
    }
    // Snapshotted on open: marking stories as seen mustn't reshuffle the pager mid-watch.
    val stories = remember { resolveStories(route.source, f, lib) }
    if (stories.isEmpty()) {
        ExitOverlayImmediately()
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    StoryViewer(
        stories = stories,
        startIndex = stories.indexOfFirst { it.id == route.startId }.coerceAtLeast(0),
        isFavorite = { it.id in social.favorites },
        onToggleFavorite = { vm.toggleFavorite(it.id) },
        onShare = { shareMedia(context, listOf(it)) },
        onSeen = { story, item -> if (story.kind != StoryKind.Highlight) vm.markSeen(story, item) },
        onOpenItem = { story, media ->
            navigate(Route.Viewer(ViewerSource.Items(story.items.map { it.id }), media.id))
        },
        onOpenProfile = { story ->
            if (story.kind == StoryKind.Mine || story.kind == StoryKind.Person) navigate(Route.Profile(story.owner.id))
        },
        onClose = onBack,
    )
}

private fun resolveStories(source: StorySource, feed: Feed, library: Library): List<Story> = when (source) {
    StorySource.Feed -> feed.stories.filter { it.items.isNotEmpty() }
    StorySource.Highlights -> feed.highlights
    is StorySource.Albums -> FeedBuilder.albumHighlights(source.authorId, feed, library)
}

private enum class Phase { Entering, Shown, Leaving }

/** How the whole story stage is drawn: scaled and shifted, clipped anywhere from a circle to a card. */
@Immutable
private data class Stage(val scale: Float, val translation: Offset, val round: Float, val corner: Float, val alpha: Float)

private val FullStage = Stage(scale = 1f, translation = Offset.Zero, round = 1f, corner = 0f, alpha = 1f)

private fun lerp(a: Stage, b: Stage, t: Float) = Stage(
    scale = a.scale + (b.scale - a.scale) * t,
    translation = a.translation + (b.translation - a.translation) * t,
    round = a.round + (b.round - a.round) * t,
    corner = a.corner + (b.corner - a.corner) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** The stage shrunk into a story bubble: a circle exactly covering [bubble]. */
private fun bubbleStage(bubble: Rect, screen: Size) = Stage(
    scale = bubble.width / screen.width,
    translation = bubble.center - Offset(screen.width / 2f, screen.height / 2f),
    round = 0f,
    corner = 0f,
    alpha = 1f,
)

/** Clips to a centred rounded rect that runs from a full-width circle ([round] 0) to the whole card. */
private class StageClip(private val round: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = round.coerceIn(0f, 1f)
        val w = size.width
        val visible = w + (size.height - w) * r
        val top = (size.height - visible) / 2f
        val radius = (w / 2f + (corner - w / 2f) * r).coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(0f, top, w, top + visible, CornerRadius(radius)))
    }
}

@Composable
private fun StoryViewer(
    stories: List<Story>,
    startIndex: Int,
    isFavorite: (Media) -> Boolean,
    onToggleFavorite: (Media) -> Unit,
    onShare: (Media) -> Unit,
    onSeen: (Story, Media) -> Unit,
    onOpenItem: (Story, Media) -> Unit,
    onOpenProfile: (Story) -> Unit,
    onClose: () -> Unit,
) {
    SystemBarIcons(light = true)
    val registry = LocalHeroRegistry.current
    val exit = LocalOverlayExit.current
    val pager = rememberPagerState(startIndex) { stories.size }
    val scope = rememberCoroutineScope()
    val player = rememberPlayer(loop = false)
    val density = LocalDensity.current
    var screen by remember { mutableStateOf(Size.Zero) }
    var phase by remember { mutableStateOf(Phase.Entering) }
    var enterFrom by remember { mutableStateOf(FullStage.copy(scale = 0.8f, alpha = 0f)) }
    var leaveFrom by remember { mutableStateOf(FullStage) }
    var leaveTo by remember { mutableStateOf(FullStage) }
    var leaveBackdrop by remember { mutableFloatStateOf(1f) }
    val travel = remember { Animatable(0f) }
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val backProgress = remember { Animatable(0f) }
    val dismissDistance = with(density) { 360.dp.toPx() }
    val cardCorner = with(density) { 32.dp.toPx() }
    val dragProgress = (drag.value.y.coerceAtLeast(0f) / dismissDistance).coerceIn(0f, 1f)

    val shownStage = Stage(
        scale = (1f - dragProgress * 0.25f) * (1f - backProgress.value * 0.12f),
        translation = drag.value,
        round = 1f,
        corner = cardCorner * ((dragProgress + backProgress.value) * 6f).coerceAtMost(1f),
        alpha = 1f,
    )
    val stage = when (phase) {
        Phase.Entering -> lerp(enterFrom, FullStage, travel.value)
        Phase.Shown -> shownStage
        Phase.Leaving -> lerp(leaveFrom, leaveTo, travel.value)
    }
    val backdrop = when (phase) {
        Phase.Entering -> travel.value
        Phase.Shown -> (1f - dragProgress * 1.2f).coerceIn(0f, 1f) * (1f - backProgress.value * 0.4f)
        Phase.Leaving -> leaveBackdrop * (1f - travel.value)
    }

    LaunchedEffect(screen) {
        if (screen == Size.Zero || phase != Phase.Entering) return@LaunchedEffect
        registry.find(storyHeroKey(stories[startIndex].id), screen)?.let { enterFrom = bubbleStage(it.rect, screen) }
        travel.snapTo(0f)
        travel.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 340f))
        phase = Phase.Shown
    }

    val leaving = exit?.requested == true
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        leaveFrom = stage
        leaveBackdrop = backdrop
        val bubble = registry.find(storyHeroKey(stories[pager.currentPage].id), screen)
        leaveTo = bubble?.let { bubbleStage(it.rect, screen) } ?: stage.copy(scale = stage.scale * 0.85f, alpha = 0f)
        phase = Phase.Leaving
        travel.snapTo(0f)
        travel.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 300f))
        exit.finish()
    }

    PredictiveBackHandler(enabled = phase == Phase.Shown) { events ->
        try {
            events.collect { backProgress.snapTo(it.progress) }
            onClose()
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f) }
            throw e
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screen = it.toSize() }
            .background(Color.Black.copy(alpha = backdrop.coerceIn(0f, 1f)))
            .pointerInput(phase) {
                if (phase != Phase.Shown) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                        if (over > 0f) change.consume()
                    } ?: return@awaitEachGesture
                    val velocity = VelocityTracker()
                    drag(start.id) { change ->
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val next = drag.value + change.positionChange()
                        scope.launch { drag.snapTo(Offset(next.x, next.y.coerceAtLeast(0f))) }
                        change.consume()
                    }
                    if (drag.value.y > dismissDistance * 0.25f || velocity.calculateVelocity().y > 2200f) {
                        onClose()
                    } else {
                        scope.launch { drag.animateTo(Offset.Zero, spring(dampingRatio = 0.78f, stiffness = 520f)) }
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pager,
            key = { stories[it].id },
            beyondViewportPageCount = 1,
            userScrollEnabled = phase == Phase.Shown,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = stage.scale
                    scaleY = stage.scale
                    translationX = stage.translation.x
                    translationY = stage.translation.y
                    alpha = stage.alpha.coerceIn(0f, 1f)
                    shape = StageClip(stage.round, stage.corner.coerceAtLeast(0f))
                    clip = true
                },
        ) { page ->
            StoryPage(
                story = stories[page],
                active = page == pager.settledPage && phase == Phase.Shown && drag.value == Offset.Zero,
                player = player,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
                onSeen = { item -> onSeen(stories[page], item) },
                onOpenItem = { onOpenItem(stories[page], it) },
                onOpenProfile = { onOpenProfile(stories[page]) },
                onClose = onClose,
                onNextStory = {
                    if (page < stories.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) } else onClose()
                },
                onPreviousStory = {
                    if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) }
                },
                modifier = Modifier.cube(pager, page),
            )
        }
    }
}

/** Instagram's cube: each person's stories sit on a face of a turning box. */
private fun Modifier.cube(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val position = page - (pager.currentPage + pager.currentPageOffsetFraction)
    cameraDistance = 14f * density
    transformOrigin = TransformOrigin(if (position < 0f) 1f else 0f, 0.5f)
    rotationY = 90f * position.coerceIn(-1f, 1f)
}

@Composable
private fun StoryPage(
    story: Story,
    active: Boolean,
    player: ExoPlayer,
    isFavorite: (Media) -> Boolean,
    onToggleFavorite: (Media) -> Unit,
    onShare: (Media) -> Unit,
    onSeen: (Media) -> Unit,
    onOpenItem: (Media) -> Unit,
    onOpenProfile: () -> Unit,
    onClose: () -> Unit,
    onNextStory: () -> Unit,
    onPreviousStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by rememberSaveable(story.id) { mutableIntStateOf(story.startIndex.coerceIn(0, story.items.lastIndex)) }
    val item = story.items[index.coerceIn(0, story.items.lastIndex)]
    val progress = remember(story.id) { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun next() {
        if (index < story.items.lastIndex) index++ else onNextStory()
    }

    fun previous() {
        if (index > 0) index-- else onPreviousStory()
    }

    LaunchedEffect(item.id) { progress.snapTo(0f) }
    LaunchedEffect(item.id, active) {
        if (active) onSeen(item)
    }

    if (item.isVideo) {
        if (active) {
            BindPlayer(player, item, play = !paused)
            val state = rememberProgressStateWithTickInterval(player, tickIntervalMs = 50)
            LaunchedEffect(state.currentPositionMs, state.durationMs) {
                if (state.durationMs > 0) {
                    progress.snapTo((state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f))
                }
            }
            val ended by rememberPlaybackEnded(player)
            LaunchedEffect(ended) { if (ended) next() }
        }
    } else {
        LaunchedEffect(item.id, active, paused) {
            if (!active || paused) return@LaunchedEffect
            val remaining = ((1f - progress.value) * PhotoMillis).toInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            next()
        }
    }

    val chromeAlpha by animateFloatAsState(if (holding) 0f else 1f, tween(180), label = "storyChrome")

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF121212)),
        ) {
            StoryMedia(item = item, player = player, playVideo = active && item.isVideo)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(story.id) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                val hold = scope.launch {
                                    delay(220)
                                    holding = true
                                }
                                tryAwaitRelease()
                                hold.cancel()
                                paused = false
                                holding = false
                            },
                            onTap = { offset -> if (offset.x < size.width * 0.3f) previous() else next() },
                        )
                    },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha }
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                    .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 28.dp),
            ) {
                StoryProgress(count = story.items.size, index = index, progress = { progress.value }, modifier = Modifier.padding(end = 8.dp))
                StoryHeader(story, item, onOpenProfile, onClose)
            }
        }
        StoryBottomBar(
            item = item,
            favorite = isFavorite(item),
            onOpen = { onOpenItem(item) },
            onFavorite = { onToggleFavorite(item) },
            onShare = { onShare(item) },
            modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
        )
    }
}

@Composable
private fun StoryMedia(item: Media, player: ExoPlayer, playVideo: Boolean) {
    val scale = if (item.aspectRatio < 0.8f) ContentScale.Crop else ContentScale.Fit
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = rememberThumbRequest(item, ThumbSize.Small),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(56.dp)
                .graphicsLayer { alpha = 0.75f },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f)),
        )
        val full: @Composable () -> Unit = {
            AsyncImage(
                model = rememberFullRequest(item, maxPx = 1600),
                contentDescription = null,
                contentScale = scale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (playVideo) VideoSurface(player = player, modifier = Modifier.fillMaxSize(), contentScale = scale, poster = full) else full()
    }
}

@Composable
private fun StoryProgress(count: Int, index: Int, progress: () -> Float, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        drawRoundRect(Color.White.copy(alpha = 0.35f), cornerRadius = radius)
                        val fill = when {
                            i < index -> 1f
                            i == index -> progress()
                            else -> 0f
                        }
                        if (fill > 0f) {
                            drawRoundRect(Color.White, size = Size(size.width * fill, size.height), cornerRadius = radius)
                        }
                    },
            )
        }
    }
}

@Composable
private fun StoryHeader(story: Story, item: Media, onProfile: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            author = story.owner,
            size = 36.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onProfile),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "  ${timeAgoShort(item.takenAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            val subtitle = when {
                story.id == "h:memories" -> yearsAgo(item.takenAt).let { if (it == 1) "1 year ago today" else "$it years ago today" }
                story.subtitle.isNotBlank() -> story.subtitle
                else -> item.bucketName
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun StoryBottomBar(
    item: Media,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpen,
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_calendar_today),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${dayTitle(item.localDate.toEpochDay())} · ${item.bucketName.ifBlank { "Library" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LikeButton(liked = favorite, onClick = onFavorite, idleTint = Color.White)
        IconButton(onClick = onShare, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(R.drawable.ic_send),
                contentDescription = "Share",
                tint = Color.White,
                modifier = Modifier.graphicsLayer { rotationZ = -24f },
            )
        }
    }
}
