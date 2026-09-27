package com.taskinthemind.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Hand { HOUR, MINUTE }

// The hour hand points straight at its hour so that moving the minute hand
// never nudges it: the two hands are fully independent.
private fun handAngles(hour: Int, minute: Int) = Pair((hour % 12) * 30f, minute * 6f)

/**
 * An analog clock whose hands can be dragged. The short hand sets only the
 * hour, the long hand only the minute; AM/PM is left to the pills beside it.
 * Pass a null [onTimeChange] for a read-only face.
 */
@Composable
fun ClockDial(hour: Int, minute: Int, onTimeChange: ((Int, Int) -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val latestHour by rememberUpdatedState(hour)
    val latestMinute by rememberUpdatedState(minute)
    val latestChange by rememberUpdatedState(onTimeChange)
    var active by remember { mutableStateOf<Hand?>(null) }

    val gestures = if (onTimeChange == null) Modifier else Modifier
        .pointerInput(Unit) {
            var h = 0
            var m = 0
            fun angle(p: Offset): Float {
                val deg = Math.toDegrees(atan2((p.x - size.width / 2f).toDouble(), (size.height / 2f - p.y).toDouble())).toFloat()
                return (deg + 360f) % 360f
            }
            fun tip(deg: Float, len: Float): Offset {
                val r = Math.toRadians(deg.toDouble())
                return Offset(size.width / 2f + len * sin(r).toFloat(), size.height / 2f - len * cos(r).toFloat())
            }
            fun pick(p: Offset): Hand {
                val radius = size.width / 2f
                val (ha, ma) = handAngles(h, m)
                val dh = (p - tip(ha, radius * 0.5f)).getDistance()
                val dm = (p - tip(ma, radius * 0.78f)).getDistance()
                return if (dh < dm) Hand.HOUR else Hand.MINUTE
            }
            fun apply(p: Offset) {
                val deg = angle(p)
                when (active) {
                    Hand.MINUTE -> m = (deg / 6f).roundToInt() % 60
                    Hand.HOUR -> h = (deg / 30f).roundToInt() % 12 + if (h >= 12) 12 else 0
                    null -> return
                }
                latestChange?.invoke(h, m)
            }
            detectDragGestures(
                onDragStart = { start ->
                    h = latestHour; m = latestMinute
                    active = pick(start)
                    apply(start)
                },
                onDragEnd = { active = null },
                onDragCancel = { active = null },
                onDrag = { change, _ -> change.consume(); apply(change.position) }
            )
        }
        .pointerInput(Unit) {
            // A tap moves whichever hand is nearer, handy for quick coarse picks.
            detectTapGestures { p ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val deg = ((Math.toDegrees(atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())).toFloat()) + 360f) % 360f
                val dist = hypot(p.x - c.x, p.y - c.y) / (size.width / 2f)
                val cb = latestChange ?: return@detectTapGestures
                if (dist < 0.62f) {
                    val pm = latestHour >= 12
                    cb((deg / 30f).roundToInt() % 12 + if (pm) 12 else 0, latestMinute)
                } else {
                    cb(latestHour, ((deg / 6f).roundToInt() % 60))
                }
            }
        }

    Canvas(modifier.aspectRatio(1f).then(gestures)) {
        val r = size.minDimension / 2f
        val c = center
        fun point(deg: Float, len: Float): Offset {
            val rad = Math.toRadians(deg.toDouble())
            return Offset(c.x + len * sin(rad).toFloat(), c.y - len * cos(rad).toFloat())
        }

        drawCircle(scheme.surfaceVariant, r)
        drawCircle(scheme.outline, r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))

        for (i in 0 until 60) {
            val major = i % 5 == 0
            val outer = r * 0.93f
            drawLine(
                color = if (major) scheme.onSurfaceVariant else scheme.outline,
                start = point(i * 6f, outer - r * if (major) 0.08f else 0.04f),
                end = point(i * 6f, outer),
                strokeWidth = if (major) r * 0.018f else r * 0.01f,
                cap = StrokeCap.Round
            )
        }

        val numeralStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = (r * 0.13f).toSp(),
            color = scheme.onSurface
        )
        for (n in 1..12) {
            val layout = measurer.measure(n.toString(), numeralStyle)
            val p = point(n * 30f, r * 0.72f)
            drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }

        val (hourAngle, minuteAngle) = handAngles(hour, minute)
        val interactive = onTimeChange != null

        val minuteTip = point(minuteAngle, r * 0.78f)
        if (active == Hand.MINUTE) drawCircle(scheme.onSurface.copy(alpha = 0.12f), r * 0.14f, minuteTip)
        drawLine(scheme.onSurface, c, minuteTip, r * 0.03f, cap = StrokeCap.Round)
        if (interactive) drawCircle(scheme.onSurface, r * 0.055f, minuteTip)

        val hourTip = point(hourAngle, r * 0.5f)
        if (active == Hand.HOUR) drawCircle(scheme.primary.copy(alpha = 0.2f), r * 0.16f, hourTip)
        drawLine(scheme.primary, c, hourTip, r * 0.055f, cap = StrokeCap.Round)
        if (interactive) drawCircle(scheme.primary, r * 0.075f, hourTip)

        drawCircle(scheme.primary, r * 0.05f, c)
        drawCircle(scheme.surface, r * 0.02f, c)
    }
}

