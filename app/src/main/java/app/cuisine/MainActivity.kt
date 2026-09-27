package app.cuisine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.cuisine.ui.CuisineRoot
import app.cuisine.ui.CuisineViewModel

class MainActivity : ComponentActivity() {
    private val vm: CuisineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Holding the splash until saved settings load avoids a flash of the wrong theme.
        splash.setKeepOnScreenCondition { !vm.social.value.loaded }
        enableEdgeToEdge()
        setContent { CuisineRoot(vm) }
    }
}
