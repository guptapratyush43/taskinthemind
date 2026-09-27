package com.taskinthemind.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.taskinthemind.data.Task
import com.taskinthemind.data.TaskList
import com.taskinthemind.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    tasks: List<Task>,
    onNewTask: () -> Unit,
    lists: List<TaskList>,
    selectedList: Int?,
    onSelectList: (Int?) -> Unit,
    onCreateList: (String) -> Unit,
    onRenameList: (Int, String) -> Unit,
    onDeleteList: (Int) -> Unit,
    onOpenTask: (Task) -> Unit,
    onToggleDone: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    showCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(15_000); value = System.currentTimeMillis() }
    }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val needsSetup = remember(resumes) { AlarmPermissions.missingCritical(context) }

    val visible = if (selectedList == null) tasks else tasks.filter { it.listId == selectedList }
    val listNames = lists.associate { it.id to it.name }
    val upcoming = visible.filter { !it.done && it.triggerAt > now }
    val overdue = visible.filter { !it.done && it.triggerAt <= now }
    val done = visible.filter { it.done }.sortedByDescending { it.triggerAt }

    val listState = rememberLazyListState()
    val bottomFade by animateFloatAsState(if (listState.canScrollForward) 0.92f else 0f, tween(250), label = "fade")

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        // Header and list tabs stay pinned; only the tasks scroll.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Fmt.longDay(now).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Task in the Mind", style = MaterialTheme.typography.displaySmall, color = scheme.onBackground)
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(46.dp)
                            .background(scheme.surface, CircleShape)
                            .border(1.dp, scheme.outline, CircleShape)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = scheme.onSurfaceVariant)
                    }
                }
        ListTabs(lists, selectedList, onSelectList, onCreateList, onRenameList, onDeleteList)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Cards melt into the bottom edge while there is more list below;
                // the fade lifts once the end is reached.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (bottomFade > 0f) {
                        val h = 120.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomFade)),
                                startY = size.height - h,
                                endY = size.height
                            ),
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            if (needsSetup) item {
                WarmCard(
                    background = scheme.primaryContainer,
                    borderColor = scheme.primary.copy(alpha = 0.35f),
                    padding = 14.dp,
                    onClick = onOpenSettings
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Alarms may not pop up", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                            Text("Tap to allow what's needed in Settings", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                item { EmptyState(onRestore = onOpenSettings) }
            } else if (visible.isEmpty()) {
                item { ListEmpty(listNames[selectedList] ?: "this list") }
            }

            val row: @Composable (Task) -> Unit = { t ->
                TaskRow(t, now, onOpenTask, onToggleDone, onLongPress = { menuFor = t.id }, tag = if (selectedList == null) t.listId?.let(listNames::get) else null) {
                    TaskMenu(menuFor == t.id, t.done, onDismiss = { menuFor = null }, onToggleDone = { menuFor = null; onToggleDone(t) }) { menuFor = null; pendingDelete = t }
                }
            }
            upcoming.firstOrNull()?.let { next ->
                item(key = "next") {
                    NextUpCard(next, now, onClick = { onOpenTask(next) }, onLongPress = { menuFor = next.id }) {
                        TaskMenu(menuFor == next.id, next.done, onDismiss = { menuFor = null }, onToggleDone = { menuFor = null; onToggleDone(next) }) { menuFor = null; pendingDelete = next }
                    }
                }
            }
            if (upcoming.size > 1) {
                item { SectionLabel("Later", Modifier.padding(top = 10.dp)) }
                items(upcoming.drop(1), key = { it.id }) { row(it) }
            }
            if (overdue.isNotEmpty()) {
                item { SectionLabel("Overdue", Modifier.padding(top = 10.dp)) }
                items(overdue, key = { it.id }) { row(it) }
            }
            if (done.isNotEmpty()) {
                item(key = "completed-header") {
                    // A full, finger-sized ribbon (like Google Tasks) rather than a thin label.
                    val turn by animateFloatAsState(if (showCompleted) 180f else 0f, tween(220), label = "chevron")
                    WarmCard(
                        background = scheme.surfaceVariant,
                        padding = 0.dp,
                        onClick = onToggleCompleted,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp)
                        ) {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Completed tasks", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .background(scheme.surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, scheme.outline, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("${done.size}", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Outlined.ExpandMore,
                                contentDescription = if (showCompleted) "Hide completed tasks" else "Show completed tasks",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp).rotate(turn)
                            )
                        }
                    }
                }
                if (showCompleted) items(done, key = { it.id }) { row(it) }
            }
        }
        }

        ExtendedFloatingActionButton(
            onClick = onNewTask,
            shape = RoundedCornerShape(18.dp),
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New task", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
        )
    }

    pendingDelete?.let { task ->
        ConfirmDeleteDialog(
            title = task.title,
            onDismiss = { pendingDelete = null },
            onConfirm = { onDelete(task); pendingDelete = null }
        )
    }
}

