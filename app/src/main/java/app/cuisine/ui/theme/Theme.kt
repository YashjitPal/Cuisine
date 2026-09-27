package app.cuisine.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import app.cuisine.data.Flavor
import app.cuisine.data.Settings
import app.cuisine.data.ThemeMode

@Composable
fun isAppInDarkTheme(settings: Settings): Boolean = when (settings.themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun CuisineTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val target = remember(settings.flavor, dark) { colorSchemeFor(settings.flavor, dark, context) }
    MaterialExpressiveTheme(
        colorScheme = animateColorScheme(target),
        motionScheme = MotionScheme.expressive(),
        typography = CuisineTypography,
        content = content,
    )
}

val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun colorSchemeFor(flavor: Flavor, dark: Boolean, context: Context): ColorScheme = when (flavor) {
    Flavor.Wallpaper -> if (dynamicColorAvailable) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        colorSchemeFor(Flavor.Paprika, dark, context)
    }
    Flavor.Paprika -> if (dark) paprikaDark else paprikaLight
    Flavor.Saffron -> if (dark) saffronDark else saffronLight
    Flavor.Basil -> if (dark) basilDark else basilLight
    Flavor.Blueberry -> if (dark) blueberryDark else blueberryLight
    Flavor.Plum -> if (dark) plumDark else plumLight
}

/** Cross-fades every role when the user switches flavor or light/dark. */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target !== to) {
            from = lerp(from, to, progress.value)
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 420))
        }
    }
    val p = progress.value
    return if (p >= 1f) to else lerp(from, to, p)
}

private fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme {
    fun c(x: Color, y: Color) = lerp(x, y, t)
    return a.copy(
        primary = c(a.primary, b.primary),
        onPrimary = c(a.onPrimary, b.onPrimary),
        primaryContainer = c(a.primaryContainer, b.primaryContainer),
        onPrimaryContainer = c(a.onPrimaryContainer, b.onPrimaryContainer),
        inversePrimary = c(a.inversePrimary, b.inversePrimary),
        secondary = c(a.secondary, b.secondary),
        onSecondary = c(a.onSecondary, b.onSecondary),
        secondaryContainer = c(a.secondaryContainer, b.secondaryContainer),
        onSecondaryContainer = c(a.onSecondaryContainer, b.onSecondaryContainer),
        tertiary = c(a.tertiary, b.tertiary),
        onTertiary = c(a.onTertiary, b.onTertiary),
        tertiaryContainer = c(a.tertiaryContainer, b.tertiaryContainer),
        onTertiaryContainer = c(a.onTertiaryContainer, b.onTertiaryContainer),
        background = c(a.background, b.background),
        onBackground = c(a.onBackground, b.onBackground),
        surface = c(a.surface, b.surface),
        onSurface = c(a.onSurface, b.onSurface),
        surfaceVariant = c(a.surfaceVariant, b.surfaceVariant),
        onSurfaceVariant = c(a.onSurfaceVariant, b.onSurfaceVariant),
        surfaceTint = c(a.surfaceTint, b.surfaceTint),
        inverseSurface = c(a.inverseSurface, b.inverseSurface),
        inverseOnSurface = c(a.inverseOnSurface, b.inverseOnSurface),
        error = c(a.error, b.error),
        onError = c(a.onError, b.onError),
        errorContainer = c(a.errorContainer, b.errorContainer),
        onErrorContainer = c(a.onErrorContainer, b.onErrorContainer),
        outline = c(a.outline, b.outline),
        outlineVariant = c(a.outlineVariant, b.outlineVariant),
        scrim = c(a.scrim, b.scrim),
        surfaceBright = c(a.surfaceBright, b.surfaceBright),
        surfaceContainer = c(a.surfaceContainer, b.surfaceContainer),
        surfaceContainerHigh = c(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = c(a.surfaceContainerHighest, b.surfaceContainerHighest),
        surfaceContainerLow = c(a.surfaceContainerLow, b.surfaceContainerLow),
        surfaceContainerLowest = c(a.surfaceContainerLowest, b.surfaceContainerLowest),
        surfaceDim = c(a.surfaceDim, b.surfaceDim),
    )
}

/** Hearts stay recognisably red in every flavor; the error palette is the harmonised red. */
val ColorScheme.heart: Color get() = error

/**
 * The story-ring sweep: this scheme's accents pushed to full saturation, so the ring reads as
 * vividly as Instagram's while staying on-palette. Loops back to its first color for a clean seam.
 */
val ColorScheme.storyRing: List<Color>
    get() {
        val a = tertiary.vivid()
        val b = primary.vivid()
        return listOf(a, b, lerp(b, a, 0.5f).vivid(), a)
    }

private fun Color.vivid(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1].coerceAtLeast(0.5f) * 1.45f).coerceAtMost(0.9f)
    hsv[2] = 0.9f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
@ReadOnlyComposable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminanceApprox() < 0.5f

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
