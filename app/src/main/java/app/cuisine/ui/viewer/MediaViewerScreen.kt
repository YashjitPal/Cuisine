package app.cuisine.ui.viewer

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import app.cuisine.R
import app.cuisine.data.Library
import app.cuisine.data.Media
import app.cuisine.data.SocialState
import app.cuisine.data.clockTime
import app.cuisine.data.dayTitle
import app.cuisine.data.formatDuration
import app.cuisine.data.rememberFullRequest
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.ExitOverlayImmediately
import app.cuisine.ui.LocalOverlayExit
import app.cuisine.ui.Route
import app.cuisine.ui.ViewerSource
import app.cuisine.ui.components.BindPlayer
import app.cuisine.ui.components.ImmersiveMode
import app.cuisine.ui.components.LocalHeroRegistry
import app.cuisine.ui.components.SystemBarIcons
import app.cuisine.ui.components.TagSheet
import app.cuisine.ui.components.VideoSurface
import app.cuisine.ui.components.fittedRect
import app.cuisine.ui.components.lerp
import app.cuisine.ui.components.mediaHeroKey
import app.cuisine.ui.components.rememberIsPlaying
import app.cuisine.ui.components.rememberPlayer
import app.cuisine.ui.components.scaledAboutCenter
import app.cuisine.ui.components.shareMedia
import app.cuisine.ui.library.filtered
import app.cuisine.ui.theme.heart
import coil3.compose.AsyncImage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaViewerScreen(vm: CuisineViewModel, route: Route.Viewer, onBack: () -> Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    val social by vm.social.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val peopleIn by vm.peopleIn.collectAsStateWithLifecycle()
    val lib = library
    // Resolved against the state at opening, so un-favouriting or untagging doesn't yank the page away.
    val opened = remember { social }
    val items = remember(lib, route.source) { lib?.let { resolve(route.source, it, opened) }.orEmpty() }
    if (items.isEmpty()) {
        ExitOverlayImmediately()
        LaunchedEffect(lib) { if (lib != null) onBack() }
        return
    }
    val context = LocalContext.current
    var tagging by remember { mutableStateOf<Media?>(null) }
    MediaViewer(
        items = items,
        startId = route.startId,
        favorites = social.favorites,
        onToggleFavorite = vm::toggleFavorite,
        onTag = { tagging = it },
        onShare = { shareMedia(context, listOf(it)) },
        onDelete = { vm.delete(listOf(it)) },
        onBack = onBack,
        peopleOf = { m ->
            val f = feed
            peopleIn[m.id].orEmpty().mapNotNull { id -> f?.authors?.get(id)?.name }.sorted()
        },
        tagsOf = { m ->
            val ids = social.mediaTags[m.id].orEmpty()
            social.tags.filter { it.id in ids }.map { it.name }.sorted()
        },
    )
    tagging?.let { media ->
        TagSheet(
            tags = social.tags,
            counts = social.mediaTags[media.id].orEmpty().associateWith { 1 },
            total = 1,
            onToggle = { tag, on -> vm.setTag(listOf(media.id), tag, on) },
            onCreate = { name -> vm.setTag(listOf(media.id), vm.createTag(name), true) },
            onDismiss = { tagging = null },
        )
    }
}

private fun resolve(source: ViewerSource, lib: Library, social: SocialState): List<Media> = when (source) {
    ViewerSource.All -> lib.all
    ViewerSource.Photos -> lib.photos
    ViewerSource.Videos -> lib.videos
    is ViewerSource.Gallery -> lib.filtered(source.filter, social)
    is ViewerSource.Items -> source.ids.mapNotNull(lib.byId::get)
}

private enum class Phase { Entering, Shown, Leaving }

/** A photo in flight between its thumbnail and the full-screen stage. */
@Immutable
private data class Flight(
    val media: Media,
    val from: Rect,
    val to: Rect,
    val fromCorner: Float,
    val toCorner: Float,
    val fade: Boolean,
)

