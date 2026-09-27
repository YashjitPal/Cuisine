package app.cuisine.ui.viewer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import app.cuisine.data.Media
import app.cuisine.data.rememberFullRequest
import coil3.compose.AsyncImage
import kotlin.math.max
import kotlinx.coroutines.launch

private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

/**
 * A photo that pinches, pans and double-taps to zoom. At 1x it leaves single-finger drags alone
 * so the pager and swipe-to-dismiss keep working.
 */
@Composable
fun ZoomableImage(
    media: Media,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val zoomCallback by rememberUpdatedState(onZoomChanged)
    val zoomed = scale > 1.01f
    LaunchedEffect(zoomed) { zoomCallback(zoomed) }

    val ratio = media.aspectRatio

    fun fitted(): Size {
        if (box == IntSize.Zero) return Size.Zero
        val boxRatio = box.width.toFloat() / box.height
        return if (ratio > boxRatio) Size(box.width.toFloat(), box.width / ratio) else Size(box.height * ratio, box.height.toFloat())
    }

    fun clamp(value: Offset, s: Float): Offset {
        val content = fitted()
        val maxX = max(0f, (content.width * s - box.width) / 2f)
        val maxY = max(0f, (content.height * s - box.height) / 2f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(media.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val fromScale = scale
                        val fromOffset = offset
                        val toScale = if (fromScale > 1.01f) 1f else DoubleTapZoom
                        val toOffset = if (toScale == 1f) Offset.Zero else clamp((center - tap) * (toScale - 1f), toScale)
                        scope.launch {
                            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)) { t, _ ->
                                scale = fromScale + (toScale - fromScale) * t
                                offset = fromOffset + (toOffset - fromOffset) * t
                            }
                        }
                    },
                )
            }
            .pointerInput(media.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val newScale = (scale * zoom).coerceIn(1f, MaxZoom)
                            val p = centroid - center
                            val ratioChange = newScale / scale
                            val moved = p * (1f - ratioChange) + offset * ratioChange + pan
                            scale = newScale
                            offset = if (newScale <= 1f) Offset.Zero else clamp(moved, newScale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val knownSize = media.width > 0 && media.height > 0
        AsyncImage(
            model = rememberFullRequest(media, maxPx = 2560),
            contentDescription = media.name,
            contentScale = if (knownSize) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier
                .then(if (knownSize) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}