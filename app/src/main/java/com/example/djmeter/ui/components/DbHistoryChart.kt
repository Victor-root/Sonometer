package com.example.djmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 * Real-time history chart. Thin red polyline over a faint grid.
 * Background and grid colors derive from the active Material theme so
 * the chart matches both light and dark schemes.
 */
@Composable
fun DbHistoryChart(
    readings: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = SonoRed,
) {
    val cd = stringResource(R.string.cd_chart)
    val background = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .semantics { contentDescription = cd }
            .fillMaxSize(),
    ) {
        val w = size.width
        val h = size.height

        val cols = 5
        val rows = 4
        val dxGrid = w / cols
        val dyGrid = h / rows
        for (i in 1 until cols) {
            val x = i * dxGrid
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }
        for (i in 1 until rows) {
            val y = i * dyGrid
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
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
