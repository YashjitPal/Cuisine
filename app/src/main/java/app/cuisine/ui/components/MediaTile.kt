package app.cuisine.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Media
import app.cuisine.data.ThumbSize
import app.cuisine.data.formatDuration
import app.cuisine.data.rememberThumbRequest
import coil3.compose.AsyncImage

/**
 * A square library cell. In selection mode ([selected] non-null) the photo shrinks into a
 * rounder shape and a check appears, the way expressive selection reads at a glance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    favorite: Boolean = false,
    selected: Boolean? = null,
    corner: Dp = 4.dp,
    shared: Boolean = true,
    thumbPx: Int = ThumbSize.Small,
) {
    val isSelected = selected == true
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "tileScale",
    )
    val radius by animateDpAsState(
        targetValue = if (isSelected) 22.dp else corner,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 520f),
        label = "tileCorner",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                model = rememberThumbRequest(media, thumbPx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .heroSource(mediaHeroKey(media.id), corner = corner, enabled = shared),
            )
            if (media.isVideo) {
                DurationBadge(media.durationMs, Modifier.align(Alignment.TopEnd).padding(5.dp))
            }
            if (favorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_favorite_filled),
                    contentDescription = "Favourite",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .size(16.dp)
                        .shadow(6.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black),
                )
            }
        }
        if (selected != null) {
            SelectionCheck(isSelected, Modifier.align(Alignment.TopStart).padding(7.dp))
        }
    }
}

@Composable
fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(22.dp)
                .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                .background(Color.Black.copy(alpha = 0.12f), CircleShape),
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 600f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle_filled),
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
            )
        }
    }
}

@Composable
fun DurationBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(start = 3.dp, end = 7.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play_arrow_filled),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = formatDuration(durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmallEmphasized,
        )
    }
}
