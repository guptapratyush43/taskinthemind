package com.taskinthemind.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.taskinthemind.data.TaskList
import com.taskinthemind.ui.theme.LocalStatusColors

/**
 * Google Tasks-style tab strip: All tasks, the user's lists, then New list.
 * Scrolls sideways when there are many. Long-press a list to rename or delete it.
 */
@Composable
fun ListTabs(
    lists: List<TaskList>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val danger = LocalStatusColors.current.danger
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var naming by remember { mutableStateOf<TaskList?>(null) } // id 0 = a new list
    var deleting by remember { mutableStateOf<TaskList?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp)
        ) {
            ListTab("All tasks", selected == null, onClick = { onSelect(null) }, onLongClick = null)
            lists.forEach { list ->
                Box {
                    ListTab(list.name, selected == list.id, onClick = { onSelect(list.id) }, onLongClick = { menuFor = list.id })
                    DropdownMenu(
                        expanded = menuFor == list.id,
                        onDismissRequest = { menuFor = null },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outline)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename list", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface) },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null, tint = scheme.primary) },
                            onClick = { menuFor = null; naming = list }
                        )
                        HairLine(Modifier.padding(horizontal = 12.dp))
                        DropdownMenuItem(
                            text = { Text("Delete list", style = MaterialTheme.typography.labelLarge, color = danger) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = danger) },
                            onClick = { menuFor = null; deleting = list }
                        )
                    }
                }
            }
            // New list, styled like a tab so it sits naturally at the end of the strip.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { naming = TaskList(0, "") }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New list", style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            }
        }
        HairLine()
    }

    naming?.let { list ->
        ListNameDialog(
            isNew = list.id == 0,
            initial = list.name,
            onDismiss = { naming = null },
            onConfirm = { name ->
                naming = null
                if (list.id == 0) onCreate(name) else onRename(list.id, name)
            }
        )
    }

    deleting?.let { list ->
        WarmDialog(
            icon = Icons.Outlined.DeleteOutline,
            accent = danger,
            title = "Delete “${list.name}”?",
            confirmLabel = "Delete",
            onConfirm = { deleting = null; onDelete(list.id) },
            dismissLabel = "Keep",
            onDismiss = { deleting = null }
        ) {
            Text(
                "The list goes away. Its tasks and alarms stay safe under All tasks.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListTab(name: String, active: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleSmall
    val textWidth = tabTextWidth(name, style)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp)
    ) {
        // Selected tab gets a soft tinted pill behind its name, plus the underline.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 6.dp, bottom = 6.dp)
                .background(if (active) scheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                .heightIn(min = 36.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                name,
                style = style,
                color = if (active) scheme.primary else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.width(textWidth)
            )
        }
        // Underline marker, as wide as the label.
        Box(
            Modifier
                .width(textWidth + 20.dp)
                .height(3.dp)
                .background(if (active) scheme.primary else Color.Transparent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
        )
    }
}

/**
 * Width that keeps a tab label to at most two lines: short names sit on one
 * line, longer ones wrap once, and anything that would need a third line makes
 * the tab wider instead. The strip scrolls, so width is never a problem.
 */
@Composable
private fun tabTextWidth(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density) {
        with(density) {
            val base = 150.dp.roundToPx()
            val single = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width + 2
            if (single <= base) {
                single.toDp()
            } else {
                var w = maxOf(base, (single * 0.55f).toInt())
                val step = 8.dp.roundToPx()
                while (w < single && measurer.measure(text, style, constraints = Constraints(maxWidth = w)).lineCount > 2) w += step
                minOf(w, single).toDp()
            }
        }
    }
}

@Composable
private fun ListNameDialog(isNew: Boolean, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = { if (name.isNotBlank()) onConfirm(name.trim()) }

    WarmDialog(
        icon = if (isNew) Icons.Outlined.PlaylistAdd else Icons.Outlined.DriveFileRenameOutline,
        accent = scheme.primary,
        title = if (isNew) "New list" else "Rename list",
        confirmLabel = if (isNew) "Create" else "Save",
        onConfirm = submit,
        dismissLabel = "Cancel",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 40) name = it },
            placeholder = { Text("Work, Groceries, Gym…") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outline,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface,
                cursorColor = scheme.primary
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
    }
}

/** Shown when a list has nothing in it yet. */
@Composable
fun ListEmpty(name: String) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
        IconBubble(Icons.Outlined.PlaylistAdd)
        Spacer(Modifier.height(20.dp))
        Text("Nothing in $name yet", style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap New task and it lands right here.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
