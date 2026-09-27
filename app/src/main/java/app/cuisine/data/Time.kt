package app.cuisine.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE
const val DAY = 24 * HOUR

private val longDate = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
private val longDateYear = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val fullStamp = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · h:mm a", Locale.getDefault())
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun Long.date(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** "Just now", "12 minutes ago", "3 days ago", "12 September". Matches post footers. */
fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - ms).coerceAtLeast(0)
    return when {
        diff < MINUTE -> "Just now"
        diff < HOUR -> plural(diff / MINUTE, "minute") + " ago"
        diff < DAY -> plural(diff / HOUR, "hour") + " ago"
        diff < 7 * DAY -> plural(diff / DAY, "day") + " ago"
        ms.date().year == now.date().year -> longDate.format(ms.date())
        else -> longDateYear.format(ms.date())
    }
}

/** "now", "5m", "3h", "2d", "4w", "12 Sep". Matches story and post headers. */
fun timeAgoShort(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - ms).coerceAtLeast(0)
    return when {
        diff < MINUTE -> "now"
        diff < HOUR -> "${diff / MINUTE}m"
        diff < DAY -> "${diff / HOUR}h"
        diff < 7 * DAY -> "${diff / DAY}d"
        diff < 30 * DAY -> "${diff / (7 * DAY)}w"
        ms.date().year == now.date().year -> shortDate.format(ms.date())
        else -> shortDate.format(ms.date()) + " " + ms.date().year
    }
}

fun fullTimestamp(ms: Long): String =
    fullStamp.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun clockTime(ms: Long): String = clock.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun yearsAgo(ms: Long, today: LocalDate = LocalDate.now()): Int = today.year - ms.date().year

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun plural(count: Long, noun: String): String = if (count == 1L) "1 $noun" else "$count ${noun}s"

fun plural(count: Int, noun: String): String = plural(count.toLong(), noun)

fun formatCount(count: Int): String = "%,d".format(count)
