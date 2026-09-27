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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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

/** Extra hold after the menu opens before a tab can be dragged. */
private const val REORDER_HOLD_MS = 1200L

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
    onDelete: (Int) -> Unit,
    onReorder: (List<Int>) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val danger = LocalStatusColors.current.danger
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var naming by remember { mutableStateOf<TaskList?>(null) } // id 0 = a new list
    var deleting by remember { mutableStateOf<TaskList?>(null) }
    // Where each tab sits, so a swipe can scroll the strip to the active one.
    val scroll = rememberScrollState()
    val positions = remember { mutableStateMapOf<Int, Int>() }
    val margin = with(LocalDensity.current) { 48.dp.roundToPx() }
    LaunchedEffect(selected) {
        positions[selected ?: -1]?.let { scroll.animateScrollTo((it - margin).coerceAtLeast(0)) }
    }

    // Reordering: hold a list tab, then drag it sideways. "All tasks" never moves.
    val context = LocalContext.current
    val widths = remember { mutableStateMapOf<Int, Int>() }
    var dragOrder by remember { mutableStateOf<List<Int>?>(null) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var fingerX by remember { mutableFloatStateOf(0f) } // finger x in the strip's own coordinates
    var grabOffset by remember { mutableFloatStateOf(0f) } // finger x within the tab when it was picked up
    var settlingFrom by remember { mutableStateOf<Int?>(null) } // slot x before a swap, until layout catches up
    val latestLists by rememberUpdatedState(lists)
    val shown = dragOrder?.mapNotNull { id -> lists.firstOrNull { it.id == id } } ?: lists
    val latestShown by rememberUpdatedState(shown)

    /**
     * Where the held tab's left edge should be drawn: under the finger, but kept
     * between the end of "All tasks" and the end of the last list tab.
     */
    fun heldLeft(id: Int): Float {
        val w = widths[id] ?: 0
        val min = ((positions[-1] ?: 0) + (widths[-1] ?: 0)).toFloat()
        val last = dragOrder?.lastOrNull() ?: id
        val max = ((positions[last] ?: 0) + (widths[last] ?: 0) - w).toFloat().coerceAtLeast(min)
        return (fingerX - grabOffset).coerceIn(min, max)
    }

    /** Swaps the held tab with a neighbour once its leading edge passes the neighbour's middle. */
    fun reorderCheck(id: Int) {
        val order = dragOrder ?: return
        if (settlingFrom != null) return
        val i = order.indexOf(id)
        val w = widths[id] ?: return
        // Edges, not centres: a wide tab can then pass a narrow one (and vice versa).
        val left = heldLeft(id)
        val right = left + w
        val next = order.getOrNull(i + 1)
        val prev = order.getOrNull(i - 1)
        val nx = next?.let(positions::get); val nw = next?.let(widths::get)
        val px = prev?.let(positions::get); val pw = prev?.let(widths::get)
        val swapTo = when {
            nx != null && nw != null && right > nx + nw / 2f -> i + 1
            px != null && pw != null && left < px + pw / 2f -> i - 1
            else -> return
        }
        settlingFrom = positions[id]
        dragOrder = order.toMutableList().apply { add(swapTo, removeAt(i)) }
        Haptics.tick(context)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 10.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // A tap or a scroll resolves before the long-press timeout; only a steady hold gets past it.
                        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var done = false
                            while (!done) {
                                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                done = c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                            }
                            false
                        } ?: true
                        if (!held) return@awaitEachGesture
                        val x0 = down.position.x
                        // Only list tabs move; a hold on All tasks or New list does nothing special.
                        val id = latestShown.firstOrNull { l ->
                            val p = positions[l.id]; val w = widths[l.id]
                            p != null && w != null && x0 >= p && x0 <= p + w
                        }?.id ?: return@awaitEachGesture

                        // Stage 1 - hold: the Rename / Delete menu opens.
                        Haptics.press(context)
                        menuFor = id

                        // Stage 2 - keep holding still: switch to reordering.
                        // Lifting the finger or moving it first just leaves the menu open.
                        var lastX = x0
                        val keepHolding = withTimeoutOrNull(REORDER_HOLD_MS) {
                            var go = true
                            while (go) {
                                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                if (c == null) { go = false } else {
                                    c.consume() // the held tab must not also register a tap
                                    lastX = c.position.x
                                    if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) go = false
                                }
                            }
                            false
                        } ?: true
                        if (!keepHolding) {
                            // Finish the gesture quietly; the menu stays open.
                            while (true) {
                                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                c.consume()
                                if (!c.pressed) break
                            }
                            return@awaitEachGesture
                        }

                        menuFor = null
                        Haptics.press(context)
                        dragOrder = latestLists.map { it.id }
                        grabOffset = x0 - (positions[id] ?: 0)
                        fingerX = lastX
                        draggingId = id
                        while (true) {
                            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            c.consume()
                            if (!c.pressed) break
                            fingerX = c.position.x
                            reorderCheck(id)
                        }
                        val finalOrder = dragOrder
                        draggingId = null
                        settlingFrom = null
                        if (finalOrder != null && finalOrder != latestLists.map { it.id }) onReorder(finalOrder)
                        dragOrder = null
                    }
                }
        ) {
            ListTab(
                "All tasks", selected == null, onClick = { onSelect(null) }, onLongClick = null,
                modifier = Modifier.onGloballyPositioned { positions[-1] = it.positionInParent().x.toInt(); widths[-1] = it.size.width }
            )
            shown.forEach { list -> key(list.id) {
                val dragging = draggingId == list.id
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .onGloballyPositioned {
                            val x = it.positionInParent().x.toInt()
                            positions[list.id] = x
                            widths[list.id] = it.size.width
                            if (list.id == draggingId && settlingFrom != null && x != settlingFrom) settlingFrom = null
                        }
                ) {
                    ListTab(
                        list.name, selected == list.id, onClick = { onSelect(list.id) }, onLongClick = null,
                        lifted = dragging,
                        modifier = Modifier.graphicsLayer {
                            if (dragging) {
                                translationX = heldLeft(list.id) - (positions[list.id] ?: 0)
                                scaleX = 1.06f
                                scaleY = 1.06f
                            }
                        }
                    )
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
                            text = { Text("Delete list", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = scheme.primary) },
                            onClick = { menuFor = null; deleting = list }
                        )
                        HairLine(Modifier.padding(horizontal = 12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.DragIndicator, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Keep holding to reorder", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            } }
            // New list, styled like a tab so it sits naturally at the end of the strip.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .clickable { naming = TaskList(0, "") }
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 6.dp)
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New list", style = MaterialTheme.typography.titleSmall, color = scheme.primary)
                }
                Spacer(Modifier.height(3.dp)) // same slot the tabs use for their underline
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
private fun ListTab(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    lifted: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleSmall
    val textWidth = tabTextWidth(name, style)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp)
    ) {
        // Selected tab gets a soft tinted pill behind its name, plus the underline.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 6.dp, bottom = 6.dp)
                .background(if (active || lifted) scheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
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
            // Drag handle while reordering; drawn in the tab's side margin so the layout doesn't shift.
            if (lifted) Icon(
                Icons.Outlined.DragIndicator,
                contentDescription = "Reordering",
                tint = scheme.primary,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-19).dp).size(14.dp)
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
fun ListEmpty(name: String, canReorder: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
        IconBubble(Icons.Outlined.PlaylistAdd)
        Spacer(Modifier.height(20.dp))
        Text("Nothing in $name yet", style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            if (canReorder) "Tap New task and it lands right here.\nHold a tab a little longer to reorder your lists."
            else "Tap New task and it lands right here.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
