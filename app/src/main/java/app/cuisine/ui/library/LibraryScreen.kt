package app.cuisine.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisine.R
import app.cuisine.data.DaySection
import app.cuisine.data.ME
import app.cuisine.data.Media
import app.cuisine.data.Story
import app.cuisine.data.ThumbSize
import app.cuisine.data.formatCount
import app.cuisine.data.plural
import app.cuisine.data.rememberThumbRequest
import app.cuisine.data.sectionsOf
import app.cuisine.ui.AccessLevel
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.GalleryFilter
import app.cuisine.ui.MediaAccessState
import app.cuisine.ui.Route
import app.cuisine.ui.StorySource
import app.cuisine.ui.ViewerSource
import app.cuisine.ui.components.EmptyState
import app.cuisine.ui.components.LoadingState
import app.cuisine.ui.components.MediaTile
import app.cuisine.ui.components.OnReselect
import app.cuisine.ui.components.ProfileButton
import app.cuisine.ui.components.RenameTagDialog
import app.cuisine.ui.components.TagSheet
import app.cuisine.ui.components.heroSource
import app.cuisine.ui.components.pinchToChangeColumns
import app.cuisine.ui.components.shareMedia
import app.cuisine.ui.components.storyHeroKey
import coil3.compose.AsyncImage

enum class LibraryKind(val title: String, val source: ViewerSource) {
    All("Library", ViewerSource.All),
    Photos("Photos", ViewerSource.Photos),
    Videos("Videos", ViewerSource.Videos),
}

