package app.cuisine.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.ThumbSize
import app.cuisine.data.rememberThumbRequest
import app.cuisine.ui.theme.flexFamily
import app.cuisine.ui.theme.storyRing
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

enum class RingState { None, Unseen, Seen }

/** A circular profile picture with an initial as fallback. */
@Composable
fun Avatar(author: Author, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = remember(author.id, scheme) {
        when ((author.id.hashCode().absoluteValue + if (author.isMe) 0 else 1) % 3) {
            0 -> scheme.primaryContainer to scheme.onPrimaryContainer
            1 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            else -> scheme.secondaryContainer to scheme.onSecondaryContainer
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        val avatar = author.avatar
        if (avatar != null) {
            AsyncImage(
                model = rememberThumbRequest(avatar, if (size > 64.dp) ThumbSize.Medium else ThumbSize.Small),
                contentDescription = author.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val initial = author.name.trim().firstOrNull()?.uppercase()
            if (initial == null) {
                Icon(
                    painter = painterResource(R.drawable.ic_person_filled),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(size * 0.5f),
                )
            } else {
                Text(
                    text = initial,
                    color = content,
                    style = TextStyle(
                        fontFamily = flexFamily(weight = 760, width = 110f, roundness = 100f, opticalSize = 48f),
                        fontSize = with(LocalDensity.current) { (size * 0.42f).toSp() },
                    ),
                )
            }
        }
    }
}

/**
 * The story ring: a slowly turning sweep of the scheme's accents while unseen, a hairline once
 * watched. The ring and its gap sit outside [content].
 */
@Composable
fun StoryRing(
    state: RingState,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 2.5.dp,
    gap: Dp = 2.5.dp,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme) { scheme.storyRing }
    val seenColor = scheme.outlineVariant
    val angle = if (state == RingState.Unseen) {
        rememberInfiniteTransition(label = "ring").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "ringAngle",
        )
    } else {
        null
    }
    Box(
        modifier = modifier
            .drawWithCache {
                val stroke = ringWidth.toPx()
                val hairline = 1.25.dp.toPx()
                val brush = Brush.sweepGradient(colors, size.center)
                onDrawBehind {
                    when (state) {
                        RingState.Unseen -> rotate(angle?.value ?: 0f) {
                            drawCircle(brush, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                        }
                        RingState.Seen -> drawCircle(
                            color = seenColor,
                            radius = size.minDimension / 2 - hairline / 2,
                            style = Stroke(hairline),
                        )
                        RingState.None -> Unit
                    }
                }
            }
            .padding(ringWidth + gap),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A ringed avatar sized by its outer diameter. */
@Composable
fun RingedAvatar(
    author: Author,
    size: Dp,
    ring: RingState,
    modifier: Modifier = Modifier,
    ringWidth: Dp = if (size >= 64.dp) 3.dp else 2.dp,
    gap: Dp = if (size >= 64.dp) 3.dp else 2.dp,
) {
    StoryRing(state = ring, modifier = modifier.size(size), ringWidth = ringWidth, gap = gap) {
        Avatar(author = author, size = size - (ringWidth + gap) * 2)
    }
}

/** A small circular badge outlined in the surface color, like the "+" on your own story. */
@Composable
fun SurfaceBadge(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
