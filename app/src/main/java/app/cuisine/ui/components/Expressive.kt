package app.cuisine.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import app.cuisine.ui.theme.WordmarkStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A [Morph] frozen at [progress], scaled and centred the same way as `RoundedPolygon.toShape`. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.fitted(progress, size))
}

private fun Morph.fitted(progress: Float, size: Size, path: Path = Path()): Path {
    val out = toPath(progress = progress, path = path)
    out.transform(Matrix().apply { scale(size.width, size.height) })
    out.translate(size.center - out.getBounds().center)
    return out
}

private fun RoundedPolygon.fitted(size: Size): Path {
    val out = Morph(this, this).toPath(progress = 0f)
    out.transform(Matrix().apply { scale(size.width, size.height) })
    out.translate(size.center - out.getBounds().center)
    return out
}

/**
 * The double-tap heart: a soft burst that springs open into a heart, then lifts away.
 * Every change of [trigger] (other than 0) plays it once.
 */
@Composable
fun HeartBurst(trigger: Int, modifier: Modifier = Modifier, size: Dp = 116.dp, color: Color = Color.White) {
    val morph = remember { Morph(MaterialShapes.SoftBurst, MaterialShapes.Heart) }
    val scale = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(1f)
        scale.snapTo(0.25f)
        progress.snapTo(0f)
        spin.snapTo(-40f)
        coroutineScope {
            launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
            launch { progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 300f)) }
            launch { spin.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        }
        delay(90)
        coroutineScope {
            launch { alpha.animateTo(0f, tween(200)) }
            launch { scale.animateTo(1.18f, tween(200)) }
        }
    }
    val path = remember { Path() }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = spin.value
                this.alpha = alpha.value
            },
    ) {
        if (alpha.value <= 0f) return@Canvas
        val shape = morph.fitted(progress.value, this.size, path)
        translate(top = 3.dp.toPx()) { drawPath(shape, Color.Black.copy(alpha = 0.22f)) }
        drawPath(shape, color)
    }
}

/** An icon sitting on one of Material's expressive shapes, optionally turning slowly. */
@Composable
fun ShapedIcon(
    @DrawableRes icon: Int,
    polygon: RoundedPolygon,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    iconSize: Dp = size * 0.38f,
    spinning: Boolean = false,
) {
    val rotation = if (spinning) {
        rememberInfiniteTransition(label = "shapeSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing)),
            label = "shapeSpinAngle",
        )
    } else {
        null
    }
    val shape = polygon.toShape()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation?.value ?: 0f }
                .clip(shape)
                .background(container),
        )
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapedIcon(
            icon = icon,
            polygon = polygon,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 136.dp,
            spinning = true,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(28.dp))
            action()
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator()
    }
}

/** "Cuisine", set heavy and round, filled with the scheme's primary-to-tertiary sweep. */
@Composable
fun CuisineWordmark(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val brush = remember(scheme.primary, scheme.tertiary) {
        Brush.linearGradient(listOf(scheme.primary, scheme.tertiary), start = Offset.Zero, end = Offset(420f, 60f))
    }
    Text(text = "Cuisine", style = WordmarkStyle.copy(brush = brush), modifier = modifier)
}

/**
 * The Cuisine mark: a nine-sided cookie with a heart. Outlined when idle; when [selected] it
 * fills, spins a little and the heart is punched out.
 */
@Composable
fun CuisineMark(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 360f),
        label = "markMorph",
    )
    val rotation by animateFloatAsState(
        targetValue = if (selected) 40f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 240f),
        label = "markSpin",
    )
    val shapePath = remember { Path() }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val inset = this.size.minDimension * 0.08f
        val box = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        val cookie = morph.fitted(progress, box, shapePath)
        translate(inset, inset) {
            rotate(rotation, pivot = box.center) {
                if (selected) {
                    drawPath(cookie, tint)
                } else {
                    drawPath(cookie, tint, style = Stroke(width = this.size.minDimension * 0.085f))
                }
            }
        }
        drawHeart(
            fraction = if (selected) 0.40f else 0.34f,
            color = if (selected) Color.Black else tint,
            blendMode = if (selected) BlendMode.Clear else BlendMode.SrcOver,
        )
    }
}

private fun DrawScope.drawHeart(fraction: Float, color: Color, blendMode: BlendMode) {
    val side = size.minDimension * fraction
    val heart = MaterialShapes.Heart.fitted(Size(side, side))
    translate((size.width - side) / 2f, (size.height - side) / 2f + side * 0.04f) {
        drawPath(heart, color, blendMode = blendMode)
    }
}