@Composable
fun LibraryScreen(
    vm: CuisineViewModel,
    kind: LibraryKind,
    access: MediaAccessState,
    navigate: (Route) -> Unit,
    reselect: Int,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val social by vm.social.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lib = library

    // Photos alone gets pills and the pinboard view.
    val isPhotos = kind == LibraryKind.Photos
    var pinboard by rememberSaveable { mutableStateOf(false) }
    var filterKey by rememberSaveable { mutableStateOf(KeyAll) }
    val pills = remember(lib, social.favorites, social.tags, social.mediaTags, filterKey) {
        if (isPhotos && lib != null) photoPills(lib, social, filterKey) else emptyList()
    }
    val filter = pills.firstOrNull { it.key == filterKey }?.filter ?: GalleryFilter.All
    val items: List<Media> = remember(lib, kind, filter, social.favorites, social.mediaTags) {
        when {
            lib == null -> emptyList()
            kind == LibraryKind.All -> lib.all
            kind == LibraryKind.Videos -> lib.videos
            else -> lib.filtered(filter, social)
        }
    }
    val sections: List<DaySection> = remember(lib, items, kind, filter) {
        when {
            lib == null -> emptyList()
            kind == LibraryKind.All -> lib.allSections
            kind == LibraryKind.Videos -> lib.videoSections
            filter == GalleryFilter.All -> lib.photoSections
            else -> sectionsOf(items)
        }
    }
    val source = if (isPhotos && filter != GalleryFilter.All) ViewerSource.Gallery(filter) else kind.source
    val highlights = if (kind == LibraryKind.All) feed?.highlights.orEmpty() else emptyList()

    val gridState = rememberLazyGridState()
    val boardState = rememberLazyStaggeredGridState()
    var columns by rememberSaveable { mutableIntStateOf(4) }
    val selection = remember { mutableStateListOf<Long>() }
    val selecting = selection.isNotEmpty()
    var tagging by remember { mutableStateOf<List<Long>?>(null) }
    var pinMenu by remember { mutableStateOf<Media?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }

    // Rows above the first day (banner, carousel) can arrive after the grid; if we're parked at the
    // top, keep the new row in view instead of letting key-based anchoring hide it above.
    val headerRows = (if (access.level == AccessLevel.Partial) 1 else 0) + (if (highlights.isNotEmpty()) 1 else 0)
    val lastHeaderRows = remember { intArrayOf(headerRows) }
    SideEffect {
        val previous = lastHeaderRows[0]
        if (headerRows > previous && gridState.firstVisibleItemIndex <= previous && gridState.firstVisibleItemScrollOffset == 0) {
            gridState.requestScrollToItem(0)
        }
        lastHeaderRows[0] = headerRows
    }
    BackHandler(enabled = selecting) { selection.clear() }
    OnReselect(reselect) {
        if (pinboard && isPhotos) boardState.animateScrollToItem(0) else gridState.animateScrollToItem(0)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun toggle(id: Long) {
        if (id in selection) selection.remove(id) else selection.add(id)
    }
    fun open(media: Media) {
        if (selecting) toggle(media.id) else navigate(Route.Viewer(source, media.id))
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedContent(
                targetState = selecting,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "libraryBar",
            ) { isSelecting ->
                if (isSelecting) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { selection.clear() }, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel selection")
                            }
                        },
                        title = {
                            Text(
                                text = "${selection.size} selected",
                                style = MaterialTheme.typography.titleLargeEmphasized,
                            )
                        },
                        actions = {
                            TextButton(onClick = {
                                if (selection.size == items.size) selection.clear() else {
                                    selection.clear()
                                    selection.addAll(items.map { it.id })
                                }
                            }) { Text(if (selection.size == items.size) "Clear" else "All") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                } else {
                    MediumFlexibleTopAppBar(
                        title = { Text(kind.title) },
                        subtitle = { Text(subtitleFor(kind, lib?.photos?.size ?: 0, lib?.videos?.size ?: 0, items.size, filter)) },
                        actions = {
                            if (isPhotos) {
                                IconToggleButton(
                                    checked = pinboard,
                                    onCheckedChange = { pinboard = it },
                                    shapes = IconButtonDefaults.toggleableShapes(),
                                ) {
                                    Icon(
                                        painterResource(if (pinboard) R.drawable.ic_grid_view else R.drawable.ic_dashboard),
                                        contentDescription = if (pinboard) "Show as grid" else "Show as pinboard",
                                    )
                                }
                            }
                            if (items.isNotEmpty()) {
                                IconButton(onClick = { selection.add(items.first().id) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(painterResource(R.drawable.ic_checklist), contentDescription = "Select")
                                }
                            }
                            ProfileButton(feed?.me, onClick = { navigate(Route.Profile(ME)) })
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
            val pillRow: @Composable (edge: Dp) -> Unit = { edge ->
                FilterPills(
                    pills = pills,
                    selectedKey = filterKey,
                    onSelect = { filterKey = it.key },
                    onRenameTag = { renaming = it },
                    onDeleteTag = { id ->
                        vm.deleteTag(id)
                        filterKey = KeyAll
                    },
                    edgePadding = edge,
                )
            }
            val emptyFilter: @Composable () -> Unit = {
                EmptyState(
                    icon = when (filter) {
                        GalleryFilter.Favorites -> R.drawable.ic_favorite_filled
                        is GalleryFilter.Tag -> R.drawable.ic_sell_filled
                        else -> R.drawable.ic_photo_camera_filled
                    },
                    title = when (filter) {
                        GalleryFilter.Favorites -> "No favourites yet"
                        is GalleryFilter.Tag -> "Nothing tagged here"
                        else -> "No photos here"
                    },
                    body = when (filter) {
                        GalleryFilter.Favorites -> "Tap the heart on any photo and it shows up here."
                        is GalleryFilter.Tag -> "Tag photos from the viewer, or select a few and tap the tag button."
                        else -> "Photos you take or save will show up here automatically."
                    },
                    modifier = Modifier.height(420.dp),
                )
            }
            when {
                lib == null -> LoadingState()
                items.isEmpty() && !(isPhotos && filter != GalleryFilter.All) -> EmptyState(
                    icon = when (kind) {
                        LibraryKind.Videos -> R.drawable.ic_videocam_filled
                        else -> R.drawable.ic_photo_camera_filled
                    },
                    title = when (kind) {
                        LibraryKind.All -> "Nothing on the menu yet"
                        LibraryKind.Photos -> "No photos yet"
                        LibraryKind.Videos -> "No videos yet"
                    },
                    body = "Photos and videos you take or save will show up here automatically.",
                )
                isPhotos && pinboard -> LazyVerticalStaggeredGrid(
                    state = boardState,
                    columns = StaggeredGridCells.Adaptive(164.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = if (selecting) 112.dp else 28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "pills", span = StaggeredGridItemSpan.FullLine, contentType = "pills") { pillRow(0.dp) }
                    if (items.isEmpty()) {
                        item(key = "empty", span = StaggeredGridItemSpan.FullLine) { emptyFilter() }
                    }
                    items(items.size, key = { items[it].id }, contentType = { "pin" }) { index ->
                        val media = items[index]
                        Pin(
                            media = media,
                            favorite = media.id in social.favorites,
                            selected = if (selecting) media.id in selection else null,
                            onClick = { open(media) },
                            onLongClick = { toggle(media.id) },
                            onMore = { pinMenu = media },
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(bottom = if (selecting) 112.dp else 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .pinchToChangeColumns(columns) { columns = it },
                ) {
                    if (access.level == AccessLevel.Partial) {
                        item(key = "partial", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
                            PartialAccessBanner(onManage = { access.request() })
                        }
                    }
                    if (highlights.isNotEmpty()) {
                        item(key = "highlights", span = { GridItemSpan(maxLineSpan) }, contentType = "carousel") {
                            HighlightsCarousel(highlights) { story ->
                                navigate(Route.Stories(StorySource.Highlights, story.id))
                            }
                        }
                    }
                    if (isPhotos) {
                        item(key = "pills", span = { GridItemSpan(maxLineSpan) }, contentType = "pills") { pillRow(12.dp) }
                        if (items.isEmpty()) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { emptyFilter() }
                        }
                    }
                    sections.forEach { section ->
                        stickyHeader(key = "day-${section.epochDay}", contentType = "header") {
                            DayHeader(
                                section = section,
                                selecting = selecting,
                                allSelected = selecting && section.items.all { it.id in selection },
                                onToggleAll = { select ->
                                    val ids = section.items.map { it.id }
                                    if (select) selection.addAll(ids.filterNot { it in selection }) else selection.removeAll(ids)
                                },
                            )
                        }
                        items(section.items, key = { it.id }, contentType = { "tile" }) { media ->
                            MediaTile(
                                media = media,
                                favorite = media.id in social.favorites,
                                selected = if (selecting) media.id in selection else null,
                                thumbPx = if (columns <= 3) ThumbSize.Medium else ThumbSize.Small,
                                onClick = { open(media) },
                                onLongClick = { toggle(media.id) },
                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            ) {
                val chosen = items.filter { it.id in selection }
                SelectionToolbar(
                    onShare = { shareMedia(context, chosen) },
                    onFavorite = {
                        val allFav = chosen.all { it.id in social.favorites }
                        vm.setFavorite(chosen.map { it.id }, !allFav)
                        selection.clear()
                    },
                    onTag = { tagging = chosen.map { it.id } },
                    onDelete = {
                        vm.delete(chosen)
                        selection.clear()
                    },
                )
            }
        }
    }

    tagging?.let { ids ->
        TagSheet(
            tags = social.tags,
            counts = ids.flatMap { social.mediaTags[it].orEmpty() }.groupingBy { it }.eachCount(),
            total = ids.size,
            onToggle = { tag, on -> vm.setTag(ids, tag, on) },
            onCreate = { name -> vm.setTag(ids, vm.createTag(name), true) },
            onDismiss = {
                tagging = null
                selection.clear()
            },
        )
    }
    pinMenu?.let { media ->
        PinSheet(
            favorite = media.id in social.favorites,
            onDismiss = { pinMenu = null },
            onOpen = { navigate(Route.Viewer(source, media.id)) },
            onFavorite = { vm.toggleFavorite(media.id) },
            onTag = { tagging = listOf(media.id) },
            onShare = { shareMedia(context, listOf(media)) },
            onAvatar = {
                vm.updateProfile(social.profile.copy(avatarId = media.id))
                vm.toast("Profile photo updated")
            },
        )
    }
    renaming?.let { id ->
        RenameTagDialog(
            current = social.tags.firstOrNull { it.id == id }?.name.orEmpty(),
            onRename = { name ->
                vm.renameTag(id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

private fun subtitleFor(kind: LibraryKind, photos: Int, videos: Int, shown: Int, filter: GalleryFilter): String = when (kind) {
    LibraryKind.All -> "${formatCount(photos)} photos · ${formatCount(videos)} videos"
    LibraryKind.Photos -> if (filter == GalleryFilter.All) plural(photos, "photo") else plural(shown, "item")
    LibraryKind.Videos -> plural(videos, "video")
}

@Composable
private fun DayHeader(section: DaySection, selecting: Boolean, allSelected: Boolean, onToggleAll: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selecting) {
            IconButton(onClick = { onToggleAll(!allSelected) }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(
                        if (allSelected) R.drawable.ic_check_circle_filled else R.drawable.ic_radio_button_unchecked,
                    ),
                    contentDescription = if (allSelected) "Deselect day" else "Select day",
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = plural(section.items.size, "item"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PartialAccessBanner(onManage: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_lock_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Limited access",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Cuisine can only see the photos you picked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(
                onClick = onManage,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            ) { Text("Manage") }
        }
    }
}

/** "For you": auto-made highlight reels in Material's multi-browse carousel. */
@Composable
private fun HighlightsCarousel(stories: List<Story>, onOpen: (Story) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        Text(
            text = "For you",
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp, top = 4.dp),
        )
        val state = rememberCarouselState { stories.size }
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 196.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp),
        ) { index ->
            val story = stories[index]
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .heroSource(storyHeroKey(story.id), corner = 28.dp)
                    .clickable { onOpen(story) },
            ) {
                AsyncImage(
                    model = rememberThumbRequest(story.items.first(), ThumbSize.Medium),
                    contentDescription = story.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.62f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                        .graphicsLayer {
                            val info = carouselItemDrawInfo
                            val span = (info.maxSize - info.minSize).coerceAtLeast(1f)
                            alpha = ((info.size - info.minSize) / span * 1.6f - 0.5f).coerceIn(0f, 1f)
                        },
                ) {
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = story.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionToolbar(onShare: () -> Unit, onFavorite: () -> Unit, onTag: () -> Unit, onDelete: () -> Unit) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        IconButton(onClick = onShare, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_share), contentDescription = "Share")
        }
        IconButton(onClick = onFavorite, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_favorite), contentDescription = "Favourite")
        }
        IconButton(onClick = onTag, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_sell), contentDescription = "Tag")
        }
        IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
        }
    }
}
