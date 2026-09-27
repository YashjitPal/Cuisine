package app.cuisine.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.FeedBuilder
import app.cuisine.data.ME
import app.cuisine.data.Media
import app.cuisine.data.Story
import app.cuisine.data.ThumbSize
import app.cuisine.data.formatCount
import app.cuisine.data.rememberThumbRequest
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.ProfileTab
import app.cuisine.ui.Route
import app.cuisine.ui.StorySource
import app.cuisine.ui.ViewerSource
import app.cuisine.ui.components.DurationBadge
import app.cuisine.ui.components.EmptyState
import app.cuisine.ui.components.LoadingState
import app.cuisine.ui.components.RingState
import app.cuisine.ui.components.RingedAvatar
import app.cuisine.ui.components.shareMedia
import app.cuisine.ui.components.heroSource
import app.cuisine.ui.components.mediaHeroKey
import app.cuisine.ui.components.storyHeroKey
import coil3.compose.AsyncImage

private data class GridEntry(val key: String, val cover: Media, val ids: List<Long>, val multiple: Boolean)

@Composable
fun ProfileScreen(vm: CuisineViewModel, authorId: String, tab: ProfileTab, navigate: (Route) -> Unit, onBack: () -> Unit) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val social by vm.social.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var current by rememberSaveable { mutableStateOf(tab) }

    val f = feed
    val lib = library
    val author: Author? = f?.authors?.get(authorId)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = author?.handle.orEmpty(),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (author?.isMe == true) {
                        IconButton(onClick = { navigate(Route.EditProfile(null)) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_person_add), contentDescription = "Add a person")
                        }
                        IconButton(onClick = { showSettings = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { inner ->
        if (f == null || lib == null) {
            LoadingState(Modifier.padding(inner))
            return@Scaffold
        }
        if (author == null) {
            EmptyState(
                icon = R.drawable.ic_person_remove,
                title = "Not here anymore",
                body = "This person was removed from Cuisine.",
                modifier = Modifier.padding(inner),
            )
            return@Scaffold
        }
        val isMe = author.isMe
        val tabs = ProfileTab.entries.toList()
        val selected = if (current in tabs) current else ProfileTab.Posts
        val posts = remember(f, authorId) { f.posts.filter { it.features(authorId) } }
        val reels = remember(f, authorId) { f.reels.filter { it.features(authorId) } }
        val ownMedia = remember(f, lib, authorId) { f.mediaOf(authorId, lib) }
        val highlights = remember(f, lib, authorId) { FeedBuilder.albumHighlights(authorId, f, lib) }
        val story = f.storyOf(authorId)
        // On someone else's profile, Saved and Liked are yours, narrowed to posts they're in.
        val entries: List<GridEntry> = when (selected) {
            ProfileTab.Posts -> posts.map { GridEntry(it.id, it.media.first(), it.media.map { m -> m.id }, it.media.size > 1) }
            ProfileTab.Reels -> reels.map { GridEntry(it.id, it.media, listOf(it.media.id), false) }
            ProfileTab.Saved -> (if (isMe) f.posts else posts).filter { it.id in social.saves }
                .map { GridEntry(it.id, it.media.first(), it.media.map { m -> m.id }, it.media.size > 1) }
            ProfileTab.Liked -> (if (isMe) f.posts else posts).filter { it.id in social.likes }
                .map { GridEntry(it.id, it.media.first(), it.media.map { m -> m.id }, it.media.size > 1) } +
                (if (isMe) f.reels else reels).filter { it.id in social.likes }.map { GridEntry(it.id, it.media, listOf(it.media.id), false) }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(
                    author = author,
                    bio = if (isMe) social.profile.bio else "",
                    ring = when {
                        story == null -> RingState.None
                        story.seen -> RingState.Seen
                        else -> RingState.Unseen
                    },
                    stats = if (isMe) {
                        listOf(
                            posts.size to if (posts.size == 1) "post" else "posts",
                            ownMedia.size to if (ownMedia.size == 1) "photo" else "photos",
                            social.people.size to if (social.people.size == 1) "person" else "people",
                        )
                    } else {
                        val photos = ownMedia.count { !it.isVideo }
                        val videos = ownMedia.count { it.isVideo }
                        listOf(
                            posts.size to if (posts.size == 1) "post" else "posts",
                            photos to if (photos == 1) "photo" else "photos",
                            videos to if (videos == 1) "video" else "videos",
                        )
                    },
                    onAvatar = {
                        if (story != null) navigate(Route.Stories(StorySource.Feed, story.id)) else navigate(Route.EditProfile(if (isMe) ME else authorId))
                    },
                    heroKey = story?.let { storyHeroKey(it.id) },
                    onEdit = { navigate(Route.EditProfile(if (isMe) ME else authorId)) },
                    onShare = { shareMedia(context, ownMedia.take(10)) },
                    onNewPost = { navigate(Route.NewPost) },
                    isMe = isMe,
                )
            }
            if (highlights.isNotEmpty()) {
                item(key = "highlights", span = { GridItemSpan(maxLineSpan) }) {
                    Highlights(highlights) { navigate(Route.Stories(StorySource.Albums(authorId), it.id)) }
                }
            }
            item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                PrimaryTabRow(
                    selectedTabIndex = tabs.indexOf(selected),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    tabs.forEach { t ->
                        val on = t == selected
                        Tab(
                            selected = on,
                            onClick = { current = t },
                            icon = { Icon(painterResource(tabIcon(t, on)), contentDescription = t.name) },
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = tabIcon(selected, true),
                        title = when (selected) {
                            ProfileTab.Posts -> "No posts yet"
                            ProfileTab.Reels -> "No reels yet"
                            ProfileTab.Saved -> "Nothing saved"
                            ProfileTab.Liked -> "No likes yet"
                        },
                        body = when (selected) {
                            ProfileTab.Posts -> if (isMe) "Share a moment from your library to start your grid." else "Pick a few photos of ${author.name} and Cuisine finds the rest."
                            ProfileTab.Reels -> "Videos show up here as reels."
                            ProfileTab.Saved -> if (isMe) "Tap the bookmark on any post to keep it here." else "Posts with ${author.name} that you save show up here."
                            ProfileTab.Liked -> if (isMe) "Double-tap posts you love and they'll gather here." else "Posts with ${author.name} that you like show up here."
                        },
                        polygon = MaterialShapes.Clover4Leaf,
                        modifier = Modifier.height(360.dp),
                    )
                }
            }
            items(entries, key = { it.key }) { entry ->
                GridTile(entry) {
                    navigate(Route.Viewer(ViewerSource.Items(entry.ids), entry.cover.id))
                }
            }
        }
    }
    if (showSettings) SettingsSheet(vm, onDismiss = { showSettings = false })
}