@Composable
private fun MediaViewer(
    items: List<Media>,
    startId: Long,
    favorites: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onTag: (Media) -> Unit,
    onShare: (Media) -> Unit,
    onDelete: (Media) -> Unit,
    onBack: () -> Unit,
    peopleOf: (Media) -> List<String>,
    tagsOf: (Media) -> List<String>,
) {
    SystemBarIcons(light = true)
    val registry = LocalHeroRegistry.current
    val exit = LocalOverlayExit.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val startIndex = remember { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(startIndex) { items.size }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]

    var chrome by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Media?>(null) }
    var screen by remember { mutableStateOf(Size.Zero) }
    var phase by remember { mutableStateOf(Phase.Entering) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var leaveFromAlpha by remember { mutableFloatStateOf(1f) }
    val travel = remember { Animatable(0f) }
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val backProgress = remember { Animatable(0f) }
    val player = rememberPlayer(loop = true)

    val dismissDistance = with(density) { 420.dp.toPx() }
    val dragProgress = (drag.value.getDistance() / dismissDistance).coerceIn(0f, 1f)
    val stageScale = (1f - dragProgress * 0.4f) * (1f - backProgress.value * 0.14f)
    val backdrop = when (phase) {
        Phase.Entering -> travel.value
        Phase.Shown -> (1f - dragProgress * 1.1f).coerceIn(0f, 1f) * (1f - backProgress.value * 0.45f)
        Phase.Leaving -> leaveFromAlpha * (1f - travel.value)
    }
    ImmersiveMode(hidden = !chrome && phase == Phase.Shown)

    fun stageRect(media: Media): Rect {
        val fitted = fittedRect(media.aspectRatio, screen)
        val c = Offset(screen.width / 2f, screen.height / 2f)
        return Rect(c + (fitted.topLeft - c) * stageScale + drag.value, fitted.size * stageScale)
    }

    // What's on stage stays lifted out of its grid cell underneath.
    LaunchedEffect(current.id, phase) {
        if (phase != Phase.Leaving) registry.lifted = mediaHeroKey(current.id)
    }

    // Video controls step aside once playback is under way; a tap brings them back.
    LaunchedEffect(chrome, current.id, phase) {
        if (chrome && current.isVideo && phase == Phase.Shown) {
            delay(3500)
            chrome = false
        }
    }

    LaunchedEffect(screen) {
        if (screen == Size.Zero || phase != Phase.Entering) return@LaunchedEffect
        val target = fittedRect(current.aspectRatio, screen)
        val source = registry.find(mediaHeroKey(current.id), screen)
        flight = Flight(
            media = current,
            from = source?.rect ?: target.scaledAboutCenter(0.88f),
            to = target,
            fromCorner = source?.corner ?: 0f,
            toCorner = 0f,
            fade = source == null,
        )
        travel.snapTo(0f)
        travel.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
        flight = null
        phase = Phase.Shown
    }

    val leaving = exit?.requested == true
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        val media = current
        val inFlight = flight
        val from = inFlight?.let { lerp(it.from, it.to, travel.value) } ?: stageRect(media)
        leaveFromAlpha = backdrop
        chrome = false
        phase = Phase.Leaving
        val source = registry.find(mediaHeroKey(media.id), screen)
        flight = Flight(
            media = media,
            from = from,
            to = source?.rect ?: from.scaledAboutCenter(0.9f),
            fromCorner = 0f,
            toCorner = source?.corner ?: 0f,
            fade = source == null,
        )
        travel.snapTo(0f)
        travel.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 460f))
        registry.lifted = null
        exit.finish()
    }

    PredictiveBackHandler(enabled = phase == Phase.Shown) { events ->
        try {
            events.collect { backProgress.snapTo(it.progress) }
            onBack()
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
            .pointerInput(zoomed, phase) {
                if (zoomed || phase != Phase.Shown) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    val velocity = VelocityTracker()
                    drag(start.id) { change ->
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val next = drag.value + change.positionChange()
                        scope.launch { drag.snapTo(next) }
                        change.consume()
                    }
                    val fling = velocity.calculateVelocity()
                    if (drag.value.getDistance() > dismissDistance * 0.22f || abs(fling.y) > 2400f) {
                        onBack()
                    } else {
                        scope.launch { drag.animateTo(Offset.Zero, spring(dampingRatio = 0.78f, stiffness = 520f)) }
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pager,
            key = { items[it].id },
            userScrollEnabled = !zoomed && phase == Phase.Shown,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (phase == Phase.Shown) 1f else 0f
                    translationX = drag.value.x
                    translationY = drag.value.y
                    scaleX = stageScale
                    scaleY = stageScale
                },
        ) { page ->
            val media = items[page]
            val isCurrent = page == pager.currentPage
            if (media.isVideo) {
                VideoPage(
                    media = media,
                    player = player,
                    active = page == pager.settledPage && phase == Phase.Shown,
                    controlsVisible = chrome && dragProgress < 0.04f,
                    onTap = { chrome = !chrome },
                )
            } else {
                ZoomableImage(
                    media = media,
                    onTap = { chrome = !chrome },
                    onZoomChanged = { if (isCurrent) zoomed = it },
                )
            }
        }

        flight?.let { f -> FlyingPhoto(f, travel.value, leaving = phase == Phase.Leaving) }

        val chromeShown = chrome && phase == Phase.Shown && dragProgress < 0.04f && backProgress.value == 0f
        AnimatedVisibility(
            visible = chromeShown,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ViewerTopBar(media = current, position = "${pager.currentPage + 1} of ${items.size}", onBack = onBack)
        }
        AnimatedVisibility(
            visible = chromeShown,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            val favorite = current.id in favorites
            HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.standardFloatingToolbarColors()) {
                IconButton(onClick = { onShare(current) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Share")
                }
                IconButton(onClick = { onToggleFavorite(current.id) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                        contentDescription = if (favorite) "Remove from favourites" else "Favourite",
                        tint = if (favorite) MaterialTheme.colorScheme.heart else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { onTag(current) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_sell), contentDescription = "Tag")
                }
                IconButton(onClick = { info = current }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = "Details")
                }
                IconButton(onClick = { onDelete(current) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                }
            }
        }
    }
    info?.let { InfoSheet(it, people = peopleOf(it), tags = tagsOf(it), onDismiss = { info = null }) }
}

