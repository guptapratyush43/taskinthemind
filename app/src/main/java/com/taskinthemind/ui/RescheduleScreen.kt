package com.taskinthemind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.taskinthemind.data.Task
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.draw.clip
import com.taskinthemind.ui.theme.LocalStatusColors

private val DELAYS = listOf(5, 10, 15)

@Composable
fun RescheduleScreen(task: Task, onBack: () -> Unit, onConfirm: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(10_000); value = System.currentTimeMillis() }
    }
    // Either a delay in minutes or an exact minute-of-day; never both.
    var delayMin by rememberSaveable { mutableStateOf<Int?>(null) }
    var exactMinuteOfDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var draft by rememberSaveable { mutableStateOf(LocalTime.now().plusMinutes(30).let { it.hour * 60 + it.minute }) }

    // A chosen day pins the date; otherwise the time lands on its next occurrence (today or tomorrow).
    var exactDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    fun resolve(minuteOfDay: Int): Long = exactDay?.let { Fmt.millisOf(LocalDate.ofEpochDay(it), minuteOfDay / 60, minuteOfDay % 60) }
        ?: Fmt.nextOccurrence(minuteOfDay / 60, minuteOfDay % 60, now)

    val exactAt = exactMinuteOfDay?.let(::resolve)
    val targetAt = delayMin?.let { now + it * 60_000L } ?: exactAt?.takeIf { it > now }

    Column(Modifier.fillMaxSize()) {
        TopBar("Reschedule", onBack)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            // No scrolling: fixed-height parts stay compact and the clock takes whatever height is left.
            IconBubble(Icons.Outlined.Snooze, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                task.title,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text("How much later should it ring?", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))
            SectionLabel("Quick delay", Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DELAYS.forEach { m ->
                    val active = delayMin == m
                    WarmCard(
                        modifier = Modifier.weight(1f),
                        background = if (active) scheme.primary else scheme.surface,
                        borderColor = if (active) scheme.primary else scheme.outline,
                        padding = 12.dp,
                        onClick = { delayMin = m; exactMinuteOfDay = null }
                    ) {
                        val fg = if (active) scheme.onPrimary else scheme.onSurface
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$m", style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 36.sp), color = fg)
                            Spacer(Modifier.width(4.dp))
                            Text("min", style = MaterialTheme.typography.titleSmall, color = fg, modifier = Modifier.padding(bottom = 5.dp))
                        }
                        Text(
                            Fmt.time(now + m * 60_000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) scheme.onPrimary.copy(alpha = 0.85f) else scheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // The clock is live right here: any drag, tap, AM/PM or date change picks exact mode.
            val active = exactMinuteOfDay != null
            val shown = exactMinuteOfDay ?: draft
            val pick = { minuteOfDay: Int -> draft = minuteOfDay; exactMinuteOfDay = minuteOfDay; delayMin = null }
            val shownAt = resolve(shown)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                SectionLabel("Or pick a date & time", Modifier.weight(1f).padding(top = 8.dp))
                // Date chip: shows which day the picked time lands on; tap to choose another.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) scheme.primaryContainer else scheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, if (active) scheme.primary else scheme.outline, RoundedCornerShape(10.dp))
                        .clickable { showCalendar = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Fmt.day(shownAt, now),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) scheme.primary else scheme.onSurface
                    )
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "Change date", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            if (showCalendar) {
                CalendarDialog(
                    initial = Instant.ofEpochMilli(shownAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                    onDismiss = { showCalendar = false },
                    onConfirm = { date ->
                        exactDay = date.toEpochDay()
                        pick(shown)
                        showCalendar = false
                    }
                )
            }
            WarmCard(
                modifier = Modifier.weight(1f),
                borderColor = if (active) scheme.primary else scheme.outline,
                borderWidth = if (active) 2.dp else 1.dp,
                padding = 14.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                    TimeReadout(shown / 60, shown % 60, onHourChange = { pick(it * 60 + shown % 60) }, dimmed = !active)
                    // The dial is the largest square that fits the space left, so it
                    // shrinks on short screens instead of pushing the caption off.
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)) {
                        ClockDial(
                            hour = shown / 60,
                            minute = shown % 60,
                            onTimeChange = { h, m -> pick(h * 60 + m) }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (exactAt != null && exactAt <= now) {
                            Text("That time has passed. Pick a later one.", style = MaterialTheme.typography.titleSmall, color = LocalStatusColors.current.danger)
                        } else if (exactAt != null) {
                            Text(
                                "Rings at ${Fmt.time(exactAt)} · ${Fmt.day(exactAt, now)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.primary
                            )
                        } else {
                            Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Drag the hands to set a time", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        PrimaryButton(
            text = "Reschedule",
            icon = Icons.Outlined.Snooze,
            enabled = targetAt != null,
            onClick = { targetAt?.let(onConfirm) },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)
        )
    }
}
