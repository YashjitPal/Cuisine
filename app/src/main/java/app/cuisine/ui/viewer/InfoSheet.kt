package app.cuisine.ui.viewer

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import app.cuisine.ui.components.rememberExpandedSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Media
import app.cuisine.data.clockTime
import app.cuisine.data.formatBytes
import app.cuisine.data.formatDuration
import app.cuisine.data.fullTimestamp
import app.cuisine.ui.components.ShapedIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

@Composable
fun InfoSheet(media: Media, people: List<String>, tags: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                ShapedIcon(
                    icon = if (media.isVideo) R.drawable.ic_videocam_filled else R.drawable.ic_photo_camera_filled,
                    polygon = MaterialShapes.Cookie6Sided,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 56.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = headerDate.format(Instant.ofEpochMilli(media.takenAt).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        text = clockTime(media.takenAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            val rows = buildList {
                add(Triple(R.drawable.ic_image, media.name.ifBlank { "Untitled" }, media.mimeType.ifBlank { null }))
                if (media.width > 0 && media.height > 0) {
                    val mp = media.width.toLong() * media.height / 1_000_000.0
                    add(
                        Triple(
                            R.drawable.ic_aspect_ratio,
                            "${media.width} × ${media.height}",
                            if (media.isVideo) "Resolution" else "%.1f megapixels".format(mp),
                        ),
                    )
                }
                if (media.isVideo) add(Triple(R.drawable.ic_timer, formatDuration(media.durationMs), "Duration"))
                add(Triple(R.drawable.ic_sd_card, formatBytes(media.sizeBytes), "On this device"))
                add(Triple(R.drawable.ic_folder, media.bucketName.ifBlank { "Library" }, media.relativePath.trimEnd('/').ifBlank { null }))
                add(Triple(R.drawable.ic_schedule, fullTimestamp(media.takenAt), "Taken"))
                if (people.isNotEmpty()) add(Triple(R.drawable.ic_group, people.joinToString(", "), "In this photo"))
                if (tags.isNotEmpty()) add(Triple(R.drawable.ic_sell, tags.joinToString(", "), "Tags"))
            }
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                rows.forEachIndexed { index, (icon, title, subtitle) ->
                    InfoRow(icon, title, subtitle, index, rows.size)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, title: String, subtitle: String?, index: Int, count: Int) {
    ListItem(
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(),
        content = { Text(title) },
    )
}