/** Shared shell for the time and date pickers so both look like one family. */
@Composable
private fun PickerDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .background(scheme.background, RoundedCornerShape(24.dp))
                .border(1.dp, scheme.outline, RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = scheme.onSurfaceVariant) }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(confirmLabel, icon = null, onClick = onConfirm)
            }
        }
    }
}

/** Big serif digits with AM/PM pills beside them. */
@Composable
fun TimeReadout(hour: Int, minute: Int, onHourChange: (Int) -> Unit, dimmed: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        Text(
            text = "%d:%02d".format(h12, minute),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 56.sp),
            color = if (dimmed) scheme.onSurfaceVariant else scheme.onBackground
        )
        Spacer(Modifier.width(14.dp))
        PillToggle(
            options = listOf("AM", "PM"),
            selected = if (hour >= 12) 1 else 0,
            onSelect = { onHourChange(hour % 12 + if (it == 1) 12 else 0) }
        )
    }
}

/** Full clock picker in a dialog: digital readout, AM/PM pills and the draggable face. */
@Composable
fun ClockDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String = "Set the time",
    confirmLabel: String = "Set time",
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hour by rememberSaveable { mutableIntStateOf(initialHour) }
    var minute by rememberSaveable { mutableIntStateOf(initialMinute) }

    PickerDialog(title, confirmLabel, onDismiss, onConfirm = { onConfirm(hour, minute) }) {
        TimeReadout(hour, minute, onHourChange = { hour = it })
        Spacer(Modifier.height(18.dp))
        ClockDial(hour, minute, onTimeChange = { h, m -> hour = h; minute = m }, modifier = Modifier.fillMaxWidth(0.92f))
        Spacer(Modifier.height(14.dp))
        Text(
            "Drag the short hand for the hour and the long hand for minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Month grid in the same warm dialog as the clock. Past days are not selectable. */
@Composable
fun CalendarDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var selectedDay by rememberSaveable { mutableLongStateOf(initial.toEpochDay()) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    var pickerYear by rememberSaveable { mutableIntStateOf(initial.year) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val month = YearMonth.from(initial).plusMonths(monthOffset.toLong())

    PickerDialog("Pick a date", "Set date", onDismiss, onConfirm = { onConfirm(selected) }) {
        Text(
            DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US).format(selected),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp),
            color = scheme.onBackground
        )
        Spacer(Modifier.height(18.dp))
        val thisMonth = YearMonth.from(today)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (pickingMonth) {
                MonthArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, enabled = pickerYear > today.year) { pickerYear-- }
            } else {
                MonthArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, enabled = month > thisMonth) { monthOffset-- }
            }
            // Tapping the title flips between the day grid and a month/year chooser.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        pickerYear = month.year
                        pickingMonth = !pickingMonth
                    }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    if (pickingMonth) "$pickerYear" else DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US).format(month),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Icon(
                    if (pickingMonth) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Choose month",
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (pickingMonth) {
                MonthArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, enabled = true) { pickerYear++ }
            } else {
                MonthArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, enabled = true) { monthOffset++ }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (pickingMonth) Column(
            Modifier
                .fillMaxWidth()
                .background(scheme.surface, RoundedCornerShape(16.dp))
                .border(1.dp, scheme.outline, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            for (row in 0 until 4) {
                Row {
                    for (col in 0 until 3) {
                        val ym = YearMonth.of(pickerYear, row * 3 + col + 1)
                        val isCurrent = ym == month
                        val past = ym < thisMonth
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(5.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) scheme.primary else Color.Transparent)
                                .border(1.dp, if (ym == thisMonth && !isCurrent) scheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable(enabled = !past) {
                                    monthOffset = ChronoUnit.MONTHS.between(YearMonth.from(initial), ym).toInt()
                                    pickingMonth = false
                                }
                        ) {
                            Text(
                                DateTimeFormatter.ofPattern("MMM", Locale.US).format(ym),
                                style = MaterialTheme.typography.titleSmall,
                                color = when {
                                    isCurrent -> scheme.onPrimary
                                    past -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    else -> scheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        } else Column(
            Modifier
                .fillMaxWidth()
                .background(scheme.surface, RoundedCornerShape(16.dp))
                .border(1.dp, scheme.outline, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            Row {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(
                        it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                    )
                }
            }
            val lead = month.atDay(1).dayOfWeek.value % 7 // Sunday-first grid
            val cells = lead + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row {
                    for (col in 0 until 7) {
                        val dayNum = week * 7 + col - lead + 1
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).aspectRatio(1f)) {
                            if (dayNum in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNum)
                                val isSelected = date == selected
                                val past = date < today
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize(0.84f)
                                        .clip(CircleShape)
                                        .background(if (isSelected) scheme.primary else Color.Transparent)
                                        .border(1.dp, if (date == today && !isSelected) scheme.primary else Color.Transparent, CircleShape)
                                        .clickable(enabled = !past) { selectedDay = date.toEpochDay() }
                                ) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isSelected -> scheme.onPrimary
                                            past -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            else -> scheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).background(scheme.surface, CircleShape).border(1.dp, scheme.outline, CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) scheme.onSurface else scheme.outline)
    }
}
