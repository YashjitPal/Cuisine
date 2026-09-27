package app.cuisine.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import app.cuisine.R
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.MediaAccessState
import app.cuisine.ui.Route
import app.cuisine.ui.components.SystemBarIcons
import app.cuisine.ui.feed.FeedScreen
import app.cuisine.ui.library.LibraryKind
import app.cuisine.ui.library.LibraryScreen
import app.cuisine.ui.reels.ReelsScreen

enum class HomeTab(val label: String, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int) {
    All("All", R.drawable.ic_photo_library, R.drawable.ic_photo_library_filled),
    Photos("Photos", R.drawable.ic_image, R.drawable.ic_image_filled),
    Videos("Videos", R.drawable.ic_video_library, R.drawable.ic_video_library_filled),
    People("People", R.drawable.ic_group, R.drawable.ic_group_filled),
    Reels("Reels", R.drawable.ic_movie, R.drawable.ic_movie_filled),
}

@Composable
fun HomeScreen(
    vm: CuisineViewModel,
    access: MediaAccessState,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    navigate: (Route) -> Unit,
) {
    val reselects = remember { mutableStateMapOf<HomeTab, Int>() }
    val immersive = tab == HomeTab.Reels
    if (immersive) SystemBarIcons(light = true)
    val holder = rememberSaveableStateHolder()
    val background by animateColorAsState(
        targetValue = if (immersive) Color.Black else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "homeBackground",
    )

    Scaffold(
        containerColor = background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            HomeNavigationBar(selected = tab, immersive = immersive) { picked ->
                if (picked == tab) reselects[picked] = (reselects[picked] ?: 0) + 1 else onTabChange(picked)
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeThrough() },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            label = "tabs",
        ) { current ->
            holder.SaveableStateProvider(current.name) {
                val reselect = reselects[current] ?: 0
                when (current) {
                    HomeTab.All -> LibraryScreen(vm, LibraryKind.All, access, navigate, reselect)
                    HomeTab.Photos -> LibraryScreen(vm, LibraryKind.Photos, access, navigate, reselect)
                    HomeTab.Videos -> LibraryScreen(vm, LibraryKind.Videos, access, navigate, reselect)
                    HomeTab.People -> FeedScreen(vm, navigate, reselect)
                    HomeTab.Reels -> ReelsScreen(vm, navigate, reselect)
                }
            }
        }
    }
}

/** Material's fade-through: the old tab steps out quickly, the new one settles in. */
private fun AnimatedContentTransitionScope<HomeTab>.fadeThrough(): ContentTransform =
    (fadeIn(tween(220, delayMillis = 70)) + scaleIn(tween(280, delayMillis = 70), initialScale = 0.96f)) togetherWith
        fadeOut(tween(90))

@Composable
private fun HomeNavigationBar(selected: HomeTab, immersive: Boolean, onSelect: (HomeTab) -> Unit) {
    val container by animateColorAsState(
        targetValue = if (immersive) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(300),
        label = "navContainer",
    )
    val colors = if (immersive) {
        ShortNavigationBarItemDefaults.colors(
            selectedIconColor = Color.Black,
            selectedTextColorTopIconPosition = Color.White,
            selectedIndicatorColor = Color.White,
            unselectedIconColor = Color.White.copy(alpha = 0.72f),
            unselectedTextColor = Color.White.copy(alpha = 0.72f),
        )
    } else {
        ShortNavigationBarItemDefaults.colors()
    }
    ShortNavigationBar(containerColor = container) {
        HomeTab.entries.forEach { item ->
            val isSelected = item == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(item) },
                icon = { TabIcon(item, isSelected) },
                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Clip) },
                colors = colors,
            )
        }
    }
}

@Composable
private fun TabIcon(tab: HomeTab, selected: Boolean) {
    Crossfade(targetState = selected, animationSpec = tween(160), label = "tabIcon") { on ->
        Icon(painterResource(if (on) tab.selectedIcon else tab.icon), contentDescription = null)
    }
}
