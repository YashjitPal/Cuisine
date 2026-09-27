package app.cuisine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.cuisine.data.FaceBox
import app.cuisine.data.Media
import app.cuisine.data.ThumbSize
import app.cuisine.data.rememberThumbRequest
import coil3.compose.AsyncImage

/** A circle cut from [media] around the face at [box], with a little room around it. */
@Composable
fun FaceCrop(media: Media, box: FaceBox, size: Dp, modifier: Modifier = Modifier) {
    val aspect = media.aspectRatio
    // In units of the photo's width.
    val side = maxOf(box.width, box.height / aspect) * 1.6f
    val imageWidth = size / side.coerceAtLeast(0.05f)
    val imageHeight = imageWidth / aspect
    val cx = (box.left + box.right) / 2f
    val cy = (box.top + box.bottom) / 2f
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // requiredSize centres the oversized photo; the offset then brings the face to the middle.
        AsyncImage(
            model = rememberThumbRequest(media, ThumbSize.Large),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(x = imageWidth * (0.5f - cx), y = imageHeight * (0.5f - cy))
                .requiredSize(imageWidth, imageHeight),
        )
    }
}
