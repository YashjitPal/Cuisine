package app.cuisine.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.cuisine.R

/**
 * A Google Sans Flex instance. The bundled variable font keeps the weight, width, optical size
 * and roundness axes; every style below picks its own point on them.
 */
@OptIn(ExperimentalTextApi::class)
fun flexFamily(
    weight: Int,
    width: Float = 100f,
    roundness: Float = 0f,
    opticalSize: Float = 18f,
): FontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
            FontVariation.Setting("ROND", roundness),
            FontVariation.Setting("opsz", opticalSize),
        ),
    ),
)

private fun style(
    size: Int,
    line: Int,
    weight: Int,
    tracking: Double = 0.0,
    roundness: Float = 0f,
    width: Float = 100f,
) = TextStyle(
    fontFamily = flexFamily(weight, width, roundness, size.toFloat()),
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val CuisineTypography = Typography(
    displayLarge = style(57, 64, 440, -0.5),
    displayMedium = style(45, 52, 440, -0.25),
    displaySmall = style(36, 44, 450),
    headlineLarge = style(32, 40, 460),
    headlineMedium = style(28, 36, 460),
    headlineSmall = style(24, 32, 480),
    titleLarge = style(22, 28, 500),
    titleMedium = style(16, 24, 560, 0.1),
    titleSmall = style(14, 20, 560, 0.1),
    bodyLarge = style(16, 24, 400, 0.2),
    bodyMedium = style(14, 20, 400, 0.15),
    bodySmall = style(12, 16, 420, 0.3),
    labelLarge = style(14, 20, 560, 0.1),
    labelMedium = style(12, 16, 580, 0.3),
    labelSmall = style(11, 16, 580, 0.4),
    displayLargeEmphasized = style(57, 64, 720, -1.0, roundness = 100f),
    displayMediumEmphasized = style(45, 52, 720, -0.75, roundness = 100f),
    displaySmallEmphasized = style(36, 44, 700, -0.5, roundness = 100f),
    headlineLargeEmphasized = style(32, 40, 700, -0.25, roundness = 100f),
    headlineMediumEmphasized = style(28, 36, 700, -0.25, roundness = 100f),
    headlineSmallEmphasized = style(24, 32, 680, roundness = 80f),
    titleLargeEmphasized = style(22, 28, 680, roundness = 60f),
    titleMediumEmphasized = style(16, 24, 700, 0.1, roundness = 40f),
    titleSmallEmphasized = style(14, 20, 700, 0.1, roundness = 40f),
    bodyLargeEmphasized = style(16, 24, 600, 0.2),
    bodyMediumEmphasized = style(14, 20, 600, 0.15),
    bodySmallEmphasized = style(12, 16, 620, 0.3),
    labelLargeEmphasized = style(14, 20, 720, 0.1),
    labelMediumEmphasized = style(12, 16, 720, 0.3),
    labelSmallEmphasized = style(11, 16, 720, 0.4),
)

/** The wordmark: heavy, wide and fully rounded, like a menu header. */
val WordmarkStyle = TextStyle(
    fontFamily = flexFamily(weight = 860, width = 118f, roundness = 100f, opticalSize = 40f),
    fontWeight = FontWeight(860),
    fontSize = 30.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.9).sp,
)
