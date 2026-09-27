package com.taskinthemind.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Outlined battery with a bolt, drawn with the same 2-unit stroke as the
 * other outlined icons. Material's own battery icons are solid blocks, which
 * looked heavy and out of place in the permission list.
 */
val BatteryBolt: ImageVector = ImageVector.Builder("BatteryBolt", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        // Body
        moveTo(8.5f, 5f)
        horizontalLineTo(15.5f)
        quadTo(17f, 5f, 17f, 6.5f)
        verticalLineTo(19.5f)
        quadTo(17f, 21f, 15.5f, 21f)
        horizontalLineTo(8.5f)
        quadTo(7f, 21f, 7f, 19.5f)
        verticalLineTo(6.5f)
        quadTo(7f, 5f, 8.5f, 5f)
        close()
        // Terminal
        moveTo(10f, 2.75f)
        horizontalLineTo(14f)
    }
    path(fill = SolidColor(Color.Black)) {
        // Bolt
        moveTo(12.9f, 8f)
        lineTo(9.6f, 13.4f)
        horizontalLineTo(11.9f)
        lineTo(11.1f, 18f)
        lineTo(14.4f, 12.6f)
        horizontalLineTo(12.1f)
        close()
    }
}.build()
