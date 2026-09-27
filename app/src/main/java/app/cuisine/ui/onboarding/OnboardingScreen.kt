package app.cuisine.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import app.cuisine.R
import app.cuisine.ui.MediaAccessState
import app.cuisine.ui.components.CuisineMark
import app.cuisine.ui.components.CuisineWordmark
import app.cuisine.ui.components.openAppSettings
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun OnboardingScreen(access: MediaAccessState) {
    val context = LocalContext.current
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeHero(
            progress = { entrance.value },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 260.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = entrance.value.coerceIn(0f, 1f)
                translationY = (1f - entrance.value) * 60.dp.toPx()
            },
        ) {
            CuisineWordmark()
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Your gallery, served fresh.",
                style = MaterialTheme.typography.displaySmallEmphasized,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Cuisine turns your photos and videos into a feed, stories, reels and a " +
                    "pinboard, without anything leaving your phone.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { access.request() },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.LargeContainerHeight),
            ) {
                Icon(
                    painterResource(R.drawable.ic_photo_library_filled),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (access.denials == 0) "Allow access" else "Try again",
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight),
                )
            }
            if (access.denials > 0) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openAppSettings(context) }, shapes = ButtonDefaults.shapes()) {
                    Text("Open settings")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Private by design. Nothing is uploaded.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private data class HeroShape(
    val polygon: RoundedPolygon,
    @DrawableRes val icon: Int,
    val x: Float,
    val y: Float,
    val size: Dp,
    val tone: Int,
    val phase: Float,
    val spin: Float,
)

/** A loose cluster of Material shapes, each carrying one of the app's surfaces, gently afloat. */
@Composable
private fun ShapeHero(progress: () -> Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shapes = remember {
        listOf(
            HeroShape(MaterialShapes.Cookie9Sided, R.drawable.ic_photo_library_filled, 0.30f, 0.34f, 150.dp, 0, 0.0f, 1f),
            HeroShape(MaterialShapes.Clover4Leaf, R.drawable.ic_favorite_filled, 0.76f, 0.22f, 104.dp, 1, 0.3f, -1f),
            HeroShape(MaterialShapes.Arch, R.drawable.ic_movie_filled, 0.74f, 0.66f, 124.dp, 2, 0.6f, 0f),
            HeroShape(MaterialShapes.Sunny, R.drawable.ic_web_stories_filled, 0.26f, 0.78f, 96.dp, 3, 0.8f, 1f),
            HeroShape(MaterialShapes.Pill, R.drawable.ic_dashboard_filled, 0.54f, 0.95f, 72.dp, 1, 0.45f, 0f),
        )
    }
    val time = rememberInfiniteTransition(label = "hero")
    val float by time.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "heroFloat",
    )
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        shapes.forEachIndexed { index, s ->
            val (container, content) = when (s.tone) {
                0 -> scheme.primaryContainer to scheme.onPrimaryContainer
                1 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                2 -> scheme.secondaryContainer to scheme.onSecondaryContainer
                else -> scheme.primary to scheme.onPrimary
            }
            val shape = s.polygon.toShape()
            Box(
                modifier = Modifier
                    .offset(x = w * s.x - s.size / 2, y = h * s.y * 0.86f - s.size / 2 + 8.dp)
                    .size(s.size)
                    .graphicsLayer {
                        val t = progress()
                        val staged = ((t * 1.4f) - index * 0.08f).coerceIn(0f, 1.2f)
                        val wave = sin((float + s.phase) * 2f * PI.toFloat())
                        scaleX = staged
                        scaleY = staged
                        translationY = wave * 8.dp.toPx()
                        rotationZ = s.spin * float * 360f * 0.25f + wave * 6f
                        alpha = staged.coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(s.size)
                        .clip(shape)
                        .background(container),
                )
                Icon(
                    painter = painterResource(s.icon),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier
                        .size(s.size * 0.34f)
                        .graphicsLayer { rotationZ = -(s.spin * float * 360f * 0.25f) },
                )
            }
        }
    }
}