@Composable
private fun FlyingPhoto(flight: Flight, t: Float, leaving: Boolean) {
    // Position may overshoot with the spring; shape and opacity must stay in range.
    val rect = lerp(flight.from, flight.to, t)
    val settled = t.coerceIn(0f, 1f)
    val corner = (flight.fromCorner + (flight.toCorner - flight.fromCorner) * settled).coerceAtLeast(0f)
    val size = with(LocalDensity.current) { DpSize(rect.width.coerceAtLeast(0f).toDp(), rect.height.coerceAtLeast(0f).toDp()) }
    Box(
        Modifier
            .offset { rect.topLeft.round() }
            .size(size)
            .graphicsLayer {
                alpha = when {
                    !flight.fade -> 1f
                    leaving -> 1f - settled
                    else -> settled
                }
                shape = RoundedCornerShape(corner)
                clip = true
            },
    ) {
        AsyncImage(
            model = rememberFullRequest(flight.media, maxPx = 1440),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ViewerTopBar(media: Media, position: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = dayTitle(media.localDate.toEpochDay()),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = Color.White,
            )
            Text(
                text = "${clockTime(media.takenAt)} · ${media.bucketName.ifBlank { "Library" }}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
        Text(position, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
    }
}

@Composable
private fun VideoPage(
    media: Media,
    player: ExoPlayer,
    active: Boolean,
    controlsVisible: Boolean,
    onTap: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(media.id) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        val poster: @Composable () -> Unit = {
            AsyncImage(
                model = rememberFullRequest(media, maxPx = 1440),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.aspectRatio(media.aspectRatio)) {
            if (active) {
                BindPlayer(player, media, play = true)
                VideoSurface(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, poster = poster)
            } else {
                poster()
            }
        }
        if (active) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                VideoControls(player)
            }
        }
    }
}

@Composable
private fun VideoControls(player: ExoPlayer) {
    val playing by rememberIsPlaying(player)
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 200)
    var scrub by remember { mutableStateOf<Float?>(null) }
    Box(Modifier.fillMaxSize()) {
        val scrim = Color.Black.copy(alpha = 0.42f)
        FilledIconToggleButton(
            checked = playing,
            onCheckedChange = { if (it) player.play() else player.pause() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = scrim,
                contentColor = Color.White,
                checkedContainerColor = scrim,
                checkedContentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .size(IconButtonDefaults.largeContainerSize()),
        ) {
            Icon(
                painterResource(if (playing) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled),
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.size(IconButtonDefaults.largeIconSize),
            )
        }
        val duration = progress.durationMs.takeIf { it > 0 } ?: 1L
        val fraction = scrub ?: (progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
        val slider = remember { SliderState() }
        SideEffect { slider.value = fraction }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
        ) {
            Text(formatDuration((fraction * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = Color.White)
            Slider(
                state = slider,
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let { player.seekTo((it * duration).toLong()) }
                    scrub = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}
