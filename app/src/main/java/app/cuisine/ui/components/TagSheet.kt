package app.cuisine.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cuisine.R
import app.cuisine.data.Tag

/**
 * Tags for one photo or many. A chip is on when every photo being tagged has it; tapping it adds
 * the tag to all of them, or takes it off all of them.
 */
@Composable
fun TagSheet(
    tags: List<Tag>,
    /** How many of the items being tagged carry each tag id. */
    counts: Map<String, Int>,
    total: Int,
    onToggle: (tagId: String, on: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    fun create() {
        if (draft.isBlank()) return
        onCreate(draft)
        draft = ""
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                .imePadding(),
        ) {
            Text(
                text = if (total == 1) "Tags" else "Tag $total items",
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tags become pills in Photos, so you can find these again in a tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(32) },
                    label = { Text("New tag") },
                    singleLine = true,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_sell), contentDescription = null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = ::create,
                    enabled = draft.isNotBlank(),
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Add tag")
                }
            }
            Spacer(Modifier.height(18.dp))
            if (tags.isEmpty()) {
                Text(
                    text = "No tags yet. Type one above, like \"Trips\" or \"Food\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tags.sortedBy { it.name.lowercase() }.forEach { tag ->
                        val on = (counts[tag.id] ?: 0) >= total
                        FilterChip(
                            selected = on,
                            onClick = { onToggle(tag.id, !on) },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Icon(
                                    painterResource(if (on) R.drawable.ic_check else R.drawable.ic_sell),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameTagDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_sell), contentDescription = null) },
        title = { Text("Rename tag") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = MaterialTheme.shapes.large,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
