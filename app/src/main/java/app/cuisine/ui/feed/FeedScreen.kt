package app.cuisine.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.Feed
import app.cuisine.data.ME
import app.cuisine.data.Post
import app.cuisine.data.formatCount
import app.cuisine.faces.IndexProgress
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.ProfileTab
import app.cuisine.ui.Route
import app.cuisine.ui.StorySource
import app.cuisine.ui.ViewerSource
import app.cuisine.ui.components.CuisineMark
import app.cuisine.ui.components.CuisineWordmark
import app.cuisine.ui.components.EmptyState
import app.cuisine.ui.components.LoadingState
import app.cuisine.ui.components.OnReselect
import app.cuisine.ui.components.ProfileButton
import app.cuisine.ui.components.RingState
import app.cuisine.ui.components.ShapedIcon
import app.cuisine.ui.components.rememberPlayer
import app.cuisine.ui.components.shareMedia
import kotlin.math.abs

@Composable
fun FeedScreen(vm: CuisineViewModel, navigate: (Route) -> Unit, reselect: Int) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val social by vm.social.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var captionFor by remember { mutableStateOf<Post?>(null) }
    var notesFor by remember { mutableStateOf<Post?>(null) }
    var optionsFor by remember { mutableStateOf<Post?>(null) }
    var collabFor by remember { mutableStateOf<Post?>(null) }
    val faceProgress by vm.faceProgress.collectAsStateWithLifecycle()
    BackHandler(enabled = menuOpen) { menuOpen = false }
    OnReselect(reselect) {
        scrollBehavior.state.heightOffset = 0f
        listState.animateScrollToItem(0)
    }
    // A freshly shared post should greet you at the top of the feed.
    var postCount by rememberSaveable { mutableIntStateOf(social.posts.size) }
    LaunchedEffect(social.posts.size) {
        if (social.posts.size > postCount) {
            scrollBehavior.state.heightOffset = 0f
            listState.animateScrollToItem(0)
        }
        postCount = social.posts.size
    }

    // One muted player follows whichever post sits closest to the middle of the screen.
    val player = rememberPlayer(loop = true, muted = true)
    var muted by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    val activePost by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .filter { (it.key as? String)?.startsWith("post:") == true }
                .minByOrNull { abs(it.offset + it.size / 2 - center) }
                ?.key as? String
        }
    }
    val fabVisible by remember { derivedStateOf { !listState.isScrollInProgress || !listState.lastScrolledForward } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { CuisineWordmark() },
                actions = {
                    IconButton(
                        onClick = { navigate(Route.Profile(ME, ProfileTab.Liked)) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        BadgedBox(badge = { if (social.likes.isNotEmpty()) Badge() }) {
                            Icon(painterResource(R.drawable.ic_favorite), contentDescription = "Liked posts")
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
        },
        floatingActionButton = {
            CreateMenu(
                expanded = menuOpen,
                visible = fabVisible,
                onExpandedChange = { menuOpen = it },
                onNewPost = { navigate(Route.NewPost) },
                onAddPerson = { navigate(Route.EditProfile(null)) },
            )
        },
    ) { inner ->
        val current = feed
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
            if (current == null) {
                LoadingState()
            } else {
                FeedList(
                    feed = current,
                    vm = vm,
                    listState = listState,
                    activePost = activePost,
                    playback = FeedPlayback(player, muted) { muted = !muted },
                    navigate = navigate,
                    onCaption = { captionFor = it },
                    onNotes = { notesFor = it },
                    onOptions = { optionsFor = it },
                    onCollab = { collabFor = it },
                    onShare = { shareMedia(context, it.media) },
                    faceProgress = faceProgress,
                )
            }
            AnimatedVisibility(visible = menuOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            menuOpen = false
                        },
                )
            }
        }
    }

    val me = feed?.me
    captionFor?.let { post ->
        CaptionSheet(initial = post.caption, onDismiss = { captionFor = null }, onSave = { vm.setCaption(post.id, it) })
    }
    notesFor?.let { post ->
        if (me != null) {
            NotesSheet(
                me = me,
                notes = social.notes[post.id].orEmpty(),
                onAdd = { vm.addNote(post.id, it) },
                onDelete = { vm.deleteNote(post.id, it.id) },
                onDismiss = { notesFor = null },
            )
        }
    }
    optionsFor?.let { post ->
        PostOptionsSheet(
            post = post,
            saved = post.id in social.saves,
            onDismiss = { optionsFor = null },
            onSave = { vm.toggleSave(post.id) },
            onShare = { shareMedia(context, post.media) },
            onView = { navigate(Route.Viewer(ViewerSource.Items(post.media.map { it.id }), post.media.first().id)) },
            onEditCaption = { captionFor = post },
            onProfile = { navigate(Route.Profile(it.id)) },
            onHide = { vm.hideFromFeed(post.media) },
            onDelete = { vm.deletePost(post.id) },
        )
    }
    collabFor?.let { post ->
        CollaboratorsSheet(
            authors = post.authors,
            ringFor = { author ->
                val story = feed?.storyOf(author.id)
                when {
                    story == null -> RingState.None
                    story.seen -> RingState.Seen
                    else -> RingState.Unseen
                }
            },
            onPick = { navigate(Route.Profile(it.id)) },
            onDismiss = { collabFor = null },
        )
    }
}

