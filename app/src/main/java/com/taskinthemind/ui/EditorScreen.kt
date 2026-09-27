package com.taskinthemind.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.taskinthemind.data.Task
import com.taskinthemind.data.TaskList
import com.taskinthemind.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun EditorScreen(
    task: Task?,
    onBack: () -> Unit,
    lists: List<TaskList>,
    initialListId: Int?,
    onSave: (title: String, description: String, triggerAt: Long, listId: Int?) -> Unit,
    onDelete: (Int) -> Unit,
    onToggleDone: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current

    // New tasks default to half an hour from now, rounded up to five minutes.
    val start = remember(task) {
        task?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerAt), ZoneId.systemDefault()) }
            ?: LocalDateTime.now().plusMinutes(30).let { it.plusMinutes(((5 - it.minute % 5) % 5).toLong()) }
    }
    var title by rememberSaveable { mutableStateOf(task?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(task?.description ?: "") }
    var dateEpochDay by rememberSaveable { mutableStateOf(start.toLocalDate().toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf(start.hour) }
    var minute by rememberSaveable { mutableIntStateOf(start.minute) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showClock by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val triggerAt = Fmt.millisOf(date, hour, minute)
    val now = System.currentTimeMillis()
    val inFuture = triggerAt > now
    val canSave = title.isNotBlank() && inFuture

    // Which quick pick set the time; cleared as soon as the date or time is changed by hand.
    var listId by rememberSaveable { mutableStateOf(task?.listId ?: initialListId) }
    var quick by rememberSaveable { mutableStateOf<Int?>(null) }

    fun setFrom(dt: LocalDateTime) {
        dateEpochDay = dt.toLocalDate().toEpochDay(); hour = dt.hour; minute = dt.minute
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (task == null) "New task" else "Edit task", onBack) {
            if (task != null) IconButton(onClick = { onToggleDone(task.id) }) {
                Icon(
                    if (task.done) Icons.Outlined.RemoveDone else Icons.Outlined.TaskAlt,
                    contentDescription = if (task.done) "Mark as not done" else "Mark as done",
                    tint = scheme.primary
                )
            }
            if (task != null) IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = status.danger)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outline,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface,
                cursorColor = scheme.primary
            )

            SectionLabel("Heading")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("What needs doing?") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Description")
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Notes, details, anything to remember") },
                minLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            if (lists.isNotEmpty()) {
                SectionLabel("List")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip("No list", listId == null) { listId = null }
                    lists.forEach { l -> QuickChip(l.name, listId == l.id) { listId = l.id } }
                }
                Spacer(Modifier.height(24.dp))
            }
            SectionLabel("When")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WhenTile(Icons.Outlined.CalendarMonth, "Date", Fmt.date(date), Modifier.weight(1f)) { showDate = true }
                WhenTile(Icons.Outlined.Schedule, "Time", Fmt.time(triggerAt), Modifier.weight(1f)) { showClock = true }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val base = LocalDateTime.now().withSecond(0).withNano(0)
                QuickChip("In 15 min", quick == 0) { quick = 0; setFrom(base.plusMinutes(15)) }
                QuickChip("In 1 hour", quick == 1) { quick = 1; setFrom(base.plusHours(1)) }
                QuickChip("This evening", quick == 2) { quick = 2; setFrom(base.toLocalDate().atTime(18, 0).let { if (it.isAfter(base)) it else it.plusDays(1) }) }
                QuickChip("Tomorrow 9 AM", quick == 3) { quick = 3; setFrom(base.toLocalDate().plusDays(1).atTime(9, 0)) }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                if (inFuture) "Rings ${Fmt.relative(triggerAt, now)} · ${Fmt.day(triggerAt, now)}, ${Fmt.time(triggerAt)}"
                else "That time has already passed. Pick a later one.",
                style = MaterialTheme.typography.bodySmall,
                color = if (inFuture) scheme.onSurfaceVariant else status.danger,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }

        PrimaryButton(
            text = if (task == null) "Set alarm" else "Save changes",
            icon = Icons.Outlined.Alarm,
            enabled = canSave,
            onClick = { onSave(title, description, triggerAt, listId) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }

    if (showDate) {
        CalendarDialog(
            initial = date,
            onDismiss = { showDate = false },
            onConfirm = { dateEpochDay = it.toEpochDay(); quick = null; showDate = false }
        )
    }

    if (showClock) {
        ClockDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showClock = false },
            onConfirm = { h, m -> hour = h; minute = m; quick = null; showClock = false }
        )
    }

    if (confirmDelete && task != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this task?") },
            text = { Text("“${task.title}” and its alarm will be removed.") },
            confirmButton = { TextButton(onClick = { onDelete(task.id) }) { Text("Delete", color = status.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
            containerColor = scheme.background
        )
    }
}

@Composable
private fun WhenTile(icon: ImageVector, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    WarmCard(modifier = modifier, padding = 16.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Selected looks like the app's other active pills: filled accent, light text.
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(10.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) scheme.primary else scheme.surface,
            labelColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
        ),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = if (selected) scheme.primary else scheme.outline)
    )
}
