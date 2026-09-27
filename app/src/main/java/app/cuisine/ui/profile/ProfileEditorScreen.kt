package app.cuisine.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.FaceSample
import app.cuisine.data.Likeness
import app.cuisine.data.ME
import app.cuisine.data.Media
import app.cuisine.data.Person
import app.cuisine.data.Profile
import app.cuisine.data.newId
import app.cuisine.data.plural
import app.cuisine.data.rememberThumbRequest
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.components.Avatar
import app.cuisine.ui.components.FaceCrop
import app.cuisine.ui.components.MediaPickerOverlay
import app.cuisine.ui.components.PickerFilter
import app.cuisine.ui.components.SectionLabel
import app.cuisine.ui.components.SurfaceBadge
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private enum class Picking { Avatar, Media, Review }

/** `ME` edits your profile, `null` adds a person, anything else edits that person. */
@Composable
fun ProfileEditorScreen(vm: CuisineViewModel, personId: String?, onDone: () -> Unit) {
    val social by vm.social.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val faceIndexProgress by vm.faceProgress.collectAsStateWithLifecycle()
    val isMe = personId == ME
    val existing = remember { social.people.firstOrNull { it.id == personId } }
    val profile = social.profile
    val likeness = remember { if (isMe) profile.likeness else existing?.likeness ?: Likeness() }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable {
        mutableStateOf(if (isMe) profile.name.takeUnless { profile.isDefault }.orEmpty() else existing?.name.orEmpty())
    }
    var handle by rememberSaveable {
        mutableStateOf(if (isMe) profile.handle.takeUnless { profile.isDefault }.orEmpty() else existing?.handle.orEmpty())
    }
    var handleTouched by rememberSaveable { mutableStateOf(handle.isNotEmpty()) }
    var bio by rememberSaveable { mutableStateOf(profile.bio) }
    var avatarId by rememberSaveable { mutableStateOf(if (isMe) profile.avatarId else existing?.avatarId) }
    var picked by rememberSaveable { mutableStateOf(likeness.picked) }
    var excluded by rememberSaveable { mutableStateOf(likeness.excluded.toList()) }
    var samples by remember { mutableStateOf(likeness.samples) }
    var learning by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var picking by remember { mutableStateOf<Picking?>(null) }

    val byId = library?.byId.orEmpty()
    // Recount whenever the faces change or the index finds more photos.
    LaunchedEffect(samples, faceIndexProgress.done) { found = vm.previewMatches(samples) }

    fun learn(chosen: List<Media>) {
        scope.launch {
            learning = true
            samples = vm.learnFaces(chosen.filter { !it.isVideo }.ifEmpty { chosen })
            learning = false
        }
    }

    val preview = Author(
        id = personId ?: "new",
        name = name.ifBlank { if (isMe) "You" else "" },
        handle = handle,
        avatar = avatarId?.let(byId::get),
        isMe = isMe,
    )
    val who = if (isMe) "you" else name.trim().ifBlank { "them" }
    val title = when {
        isMe -> "Edit profile"
        existing != null -> "Edit ${existing.name}"
        else -> "Add a person"
    }

    fun save() {
        val cleanName = name.trim()
        val cleanHandle = handle.trim().removePrefix("@").ifBlank { toHandle(cleanName) }
        val newLikeness = Likeness(picked = picked, samples = samples, excluded = excluded.toSet())
        if (isMe) {
            vm.updateProfile(
                Profile(
                    name = cleanName.ifBlank { "You" },
                    handle = cleanHandle.ifBlank { "you" },
                    bio = bio.trim(),
                    avatarId = avatarId,
                    likeness = newLikeness,
                ),
            )
            vm.dismissSetup()
        } else {
            vm.savePerson(
                Person(
                    id = existing?.id ?: newId(),
                    name = cleanName,
                    handle = cleanHandle.ifBlank { "friend" },
                    avatarId = avatarId,
                    likeness = newLikeness,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                ),
            )
            vm.toast(if (existing == null) "$cleanName was added" else "Saved")
        }
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDone, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel")
                        }
                    },
                    title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized) },
                    actions = {
                        Button(
                            onClick = ::save,
                            enabled = (isMe || name.isNotBlank()) && !learning,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Save") }
                    },
                )
            },
        ) { inner ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp),
                ) {
                    Avatar(
                        author = preview,
                        size = 128.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { picking = Picking.Avatar },
                    )
                    SurfaceBadge(
                        size = 44.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .clip(CircleShape)
                            .clickable { picking = Picking.Avatar },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_photo_camera_filled),
                            contentDescription = "Choose photo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                TextButton(
                    onClick = { picking = Picking.Avatar },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(if (avatarId == null) "Choose a profile photo" else "Change photo") }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!handleTouched) handle = toHandle(it)
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = handle,
                    onValueChange = {
                        handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' }
                        handleTouched = true
                    },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isMe) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(150) },
                        label = { Text("Bio") },
                        supportingText = { Text("${bio.length}/150") },
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionLabel(if (isMe) "Photos of you" else "Photos of them")
                Text(
                    text = if (isMe) {
                        "Pick a few photos of yourself. Cuisine learns your face on this phone, so new photos " +
                            "of you become your story and group shots become collabs."
                    } else {
                        "Pick a few photos of $who. Cuisine learns their face on this phone and finds the rest; " +
                            "new photos of them show up as their story."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
                val chosen = picked.mapNotNull(byId::get)
                if (chosen.isEmpty()) {
                    Surface(
                        onClick = { picking = Picking.Media },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_add_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Choose photos", style = MaterialTheme.typography.titleSmallEmphasized)
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(chosen, key = { it.id }) { media ->
                            AsyncImage(
                                model = rememberThumbRequest(media),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 84.dp, height = 112.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            plural(chosen.size, "photo") + " picked",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                        )
                        FilledTonalButton(onClick = { picking = Picking.Media }, shapes = ButtonDefaults.shapes()) {
                            Text("Edit selection")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FaceCard(
                        learning = learning,
                        samples = samples,
                        byId = byId,
                        who = who,
                        foundCount = (found - picked.toSet() - excluded.toSet()).size,
                        onReview = { picking = Picking.Review },
                    )
                }

                if (existing != null) {
                    Spacer(Modifier.height(28.dp))
                    TextButton(
                        onClick = {
                            vm.removePerson(existing.id)
                            vm.toast("${existing.name} was removed")
                            onDone()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_person_remove),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Remove ${existing.name}", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        val lib = library
        if (lib != null) {
            MediaPickerOverlay(
                visible = picking == Picking.Avatar,
                items = lib.photos,
                title = "Profile photo",
                confirmLabel = "Use",
                maxSelection = 1,
                onDismiss = { picking = null },
                onConfirm = {
                    avatarId = it.firstOrNull()?.id
                    picking = null
                },
                initialFilter = PickerFilter.Photos,
            )
            MediaPickerOverlay(
                visible = picking == Picking.Media,
                items = lib.all,
                title = if (isMe) "Photos of you" else "Photos of $who",
                confirmLabel = "Done",
                maxSelection = 60,
                initialSelection = picked,
                onDismiss = { picking = null },
                onConfirm = { selection ->
                    picked = selection.map { it.id }
                    excluded = excluded - picked.toSet()
                    if (avatarId == null) avatarId = selection.firstOrNull { !it.isVideo }?.id
                    picking = null
                    learn(selection)
                },
            )
            // Everything recognition found starts ticked; untick what isn't them.
            val review = remember(found, picked) { lib.all.filter { it.id in found && it.id !in picked } }
            MediaPickerOverlay(
                visible = picking == Picking.Review,
                items = review,
                title = if (isMe) "Is this you?" else "Is this $who?",
                confirmLabel = "Done",
                maxSelection = review.size.coerceAtLeast(1),
                initialSelection = review.map { it.id }.filterNot { it in excluded },
                onDismiss = { picking = null },
                onConfirm = { kept ->
                    val keep = kept.mapTo(HashSet()) { it.id }
                    excluded = (excluded.filterNot { it in found } + review.map { it.id }.filterNot { it in keep }).distinct()
                    picking = null
                },
            )
        }
    }
}

/** What Cuisine learned: the faces it will look for, and how many more photos it found. */
@Composable
private fun FaceCard(
    learning: Boolean,
    samples: List<FaceSample>,
    byId: Map<Long, Media>,
    who: String,
    foundCount: Int,
    onReview: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = learning to samples.isEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "faceCard",
        ) { (busy, none) ->
            Column(Modifier.padding(18.dp)) {
                when {
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Learning ${if (who == "you") "your" else "their"} face…",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                    }
                    none -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_face), contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("No clear faces yet", style = MaterialTheme.typography.titleSmallEmphasized)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Only the photos you picked will count. Add a couple where the face is clear and " +
                                "looking at the camera, and Cuisine can find the rest.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                                samples.take(5).forEach { sample ->
                                    byId[sample.mediaId]?.let { media ->
                                        FaceCrop(
                                            media = media,
                                            box = sample.box,
                                            size = 44.dp,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(2.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (foundCount > 0) "Found in ${plural(foundCount, "more photo")}" else "Looking for this face",
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                )
                                Text(
                                    "Learned from ${plural(samples.size, "photo")}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (foundCount > 0) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onReview, shapes = ButtonDefaults.shapes()) {
                                Icon(painterResource(R.drawable.ic_person_search), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Review matches")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toHandle(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), ".").filter { it.isLetterOrDigit() || it == '.' || it == '_' }
