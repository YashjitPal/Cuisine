package app.cuisine.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/** Where a piece of content currently sits on screen, so an overlay can grow out of it. */
data class HeroSource(val rect: Rect, val corner: Float)

/**
 * Tracks on-screen sources for full-screen overlays (the viewer, stories). Sources publish their
 * bounds as they lay out; an overlay can lift one out, hiding it until the overlay flies back.
 */
@Stable
class HeroRegistry {
    private val sources = HashMap<String, HeroSource>()

    /** The source currently lifted into an overlay; it draws nothing while set. */
    var lifted: String? by mutableStateOf(null)

    fun publish(key: String, source: HeroSource) {
        sources[key] = source
    }

    fun withdraw(key: String) {
        sources.remove(key)
    }

    /** The source for [key] if it is at least partly on a screen of [screen] size. */
    fun find(key: String, screen: Size): HeroSource? = sources[key]?.takeIf {
        it.rect.right > 0f && it.rect.bottom > 0f && it.rect.left < screen.width && it.rect.top < screen.height
    }
}

val LocalHeroRegistry = staticCompositionLocalOf { HeroRegistry() }

fun mediaHeroKey(id: Long) = "media-$id"

fun storyHeroKey(id: String) = "story-$id"

/** Publishes this element as a hero source for [key]. */
@Composable
fun Modifier.heroSource(key: String, corner: Dp = 0.dp, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val registry = LocalHeroRegistry.current
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    DisposableEffect(registry, key) { onDispose { registry.withdraw(key) } }
    val lifted = registry.lifted == key
    return this
        .onGloballyPositioned { coordinates ->
            registry.publish(key, HeroSource(Rect(coordinates.positionInRoot(), coordinates.size.toSize()), cornerPx))
        }
        .graphicsLayer { alpha = if (lifted) 0f else 1f }
}

fun lerp(a: Rect, b: Rect, t: Float): Rect = Rect(
    left = a.left + (b.left - a.left) * t,
    top = a.top + (b.top - a.top) * t,
    right = a.right + (b.right - a.right) * t,
    bottom = a.bottom + (b.bottom - a.bottom) * t,
)

/** The largest rect of [aspect] that fits [container], centred. */
fun fittedRect(aspect: Float, container: Size): Rect {
    if (container.width <= 0f || container.height <= 0f) return Rect.Zero
    val boxAspect = container.width / container.height
    val size = if (aspect > boxAspect) Size(container.width, container.width / aspect) else Size(container.height * aspect, container.height)
    return Rect(
        left = (container.width - size.width) / 2f,
        top = (container.height - size.height) / 2f,
        right = (container.width + size.width) / 2f,
        bottom = (container.height + size.height) / 2f,
    )
}

fun Rect.scaledAboutCenter(scale: Float): Rect {
    val c = center
    return Rect(c.x - width * scale / 2f, c.y - height * scale / 2f, c.x + width * scale / 2f, c.y + height * scale / 2f)
}
