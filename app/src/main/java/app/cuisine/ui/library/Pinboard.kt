package app.cuisine.ui.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Album
import app.cuisine.data.Library
import app.cuisine.data.Media
import app.cuisine.data.SocialState
import app.cuisine.data.ThumbSize
import app.cuisine.data.formatDuration
import app.cuisine.data.rememberThumbRequest
import app.cuisine.ui.GalleryFilter
import app.cuisine.ui.components.SegmentedAction
import app.cuisine.ui.components.SegmentedList
import app.cuisine.ui.components.heroSource
import app.cuisine.ui.components.mediaHeroKey
import app.cuisine.ui.components.rememberExpandedSheetState
import app.cuisine.ui.theme.heart
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** One pill above the Photos tab. */
@Immutable
data class FilterPill(val key: String, val label: String, val icon: Int, val selectedIcon: Int, val filter: GalleryFilter)

/** All, then Favourites, your tags and your albums. */
fun photoPills(lib: Library, social: SocialState, selectedKey: String): List<FilterPill> = buildList {
    add(FilterPill(KeyAll, "All", R.drawable.ic_image, R.drawable.ic_image_filled, GalleryFilter.All))
    if (social.favorites.isNotEmpty() || selectedKey == KeyFavorites) {
        add(FilterPill(KeyFavorites, "Favourites", R.drawable.ic_favorite, R.drawable.ic_favorite_filled, GalleryFilter.Favorites))
    }
    val used = social.mediaTags.values.flatMapTo(HashSet()) { it }
    social.tags
        .filter { it.id in used || "tag:${it.id}" == selectedKey }
        .sortedBy { it.name.lowercase() }
        .forEach { add(FilterPill("tag:${it.id}", it.name, R.drawable.ic_sell, R.drawable.ic_sell_filled, GalleryFilter.Tag(it.id))) }
    lib.albums
        .filter { album -> lib.photos.any { it.bucketId == album.id } }
        .forEach { add(FilterPill(albumKey(it), it.name, R.drawable.ic_folder, R.drawable.ic_folder_filled, GalleryFilter.Album(it.id, it.name))) }
}

const val KeyAll = "all"
const val KeyFavorites = "favorites"

private fun albumKey(album: Album) = "album:${album.id}"

/** What a pill shows: photos only, except tags, which gather whatever you tagged. */
fun Library.filtered(filter: GalleryFilter, social: SocialState): List<Media> = when (filter) {
    GalleryFilter.All -> photos
    GalleryFilter.Favorites -> photos.filter { it.id in social.favorites }
    is GalleryFilter.Tag -> all.filter { social.mediaTags[it.id]?.contains(filter.id) == true }
    is GalleryFilter.Album -> photos.filter { it.bucketId == filter.id }
}

@Composable
fun FilterPills(
    pills: List<FilterPill>,
    selectedKey: String,
    onSelect: (FilterPill) -> Unit,
    onRenameTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 12.dp,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = edgePadding),
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp),
    ) {
        items(pills, key = { it.key }) { pill ->
            val checked = pill.key == selectedKey
            val tagId = (pill.filter as? GalleryFilter.Tag)?.id
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.animateItem()) {
                ToggleButton(
                    checked = checked,
                    // Tapping the tag you're already on opens its options.
                    onCheckedChange = { if (checked && tagId != null) menu = true else onSelect(pill) },
                ) {
                    Icon(
                        painterResource(if (checked) pill.selectedIcon else pill.icon),
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(pill.label)
                    if (tagId != null && checked) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            painterResource(R.drawable.ic_expand_more),
                            contentDescription = "Tag options",
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                        )
                    }
                }
                if (tagId != null) {
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                        DropdownMenuItem(
                            text = { Text("Rename tag") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                            onClick = {
                                menu = false
                                onRenameTag(tagId)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete tag") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_label_off), contentDescription = null) },
                            onClick = {
                                menu = false
                                onDeleteTag(tagId)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A pinboard card: the photo at its own shape, rounded, with a menu underneath. */
@Composable
fun Pin(
    media: Media,
    favorite: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val inset by animateDpAsState(if (selected == true) 10.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "pinInset")
    val checkScale by animateFloatAsState(if (selected == true) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "pinCheck")
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(media.aspectRatio.coerceIn(0.6f, 1.6f))
                .clip(RoundedCornerShape(24.dp))
                .background(if (selected == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            AsyncImage(
                model = rememberThumbRequest(media, ThumbSize.Medium),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .clip(RoundedCornerShape(24.dp - inset / 2))
                    .heroSource(mediaHeroKey(media.id), corner = 24.dp, enabled = selected == null),
            )
            if (media.isVideo) {
                Text(
                    text = "▶ " + formatDuration(media.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (favorite && selected == null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_favorite_filled),
                        contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.heart,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            if (selected != null) {
                Icon(
                    painterResource(if (selected) R.drawable.ic_check_circle_filled else R.drawable.ic_radio_button_unchecked),
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(26.dp)
                        .graphicsLayer {
                            val s = if (selected) checkScale else 1f
                            scaleX = s
                            scaleY = s
                        }
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onMore, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(32.dp)) {
                Icon(
                    painterResource(R.drawable.ic_more_horiz),
                    contentDescription = "Pin options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun PinSheet(
    favorite: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onTag: () -> Unit,
    onShare: () -> Unit,
    onAvatar: () -> Unit,
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
        SegmentedList(
            actions = listOf(
                SegmentedAction(R.drawable.ic_open_in_new, "Open") { pick(onOpen) },
                SegmentedAction(
                    if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
                    if (favorite) "Remove from favourites" else "Add to favourites",
                ) { pick(onFavorite) },
                SegmentedAction(R.drawable.ic_sell, "Tag") { pick(onTag) },
                SegmentedAction(R.drawable.ic_person, "Set as profile photo") { pick(onAvatar) },
                SegmentedAction(R.drawable.ic_share, "Share") { pick(onShare) },
            ),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        )
    }
}
