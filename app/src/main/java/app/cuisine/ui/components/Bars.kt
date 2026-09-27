package app.cuisine.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Author

/** Your avatar as a top-bar action, the way most social apps lead to the profile. */
@Composable
fun ProfileButton(author: Author?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
        if (author != null) {
            Avatar(author = author, size = 30.dp)
        } else {
            Icon(painterResource(R.drawable.ic_person), contentDescription = "Profile")
        }
    }
}

/**
 * Runs [action] each time the current tab is tapped again, but not when the tab's state is
 * merely restored.
 */
@Composable
fun OnReselect(count: Int, action: suspend () -> Unit) {
    var handled by rememberSaveable { mutableIntStateOf(count) }
    val latest by rememberUpdatedState(action)
    LaunchedEffect(count) {
        if (count != handled) {
            handled = count
            latest()
        }
    }
}

/** Pinch out for bigger tiles, pinch in for more of them. One step per gesture. */
@Composable
fun Modifier.pinchToChangeColumns(columns: Int, range: IntRange = 2..6, onChange: (Int) -> Unit): Modifier {
    val current by rememberUpdatedState(columns)
    val change by rememberUpdatedState(onChange)
    return pointerInput(range) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            var stepped = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    if (!stepped && zoom > 1.18f && current > range.first) {
                        change(current - 1)
                        stepped = true
                    } else if (!stepped && zoom < 0.85f && current < range.last) {
                        change(current + 1)
                        stepped = true
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