@Composable
private fun FeedList(
    feed: Feed,
    vm: CuisineViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    activePost: String?,
    playback: FeedPlayback,
    navigate: (Route) -> Unit,
    onCaption: (Post) -> Unit,
    onNotes: (Post) -> Unit,
    onOptions: (Post) -> Unit,
    onCollab: (Post) -> Unit,
    onShare: (Post) -> Unit,
    faceProgress: IndexProgress,
) {
    val social by vm.social.collectAsStateWithLifecycle()
    val learning = social.profile.likeness.samples.isNotEmpty() || social.people.any { it.likeness.samples.isNotEmpty() }
    fun ringFor(author: Author): RingState {
        val story = feed.storyOf(author.id) ?: return RingState.None
        return if (story.seen) RingState.Seen else RingState.Unseen
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 112.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "stories", contentType = "stories") {
            StoriesRow(
                stories = feed.stories,
                onOpen = { navigate(Route.Stories(StorySource.Feed, it.id)) },
                onAddPerson = { navigate(Route.EditProfile(null)) },
                onProfile = { navigate(Route.Profile(it.id)) },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        if (learning && faceProgress.running) {
            item(key = "faces", contentType = "faces") { FaceScanCard(faceProgress) }
        }
        if (social.profile.isDefault && !social.settings.setupDismissed) {
            item(key = "setup", contentType = "setup") {
                SetupCard(
                    onSetUp = { navigate(Route.EditProfile(ME)) },
                    onDismiss = { vm.dismissSetup() },
                )
            }
        }
        if (feed.posts.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = R.drawable.ic_add_box_filled,
                    title = "Your feed is hungry",
                    body = "Take a few photos or make a post from your library to fill it up.",
                    modifier = Modifier.height(420.dp),
                    action = {
                        Button(onClick = { navigate(Route.NewPost) }, shapes = ButtonDefaults.shapes()) {
                            Text("Make a post")
                        }
                    },
                )
            }
        }
        items(feed.posts, key = { "post:${it.id}" }, contentType = { "post" }) { post ->
            PostCard(
                post = post,
                liked = post.id in social.likes,
                saved = post.id in social.saves,
                noteCount = social.notes[post.id]?.size ?: 0,
                authorRing = ringFor(post.author),
                playback = if (activePost == "post:${post.id}") playback else null,
                onLike = { vm.toggleLike(post.id, it) },
                onSave = { vm.toggleSave(post.id) },
                onShare = { onShare(post) },
                onNotes = { onNotes(post) },
                onAuthor = {
                    val story = feed.storyOf(post.author.id)
                    when {
                        post.isCollab -> onCollab(post)
                        story != null && !story.seen -> navigate(Route.Stories(StorySource.Feed, story.id))
                        else -> navigate(Route.Profile(post.author.id))
                    }
                },
                onOpen = { index ->
                    navigate(Route.Viewer(ViewerSource.Items(post.media.map { it.id }), post.media[index].id))
                },
                onMore = { onOptions(post) },
                onEditCaption = { onCaption(post) },
            )
        }
        if (feed.posts.isNotEmpty()) {
            item(key = "caught-up", contentType = "end") { CaughtUp() }
        }
    }
}

@Composable
private fun CreateMenu(
    expanded: Boolean,
    visible: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNewPost: () -> Unit,
    onAddPerson: () -> Unit,
) {
    fun pick(action: () -> Unit) {
        onExpandedChange(false)
        action()
    }
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier.animateFloatingActionButton(visible = visible || expanded, alignment = Alignment.BottomEnd),
            ) {
                val icon = if (checkedProgress > 0.5f) R.drawable.ic_close else R.drawable.ic_add
                Icon(
                    painter = painterResource(icon),
                    contentDescription = if (expanded) "Close" else "Create",
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { pick(onNewPost) },
            icon = { Icon(painterResource(R.drawable.ic_add_box), contentDescription = null) },
            text = { Text("New post") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick(onAddPerson) },
            icon = { Icon(painterResource(R.drawable.ic_person_add), contentDescription = null) },
            text = { Text("Add a person") },
        )
    }
}

@Composable
private fun SetupCard(onSetUp: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            // On-container colours: vibrant dark palettes put primary and primaryContainer too close.
            ShapedIcon(
                icon = R.drawable.ic_person_filled,
                polygon = MaterialShapes.Cookie7Sided,
                container = MaterialTheme.colorScheme.onPrimaryContainer,
                content = MaterialTheme.colorScheme.primaryContainer,
                size = 56.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Make it yours", style = MaterialTheme.typography.titleLargeEmphasized)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add your name, a profile photo and a few photos of you, so Cuisine can find you in your library.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSetUp,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) { Text("Set up profile") }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    ) { Text("Not now") }
                }
            }
        }
    }
}

/** While faces are still being found, so new rings and collabs turning up make sense. */
@Composable
private fun FaceScanCard(progress: IndexProgress) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (progress.waitingForDetector) "Getting face recognition ready" else "Finding people in your photos",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                Spacer(Modifier.height(6.dp))
                if (progress.waitingForDetector) {
                    Text(
                        "Google Play services is fetching the free face detector. Nothing leaves your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearWavyProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatCount(progress.done)} of ${formatCount(progress.total)} looked at, on this phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaughtUp() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShapedIcon(
                icon = R.drawable.ic_check,
                polygon = MaterialShapes.SoftBurst,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 76.dp,
                spinning = true,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("You're all caught up", style = MaterialTheme.typography.titleMediumEmphasized)
        Text(
            "That's every moment in your library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        CuisineMark(selected = false, tint = MaterialTheme.colorScheme.outline)
    }
}
