package app.cuisine.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import app.cuisine.ui.components.rememberExpandedSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisine.R
import app.cuisine.data.Flavor
import app.cuisine.data.ThemeMode
import app.cuisine.ui.CuisineViewModel
import app.cuisine.ui.components.MorphShape
import app.cuisine.ui.components.SectionLabel
import app.cuisine.ui.components.SegmentedAction
import app.cuisine.ui.components.SegmentedList
import app.cuisine.ui.theme.colorSchemeFor
import app.cuisine.ui.theme.dynamicColorAvailable
import app.cuisine.ui.theme.isAppInDarkTheme
import java.time.LocalDate

@Composable
fun SettingsSheet(vm: CuisineViewModel, onDismiss: () -> Unit) {
    val social by vm.social.collectAsStateWithLifecycle()
    val settings = social.settings
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = 8.dp))

            SectionLabel("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                val modes = ThemeMode.entries
                modes.forEachIndexed { index, mode ->
                    ToggleButton(
                        checked = settings.themeMode == mode,
                        onCheckedChange = { vm.setThemeMode(mode) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painterResource(
                                when (mode) {
                                    ThemeMode.System -> R.drawable.ic_contrast
                                    ThemeMode.Light -> R.drawable.ic_light_mode
                                    ThemeMode.Dark -> R.drawable.ic_dark_mode
                                },
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(mode.name)
                    }
                }
            }

            SectionLabel("Flavour")
            Text(
                "Pick a palette for Cuisine, or let your wallpaper choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                maxItemsInEachRow = 3,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Flavor.entries
                    .filter { it != Flavor.Wallpaper || dynamicColorAvailable }
                    .forEach { flavor ->
                        val scheme = remember(flavor, dark) { colorSchemeFor(flavor, dark, context) }
                        FlavorSwatch(
                            label = flavor.label,
                            scheme = scheme,
                            selected = settings.flavor == flavor,
                            onClick = { vm.setFlavor(flavor) },
                        )
                    }
            }

            SectionLabel("Feed")
            SegmentedList(
                buildList {
                    add(
                        SegmentedAction(
                            icon = R.drawable.ic_image,
                            title = "Include screenshots",
                            subtitle = "Show screenshots and screen recordings in your feed and reels",
                            trailing = {
                                Switch(checked = settings.feedIncludesScreenshots, onCheckedChange = { vm.setFeedScreenshots(it) })
                            },
                        ) { vm.setFeedScreenshots(!settings.feedIncludesScreenshots) },
                    )
                    if (social.hidden.isNotEmpty()) {
                        add(
                            SegmentedAction(
                                icon = R.drawable.ic_restart_alt,
                                title = "Restore hidden posts",
                                subtitle = "${social.hidden.size} hidden from your feed",
                            ) { vm.restoreHidden() },
                        )
                    }
                },
            )

            SectionLabel("Backup")
            Text(
                "Your photos stay where they are. This is everything Cuisine adds to them: people, tags, " +
                    "likes, captions and notes. With Google backup on, Android also saves it for you automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
            )
            val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                uri?.let(vm::exportBackup)
            }
            val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let(vm::importBackup)
            }
            SegmentedList(
                listOf(
                    SegmentedAction(R.drawable.ic_backup, "Save a backup file", "Keep it in Drive, email it to yourself, or copy it to a new phone") {
                        exporter.launch("Cuisine backup ${LocalDate.now()}.json")
                    },
                    SegmentedAction(R.drawable.ic_restore, "Restore from a file", "Works on a new phone too: Cuisine finds the same photos by name, date and size") {
                        importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    },
                ),
            )

            SectionLabel("About")
            SegmentedList(
                listOf(
                    SegmentedAction(R.drawable.ic_cookie, "Cuisine 1.0", "Your gallery, served fresh. Everything stays on this device.") {},
                    SegmentedAction(
                        R.drawable.ic_face,
                        "Face recognition",
                        "On-device: ML Kit face detection (Google Play services) and MobileFaceNet from FaceRecognitionAuth, BSD 3-Clause",
                    ) {},
                    SegmentedAction(R.drawable.ic_text_fields, "Google Sans Flex", "Licensed under the SIL Open Font License 1.1") {},
                ),
            )
        }
    }
}

/**
 * A palette preview split like the system wallpaper picker. The chosen one blooms from a
 * circle into a cookie.
 */
@Composable
private fun FlavorSwatch(label: String, scheme: ColorScheme, selected: Boolean, onClick: () -> Unit) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "swatchMorph",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(MorphShape(morph, progress)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRect(scheme.primary, size = Size(w, h / 2))
                drawRect(scheme.secondaryContainer, topLeft = Offset(0f, h / 2), size = Size(w / 2, h / 2))
                drawRect(scheme.tertiaryContainer, topLeft = Offset(w / 2, h / 2), size = Size(w / 2, h / 2))
            }
            if (selected) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .then(Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check_circle_filled),
                        contentDescription = "Selected",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
