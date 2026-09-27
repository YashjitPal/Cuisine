package app.cuisine.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** One row of an expressive segmented list: the group's first and last rows get the round corners. */
data class SegmentedAction(
    @DrawableRes val icon: Int,
    val title: String,
    val subtitle: String? = null,
    val destructive: Boolean = false,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
fun SegmentedList(actions: List<SegmentedAction>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        actions.forEachIndexed { index, action ->
            SegmentedRow(action, index, actions.size)
        }
    }
}

@Composable
fun SegmentedRow(action: SegmentedAction, index: Int, count: Int) {
    val tint = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        onClick = action.onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(),
        leadingContent = { Icon(painterResource(action.icon), contentDescription = null, tint = tint) },
        supportingContent = action.subtitle?.let { { Text(it) } },
        trailingContent = action.trailing,
    ) {
        Text(
            text = action.title,
            color = if (action.destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
}

/** A modal sheet that opens straight to its full height. */
@Composable
fun rememberExpandedSheetState(): SheetState =
    rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 20.dp, bottom = 10.dp),
    )
}