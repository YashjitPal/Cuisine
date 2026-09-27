package app.cuisine.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.cuisine.ui.components.HeroRegistry
import app.cuisine.ui.components.LocalHeroRegistry
import app.cuisine.ui.components.SystemBarIcons
import app.cuisine.ui.create.NewPostScreen
import app.cuisine.ui.home.HomeScreen
import app.cuisine.ui.home.HomeTab
import app.cuisine.ui.onboarding.OnboardingScreen
import app.cuisine.ui.profile.ProfileEditorScreen
import app.cuisine.ui.profile.ProfileScreen
import app.cuisine.ui.stories.StoryViewerScreen
import app.cuisine.ui.theme.CuisineTheme
import app.cuisine.ui.theme.isAppInDarkTheme
import app.cuisine.ui.viewer.MediaViewerScreen

@Composable
fun CuisineRoot(vm: CuisineViewModel) {
    val social by vm.social.collectAsStateWithLifecycle()
    CuisineTheme(social.settings) {
        SystemBarIcons(light = isAppInDarkTheme(social.settings))
        val access = rememberMediaAccess()
        LaunchedEffect(access.level, access.version) {
            if (access.level != AccessLevel.None) vm.onAccessGranted()
        }

        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

        val deleteLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) vm.onDeleted()
        }
        LaunchedEffect(Unit) {
            vm.deleteRequests.collect { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        }

        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = access.level == AccessLevel.None,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
                    label = "gate",
                ) { locked ->
                    if (locked) OnboardingScreen(access) else CuisineNavigation(vm, access)
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 150.dp),
                )
            }
        }
    }
}

@Composable
private fun CuisineNavigation(vm: CuisineViewModel, access: MediaAccessState) {
    val backStack = remember { mutableStateListOf<Route>(Route.Home) }
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.All) }
    val navigate: (Route) -> Unit = remember(backStack) { { backStack.add(it) } }
    val back: () -> Unit = remember(backStack) { { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) } }

    val overlays = remember { FullscreenOverlayStrategy<Route>() }
    val heroes = remember { HeroRegistry() }
    // A Box so overlay scenes (viewer, stories) stack above the screen they came from.
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalHeroRegistry provides heroes) {
            NavDisplay(
                backStack = backStack,
                onBack = back,
                sceneStrategies = listOf(overlays),
                transitionSpec = { pushTransition() },
                popTransitionSpec = { popTransition() },
                predictivePopTransitionSpec = { edge -> predictivePopTransition(edge) },
                entryProvider = entryProvider {
                    entry<Route.Home> {
                        HomeScreen(
                            vm = vm,
                            access = access,
                            tab = homeTab,
                            onTabChange = { homeTab = it },
                            navigate = navigate,
                        )
                    }
                    entry<Route.Viewer>(metadata = FullscreenOverlay) { route ->
                        MediaViewerScreen(vm, route, onBack = back)
                    }
                    entry<Route.Stories>(metadata = FullscreenOverlay) { route ->
                        StoryViewerScreen(vm, route, onBack = back, navigate = navigate)
                    }
                    entry<Route.Profile> { route ->
                        ProfileScreen(vm, route.authorId, route.tab, navigate = navigate, onBack = back)
                    }
                    entry<Route.NewPost> {
                        NewPostScreen(
                            vm = vm,
                            onBack = back,
                            onPosted = {
                                homeTab = HomeTab.People
                                backStack.retainAll(listOf(Route.Home))
                            },
                        )
                    }
                    entry<Route.EditProfile> { route ->
                        ProfileEditorScreen(vm, route.personId, onDone = back)
                    }
                },
            )
        }
    }
}
