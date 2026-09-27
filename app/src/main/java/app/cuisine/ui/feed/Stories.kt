package app.cuisine.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Author
import app.cuisine.data.Story
import app.cuisine.ui.components.RingState
import app.cuisine.ui.components.RingedAvatar
import app.cuisine.ui.components.heroSource
import app.cuisine.ui.components.storyHeroKey

private val BubbleSize = 76.dp

/**
 * You, then everyone you've added. A ring means new photos of them to watch; with no ring,
 * tapping someone opens their profile.
 */
@Composable
fun StoriesRow(
    stories: List<Story>,
    onOpen: (Story) -> Unit,
    onAddPerson: () -> Unit,
    onProfile: (Author) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(stories, key = { it.id }) { story ->
            StoryBubble(
                story = story,
                onClick = { if (story.items.isNotEmpty()) onOpen(story) else onProfile(story.owner) },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "add-person") { AddPersonBubble(onAddPerson) }
    }
}

@Composable
private fun StoryBubble(story: Story, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "bubblePress",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(BubbleSize + 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        RingedAvatar(
            author = story.owner,
            size = BubbleSize,
            ring = when {
                story.items.isEmpty() -> RingState.None
                story.seen -> RingState.Seen
                else -> RingState.Unseen
            },
            modifier = Modifier.heroSource(storyHeroKey(story.id), corner = BubbleSize / 2),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = story.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (story.items.isNotEmpty() && !story.seen) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddPersonBubble(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(BubbleSize + 6.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(BubbleSize)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawCircle(
                        color = outline,
                        radius = size.minDimension / 2 - stroke,
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))),
                    )
                }
                .padding(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_person_add),
                contentDescription = "Add a person",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
