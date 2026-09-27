package app.cuisine.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class AccessLevel { None, Partial, Full }

@Stable
class MediaAccessState internal constructor(initial: AccessLevel) {
    var level by mutableStateOf(initial)
        internal set

    /** Bumps whenever the system returns from a permission prompt, so reads can refresh. */
    var version by mutableIntStateOf(0)
        internal set

    var denials by mutableIntStateOf(0)
        internal set

    internal var launch: () -> Unit = {}

    fun request() = launch()
}

private val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun currentAccess(context: Context): AccessLevel = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        (context.granted(Manifest.permission.READ_MEDIA_IMAGES) || context.granted(Manifest.permission.READ_MEDIA_VIDEO)) ->
        AccessLevel.Full
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        context.granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
        AccessLevel.Partial
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
        context.granted(Manifest.permission.READ_EXTERNAL_STORAGE) ->
        AccessLevel.Full
    else -> AccessLevel.None
}

@Composable
fun rememberMediaAccess(): MediaAccessState {
    val context = LocalContext.current
    val state = remember { MediaAccessState(currentAccess(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val level = currentAccess(context)
        if (level == AccessLevel.None) state.denials++
        state.level = level
        state.version++
    }
    state.launch = { launcher.launch(mediaPermissions) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.level = currentAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}
