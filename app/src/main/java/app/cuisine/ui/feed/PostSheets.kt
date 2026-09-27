package app.cuisine.ui.feed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.cuisine.ui.components.rememberExpandedSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.Note
import app.cuisine.data.Post
import app.cuisine.data.timeAgoShort
import app.cuisine.ui.components.Avatar
import app.cuisine.ui.components.RingState
import app.cuisine.ui.components.RingedAvatar
import app.cuisine.ui.components.SegmentedAction
import app.cuisine.ui.components.SegmentedList
import app.cuisine.ui.components.ShapedIcon
import kotlinx.coroutines.launch

@Composable
fun CaptionSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            then()
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .imePadding(),
        ) {
            Text("Caption", style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                "Say where it was, who was there, or what made it good.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write a caption…") },
                minLines = 3,
                maxLines = 8,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { close() }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { close { onSave(text) } }, shapes = ButtonDefaults.shapes()) { Text("Save") }
            }
        }
    }
}

/** Private notes on a post: the comment thread of a gallery only you can read. */
@Composable
fun NotesSheet(
    me: Author,
    notes: List<Note>,
    onAdd: (String) -> Unit,
    onDelete: (Note) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    var draft by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Text(
                "Notes",
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Only you can see these",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (notes.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 32.dp),
                ) {
                    ShapedIcon(
                        icon = R.drawable.ic_chat_bubble_filled,
                        polygon = MaterialShapes.Cookie6Sided,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        size = 84.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No notes yet", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        "Leave yourself a memory: a place, a person, a feeling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Avatar(me, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(me.handle) }
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                            append("  ${timeAgoShort(note.createdAt)}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(note.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { onDelete(note) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    painterResource(R.drawable.ic_delete),
                                    contentDescription = "Delete note",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(me, 38.dp)
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Add a note…") },
                    shape = CircleShape,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        onAdd(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = "Post note")
                }
            }
        }
    }
}

@Composable
fun PostOptionsSheet(
    post: Post,
    saved: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onView: () -> Unit,
    onEditCaption: () -> Unit,
    onProfile: (Author) -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    fun pick(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickAction(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark, if (saved) "Saved" else "Save") {
                    pick(onSave)
                }
                QuickAction(R.drawable.ic_share, "Share") { pick(onShare) }
                QuickAction(R.drawable.ic_open_in_new, "Open") { pick(onView) }
            }
            Spacer(Modifier.height(20.dp))
            SegmentedList(
                buildList {
                    add(SegmentedAction(R.drawable.ic_edit, if (post.caption.isBlank()) "Add a caption" else "Edit caption") { pick(onEditCaption) })
                    post.authors.forEach { author ->
                        add(SegmentedAction(R.drawable.ic_person, "Go to ${author.name}") { pick { onProfile(author) } })
                    }
                    if (post.isUserPost) {
                        add(SegmentedAction(R.drawable.ic_delete, "Delete post", "The photos stay in your library", destructive = true) { pick(onDelete) })
                    } else {
                        add(SegmentedAction(R.drawable.ic_visibility_off, "Hide from feed", "Still kept in your library") { pick(onHide) })
                    }
                },
            )
        }
    }
}

/** Everyone in a collab post, like Instagram's collaborator list. */
@Composable
fun CollaboratorsSheet(
    authors: List<Author>,
    ringFor: (Author) -> RingState,
    onPick: (Author) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "In this post",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                authors.forEachIndexed { index, author ->
                    ListItem(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onPick(author)
                            }
                        },
                        leadingContent = { RingedAvatar(author = author, size = 48.dp, ring = ringFor(author)) },
                        supportingContent = { Text("@${author.handle}") },
                        trailingContent = {
                            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                        },
                        shapes = ListItemDefaults.segmentedShapes(index, authors.size),
                        colors = ListItemDefaults.segmentedColors(),
                        content = { Text(if (author.isMe) "You" else author.name, style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(painterResource(icon), contentDescription = null)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}