/** "8:30 PM" for today, otherwise "Tomorrow · 8:30 PM" or "Sat, 27 Sep · 8:30 PM". */
private fun whenLabel(ms: Long, now: Long): String {
    val day = Fmt.day(ms, now)
    return if (day == "Today") Fmt.time(ms) else "$day · ${Fmt.time(ms)}"
}

@Composable
private fun NextUpCard(task: Task, now: Long, onClick: () -> Unit, onLongPress: () -> Unit, menu: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box {
        WarmCard(background = scheme.primary, borderColor = scheme.primary, padding = 20.dp, onClick = onClick, onLongClick = onLongPress) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "NEXT UP · ${Fmt.relative(task.triggerAt, now).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimary.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(task.title, style = MaterialTheme.typography.displaySmall, color = scheme.onPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    task.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onPrimary.copy(alpha = 0.88f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(scheme.onPrimary.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(whenLabel(task.triggerAt, now), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
            }
        }
        menu()
    }
}

@Composable
private fun TaskRow(
    task: Task,
    now: Long,
    onOpen: (Task) -> Unit,
    onToggleDone: (Task) -> Unit,
    onLongPress: () -> Unit,
    tag: String? = null,
    menu: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val overdue = !task.done && task.triggerAt <= now

    Box {
        WarmCard(
            padding = 16.dp,
            onClick = { onOpen(task) },
            onLongClick = onLongPress,
            modifier = Modifier.alpha(if (task.done) 0.6f else 1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null
                    )
                    if (task.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val tint = if (overdue) status.danger else scheme.onSurfaceVariant
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            (if (overdue) "Missed · ${whenLabel(task.triggerAt, now)}" else whenLabel(task.triggerAt, now)) + (tag?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = tint
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                DoneCircle(done = task.done, onClick = { onToggleDone(task) })
            }
        }
        menu()
    }
}

@Composable
private fun TaskMenu(expanded: Boolean, done: Boolean, onDismiss: () -> Unit, onToggleDone: () -> Unit, onDelete: () -> Unit) {
    val danger = LocalStatusColors.current.danger
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        offset = DpOffset(16.dp, 0.dp)
    ) {
        DropdownMenuItem(
            text = { Text(if (done) "Mark as not done" else "Mark as done", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface) },
            leadingIcon = { Icon(if (done) Icons.Outlined.RemoveDone else Icons.Outlined.TaskAlt, contentDescription = null, tint = scheme.primary) },
            onClick = onToggleDone
        )
        HairLine(Modifier.padding(horizontal = 12.dp))
        DropdownMenuItem(
            text = { Text("Delete task", style = MaterialTheme.typography.labelLarge, color = danger) },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = danger) },
            onClick = onDelete
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val danger = LocalStatusColors.current.danger
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(scheme.background, RoundedCornerShape(24.dp))
                .border(1.dp, scheme.outline, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp).background(danger.copy(alpha = 0.12f), CircleShape)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = danger, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Delete this task?", style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "“$title” and its alarm will be removed. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, scheme.outline),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Keep", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = danger, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Delete", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun DoneCircle(done: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val success = LocalStatusColors.current.success
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .background(if (done) success else Color.Transparent, CircleShape)
            .border(1.5.dp, if (done) success else scheme.outline, CircleShape)
            .clickable(onClick = onClick)
    ) {
        if (done) Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun EmptyState(onRestore: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 36.dp)) {
        IconBubble(Icons.Outlined.Alarm)
        Spacer(Modifier.height(22.dp))
        Text("A clear mind", style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a task with a time. When it arrives, your phone rings like an alarm until you act on it.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(30.dp))
        WarmCard {
            Feature(Icons.Outlined.Alarm, "Rings, not pings", "A real alarm with your chosen tone, looping until you tap Got it.")
            Spacer(Modifier.height(16.dp))
            HairLine()
            Spacer(Modifier.height(16.dp))
            Feature(Icons.Outlined.Snooze, "Reschedule in a tap", "Push it 5, 10 or 15 minutes, or drag the clock hands to any time.")
        }
        Spacer(Modifier.height(10.dp))
        // A fresh install is the moment people look for their old tasks.
        TextButton(onClick = onRestore) {
            Text("New phone? Restore from Google Drive", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        }
    }
}

@Composable
fun Feature(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
