package app.cuisine.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Media
import app.cuisine.data.rememberThumbRequest
import coil3.compose.AsyncImage

enum class PickerFilter(val label: String) { All("All"), Photos("Photos"), Videos("Videos") }

/** Slides a full-screen picker over the current screen. */
@Composable
fun MediaPickerOverlay(
    visible: Boolean,
    items: List<Media>,
    title: String,
    confirmLabel: String,
    maxSelection: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Media>) -> Unit,
    initialSelection: List<Long> = emptyList(),
    initialFilter: PickerFilter = PickerFilter.All,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 4 } + fadeIn(),
        exit = slideOutVertically { it / 4 } + fadeOut(),
    ) {
        BackHandler(onBack = onDismiss)
        MediaPicker(
            items = items,
            title = title,
            confirmLabel = confirmLabel,
            maxSelection = maxSelection,
            initialSelection = initialSelection,
            initialFilter = initialFilter,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

/**
 * Picks up to [maxSelection] items. With a limit of one, a tap picks immediately; otherwise
 * each pick is numbered in order, like building a carousel.
 */
@Composable
fun MediaPicker(
    items: List<Media>,
    title: String,
    confirmLabel: String,
    maxSelection: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Media>) -> Unit,
    modifier: Modifier = Modifier,
    initialSelection: List<Long> = emptyList(),
    initialFilter: PickerFilter = PickerFilter.All,
) {
    val selection = remember { mutableStateListOf<Long>().apply { addAll(initialSelection) } }
    var filter by remember { mutableStateOf(initialFilter) }
    val byId = remember(items) { items.associateBy { it.id } }
    val shown = remember(items, filter) {
        when (filter) {
            PickerFilter.All -> items
            PickerFilter.Photos -> items.filter { !it.isVideo }
            PickerFilter.Videos -> items.filter { it.isVideo }
        }
    }
    val haptics = LocalHapticFeedback.current
    val single = maxSelection == 1

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                    }
                },
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(
                            text = when {
                                single -> "Tap to choose"
                                selection.isEmpty() -> "Choose up to $maxSelection"
                                else -> "${selection.size} of $maxSelection selected"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (!single) {
                        Button(
                            onClick = { onConfirm(selection.mapNotNull(byId::get)) },
                            enabled = selection.isNotEmpty(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(confirmLabel)
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                PickerFilter.entries.forEachIndexed { index, f ->
                    ToggleButton(
                        checked = filter == f,
                        onCheckedChange = { filter = f },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            PickerFilter.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = FilledTonalToggleButtonDefaults.colors(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(f.label)
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                items(shown, key = { it.id }) { media ->
                    val order = selection.indexOf(media.id)
                    PickerTile(
                        media = media,
                        order = order,
                        showCheck = !single,
                        onClick = {
                            when {
                                single -> onConfirm(listOf(media))
                                order >= 0 -> selection.remove(media.id)
                                selection.size < maxSelection -> selection.add(media.id)
                                else -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerTile(media: Media, order: Int, showCheck: Boolean, onClick: () -> Unit) {
    val selected = order >= 0
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "pickScale",
    )
    val radius by animateDpAsState(
        targetValue = if (selected) 20.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 520f),
        label = "pickRadius",
    )
    Box(
        Modifier
            .aspectRatio(1f)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = rememberThumbRequest(media),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (media.isVideo) {
                DurationBadge(media.durationMs, Modifier.align(Alignment.BottomEnd).padding(5.dp))
            }
        }
        if (showCheck) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                                .background(Color.Black.copy(alpha = 0.15f))
                                .border(2.dp, Color.White, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text(
                        text = "${order + 1}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                    )
                }
            }
        }
    }
}
