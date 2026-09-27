package app.cuisine.ui.create

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.ME
import app.cuisine.data.Media
import app.cuisine.data.plural
import app.cuisine.data.rememberFullRequest
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.components.Avatar
import app.cuisine.ui.components.DurationBadge
import app.cuisine.ui.components.LoadingState
import app.cuisine.ui.components.MediaPicker
import app.cuisine.ui.components.SectionLabel
import coil3.compose.AsyncImage

@Composable
fun NewPostScreen(vm: CuisineViewModel, onBack: () -> Unit, onPosted: () -> Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val peopleIn by vm.peopleIn.collectAsStateWithLifecycle()
    val lib = library
    val f = feed
    if (lib == null || f == null) {
        LoadingState()
        return
    }
    var selection by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step == 1) { step = 0 }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState > initialState
            (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 6 else it / 6 } + fadeOut())
        },
        label = "newPostStep",
    ) { current ->
        if (current == 0) {
            MediaPicker(
                items = lib.all,
                title = "New post",
                confirmLabel = "Next",
                maxSelection = 10,
                initialSelection = selection,
                onDismiss = onBack,
                onConfirm = {
                    selection = it.map { m -> m.id }
                    step = 1
                },
            )
        } else {
            ComposePost(
                media = selection.mapNotNull(lib.byId::get),
                authors = f.authors.values.toList(),
                // Whoever is recognised in the picks posts it; several make it a collab.
                suggested = selection.flatMap { peopleIn[it].orEmpty() }.distinct().ifEmpty { listOf(ME) },
                onBack = { step = 0 },
                onShare = { authorIds, caption ->
                    vm.createPost(authorIds, selection.mapNotNull(lib.byId::get), caption)
                    vm.toast("Posted to your feed")
                    onPosted()
                },
            )
        }
    }
}

@Composable
private fun ComposePost(
    media: List<Media>,
    authors: List<Author>,
    suggested: List<String>,
    onBack: () -> Unit,
    onShare: (authorIds: List<String>, caption: String) -> Unit,
) {
    var caption by rememberSaveable { mutableStateOf("") }
    var authorIds by rememberSaveable { mutableStateOf(suggested.filter { id -> authors.any { it.id == id } }.ifEmpty { listOf(ME) }) }
    val author = authors.firstOrNull { it.id == authorIds.first() } ?: authors.first()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("New post", style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(
                            plural(media.size, "item"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                val carousel = rememberCarouselState { media.size }
                HorizontalUncontainedCarousel(
                    state = carousel,
                    itemWidth = if (media.size == 1) 300.dp else 260.dp,
                    itemSpacing = 10.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(top = 8.dp),
                ) { index ->
                    val item = media[index]
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .maskClip(MaterialTheme.shapes.extraLarge),
                    ) {
                        AsyncImage(
                            model = rememberFullRequest(item, maxPx = 1080),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isVideo) {
                            DurationBadge(item.durationMs, Modifier.align(Alignment.TopEnd).padding(12.dp))
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
                ) {
                    Avatar(author, 44.dp, Modifier.padding(top = 6.dp))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it.take(2200) },
                        placeholder = { Text("Write a caption…") },
                        minLines = 3,
                        maxLines = 8,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (authors.size > 1) {
                    SectionLabel("Post as", Modifier.padding(start = 16.dp))
                    Text(
                        "Pick more than one to make it a collab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(authors, key = { it.id }) { a ->
                            val on = a.id in authorIds
                            ToggleButton(
                                checked = on,
                                onCheckedChange = { checked ->
                                    // Somebody always has to post it.
                                    authorIds = if (checked) authorIds + a.id else (authorIds - a.id).ifEmpty { authorIds }
                                },
                            ) {
                                Avatar(a, 24.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (a.isMe) "You" else a.name)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Button(
                onClick = { onShare(authorIds, caption) },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(
                    painterResource(R.drawable.ic_send_filled),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)),
                )
                Spacer(Modifier.width(10.dp))
                Text("Share", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }
}