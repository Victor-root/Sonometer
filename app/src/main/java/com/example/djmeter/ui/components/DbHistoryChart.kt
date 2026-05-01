package com.example.djmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.djmeter.R
import com.example.djmeter.ui.theme.SonoRed

/**
 * Real-time history chart. Draws a thin red polyline over a faint dotted
 * grid. The horizontal axis is sample index (oldest → newest); the
 * vertical axis is dB mapped to [DB_MIN] .. [DB_MAX].
 */
@Composable
fun DbHistoryChart(
    readings: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = SonoRed,
    gridColor: Color = Color(0x33FFFFFF),
    background: Color = Color(0xFF0A0A0A),
) {
    val cd = stringResource(R.string.cd_chart)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .semantics { contentDescription = cd }
            .fillMaxSize(),
    ) {
        val w = size.width
        val h = size.height

        // Grid: 5 cols x 4 rows
        val cols = 5
        val rows = 4
        val dxGrid = w / cols
        val dyGrid = h / rows
        for (i in 1 until cols) {
            val x = i * dxGrid
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
            )
        }
        for (i in 1 until rows) {
            val y = i * dyGrid
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        if (readings.size < 2) return@Canvas
        val n = readings.size
        val dx = w / (n - 1).coerceAtLeast(1)

        val path = Path()
        readings.forEachIndexed { i, value ->
            val frac = ((value - DB_MIN) / (DB_MAX - DB_MIN)).coerceIn(0f, 1f)
            val x = i * dx
            val y = h - frac * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
