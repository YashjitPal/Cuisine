package app.cuisine.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Immutable
data class Media(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val mimeType: String,
    /** Display width, already corrected for the stored orientation. */
    val width: Int,
    val height: Int,
    val takenAt: Long,
    /** When it arrived in the library; "new" for stories means recently added, not recently shot. */
    val addedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f

    val isScreenshot: Boolean
        get() = relativePath.contains("Screenshot", ignoreCase = true) ||
            relativePath.contains("Screen record", ignoreCase = true) ||
            bucketName.contains("Screenshot", ignoreCase = true)

    val localDate: LocalDate
        get() = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * Identifies the same photo across devices, where MediaStore ids differ: name, capture time
     * and size survive backups, cloud sync and cable transfers.
     */
    val fingerprint: String
        get() = "$name|$takenAt|$sizeBytes"
}

@Immutable
data class Album(val id: Long, val name: String, val cover: Media, val count: Int)

@Immutable
data class DaySection(val epochDay: Long, val title: String, val items: List<Media>)

@Immutable
class Library private constructor(
    val all: List<Media>,
    val photos: List<Media>,
    val videos: List<Media>,
    val albums: List<Album>,
    val allSections: List<DaySection>,
    val photoSections: List<DaySection>,
    val videoSections: List<DaySection>,
) {
    val byId: Map<Long, Media> = all.associateBy { it.id }

    companion object {
        /** [all] must already be sorted newest first. */
        fun from(all: List<Media>): Library {
            val today = LocalDate.now()
            val photos = all.filter { !it.isVideo }
            val videos = all.filter { it.isVideo }
            val albums = all.groupBy { it.bucketId }
                .map { (id, items) -> Album(id, items.first().bucketName.ifBlank { "Other" }, items.first(), items.size) }
                .sortedByDescending { it.count }
            return Library(
                all = all,
                photos = photos,
                videos = videos,
                albums = albums,
                allSections = daySections(all, today),
                photoSections = daySections(photos, today),
                videoSections = daySections(videos, today),
            )
        }
    }
}

/** [items] (newest first) grouped by the day they were taken. */
fun sectionsOf(items: List<Media>): List<DaySection> = daySections(items, LocalDate.now())

private fun daySections(items: List<Media>, today: LocalDate): List<DaySection> {
    if (items.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val out = ArrayList<DaySection>()
    var current = ArrayList<Media>()
    var currentDay = Long.MIN_VALUE
    for (media in items) {
        val day = Instant.ofEpochMilli(media.takenAt).atZone(zone).toLocalDate().toEpochDay()
        if (day != currentDay) {
            if (current.isNotEmpty()) out += DaySection(currentDay, dayTitle(currentDay, today), current)
            current = ArrayList()
            currentDay = day
        }
        current += media
    }
    if (current.isNotEmpty()) out += DaySection(currentDay, dayTitle(currentDay, today), current)
    return out
}

private val sameYearDay = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private val otherYearDay = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

fun dayTitle(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> sameYearDay.format(date)
        else -> otherYearDay.format(date)
    }
}