@DrawableRes
private fun tabIcon(tab: ProfileTab, selected: Boolean): Int = when (tab) {
    ProfileTab.Posts -> if (selected) R.drawable.ic_grid_on_filled else R.drawable.ic_grid_on
    ProfileTab.Reels -> if (selected) R.drawable.ic_movie_filled else R.drawable.ic_movie
    ProfileTab.Saved -> if (selected) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
    ProfileTab.Liked -> if (selected) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
}

@Composable
private fun ProfileHeader(
    author: Author,
    bio: String,
    ring: RingState,
    stats: List<Pair<Int, String>>,
    onAvatar: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onNewPost: () -> Unit,
    isMe: Boolean,
    heroKey: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingedAvatar(
                author = author,
                size = 96.dp,
                ring = ring,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (heroKey != null) Modifier.heroSource(heroKey, corner = 48.dp) else Modifier)
                    .clickable(onClick = onAvatar),
            )
            Spacer(Modifier.width(20.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                stats.forEach { (value, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatCount(value), style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(author.name, style = MaterialTheme.typography.titleMediumEmphasized)
        when {
            bio.isNotBlank() -> Text(bio, style = MaterialTheme.typography.bodyMedium)
            isMe -> Text(
                "Add a bio from Edit profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "Shared with you on Cuisine",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text(if (isMe) "Edit profile" else "Edit person")
            }
            FilledTonalButton(onClick = onShare, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text("Share")
            }
            if (isMe) {
                FilledTonalIconButton(onClick = onNewPost, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_add_box), contentDescription = "New post")
                }
            }
        }
    }
}

@Composable
private fun Highlights(stories: List<Story>, onOpen: (Story) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(stories, key = { it.id }) { story ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpen(story) },
            ) {
                RingedAvatar(
                    author = story.owner,
                    size = 66.dp,
                    ring = RingState.Seen,
                    modifier = Modifier.heroSource(storyHeroKey(story.id), corner = 33.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GridTile(entry: GridEntry, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(0.75f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = rememberThumbRequest(entry.cover, ThumbSize.Medium),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .heroSource(mediaHeroKey(entry.cover.id)),
        )
        when {
            entry.multiple -> Icon(
                painterResource(R.drawable.ic_collections_filled),
                contentDescription = "Carousel",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(18.dp),
            )
            entry.cover.isVideo -> DurationBadge(entry.cover.durationMs, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}