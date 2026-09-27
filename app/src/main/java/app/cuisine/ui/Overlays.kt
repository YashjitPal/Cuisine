package app.cuisine.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.CompletableDeferred

private const val FullscreenOverlayKey = "cuisine.fullscreenOverlay"

/** Metadata for entries that render above the previous screen instead of replacing it. */
val FullscreenOverlay: Map<String, Any> = mapOf(FullscreenOverlayKey to true)

/**
 * Lets an overlay play its exit (flying back into its source) after it has been popped. The
 * back stack drops the entry at once; the overlay stays composed until [finish] is called.
 */
@Stable
class OverlayExit internal constructor(private val onFinished: () -> Unit) {
    var requested by mutableStateOf(false)
        private set
    private val done = CompletableDeferred<Unit>()

    internal suspend fun await() {
        requested = true
        done.await()
    }

    fun finish() {
        if (done.complete(Unit)) onFinished()
    }
}

val LocalOverlayExit = staticCompositionLocalOf<OverlayExit?> { null }

/** For overlay states with nothing to animate (still loading, empty): a pop completes at once. */
@Composable
fun ExitOverlayImmediately() {
    val exit = LocalOverlayExit.current ?: return
    LaunchedEffect(exit.requested) { if (exit.requested) exit.finish() }
}

private class FullscreenOverlayScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val exit: OverlayExit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<T>> = previousEntries
    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalOverlayExit provides exit) { entry.Content() }
    }

    override suspend fun onRemove() = exit.await()

    override fun equals(other: Any?): Boolean = other is FullscreenOverlayScene<*> && other.key == key

    override fun hashCode(): Int = key.hashCode()
}

/** Shows [FullscreenOverlay] entries as overlays, keeping the screen beneath composed and visible. */
class FullscreenOverlayStrategy<T : Any> : SceneStrategy<T> {
    private val exits = HashMap<Any, OverlayExit>()

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        if (entries.size < 2 || entry.metadata[FullscreenOverlayKey] != true) return null
        val key = entry.contentKey
        val exit = exits.getOrPut(key) { OverlayExit { exits.remove(key) } }
        return FullscreenOverlayScene(key, entry, entries.dropLast(1), exit)
    }
}
