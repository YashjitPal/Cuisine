package app.cuisine.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

// Expressive "default spatial" spring, tuned slightly calmer for full-screen moves.
private val spatial = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 380f)

/** Shared-axis push: the new screen glides in, the old one drifts back and dims. */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.pushTransition(): ContentTransform =
    (slideInHorizontally(spatial) { it / 3 } + fadeIn(tween(210, delayMillis = 30))) togetherWith
        (slideOutHorizontally(spatial) { -it / 10 } + fadeOut(tween(150)))

fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.popTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(spatial) { -it / 10 } + fadeIn(tween(210)),
    initialContentExit = slideOutHorizontally(spatial) { it / 3 } + fadeOut(tween(150)),
    targetContentZIndex = -1f,
)

/** Predictive back: the current screen shrinks toward the gesture edge, revealing the previous one. */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.predictivePopTransition(edge: Int): ContentTransform {
    val origin = if (edge == NavigationEvent.EDGE_RIGHT) TransformOrigin(0.2f, 0.5f) else TransformOrigin(0.8f, 0.5f)
    return ContentTransform(
        targetContentEnter = fadeIn(tween(160)) + scaleIn(tween(300), initialScale = 0.96f),
        initialContentExit = scaleOut(tween(300), targetScale = 0.86f, transformOrigin = origin) + fadeOut(tween(260)),
        targetContentZIndex = -1f,
    )
}