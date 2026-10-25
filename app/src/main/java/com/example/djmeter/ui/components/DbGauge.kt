package com.example.djmeter.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.djmeter.R
import com.example.djmeter.ui.theme.SonoOnDarkMuted
import com.example.djmeter.ui.theme.SonoRed
import kotlin.math.cos
import kotlin.math.sin

private const val GAUGE_START_ANGLE_DEG = 200f  // left end of arc (standard canvas clockwise)
private const val GAUGE_SWEEP_DEG = 140f         // total arc span

/**
 * Semi-circular dB gauge from [DB_MIN] to [DB_MAX].
 *
 * The arc is split into a cool (grey) zone and a hot (red) zone at [DB_HOT_THRESHOLD].
 * Labels are drawn outside the arc using polar coordinates.
 * All drawText calls receive an explicit size so Compose never computes a
 * negative maxWidth from an out-of-bounds topLeft.
 */
@Composable
fun DbGauge(
    decibel: Float,
    modifier: Modifier = Modifier,
    minDb: Float? = null,
    peakDb: Float? = null,
    height: Dp = 220.dp,
) {
    val animatedDb by animateFloatAsState(
        targetValue = decibel.coerceIn(DB_MIN, DB_MAX),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "needle",
    )

    val gaugeCd = stringResource(R.string.cd_gauge)
    val peakLabel = stringResource(R.string.label_peak)
    val minLabel = stringResource(R.string.label_min_short)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = SonoOnDarkMuted,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
    )
    val smallStyle = TextStyle(
        color = SonoRed,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = gaugeCd },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGauge(
                animatedDb = animatedDb,
                minDb = minDb,
                peakDb = peakDb,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
                smallStyle = smallStyle,
                peakLabel = peakLabel,
                minLabel = minLabel,
                mutedColor = SonoOnDarkMuted,
                hotColor = SonoRed,
            )
        }
    }
}

private fun DrawScope.drawGauge(
    animatedDb: Float,
    minDb: Float?,
    peakDb: Float?,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    smallStyle: TextStyle,
    peakLabel: String,
    minLabel: String,
    mutedColor: Color,
    hotColor: Color,
) {
    val w = size.width
    val h = size.height

    // Radius sized to leave room for labels on both sides:
    // labels at the extremes (20 dB and 120 dB) are at ±cos(20°)≈0.94 from center.
    // Using w*0.35 keeps the furthest label at 0.5w + 0.45w*0.94 ≈ 0.92w → inside canvas.
    val outerRadius = w * 0.35f
    val cx = w / 2f
    val cy = h * 0.94f           // arc center is near the bottom of the canvas
    val arcStroke = (outerRadius * 0.032f).coerceAtLeast(4f)

    val tickMajorLen = outerRadius * 0.12f
    val tickMinorLen = outerRadius * 0.06f
    val labelRadius = outerRadius + outerRadius * 0.22f  // labels just outside the arc

    // Hot zone boundary along the arc
    val hotFraction = ((DB_HOT_THRESHOLD - DB_MIN) / (DB_MAX - DB_MIN)).coerceIn(0f, 1f)
    val coolSweep = GAUGE_SWEEP_DEG * hotFraction
    val hotSweep = GAUGE_SWEEP_DEG - coolSweep

    val arcTopLeft = Offset(cx - outerRadius, cy - outerRadius)
    val arcSize = Size(outerRadius * 2f, outerRadius * 2f)

    // Arc (cool + hot halves)
    drawArc(
        color = mutedColor,
        startAngle = GAUGE_START_ANGLE_DEG,
        sweepAngle = coolSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = arcStroke, cap = StrokeCap.Butt),
    )
    drawArc(
        color = hotColor,
        startAngle = GAUGE_START_ANGLE_DEG + coolSweep,
        sweepAngle = hotSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = arcStroke, cap = StrokeCap.Butt),
    )

    // Ticks and labels — major every 10 dB, minor every 2 dB.
    val majorStep = 10
    val minorStep = 2
    var db = DB_MIN.toInt()
    while (db <= DB_MAX.toInt()) {
        val frac = (db - DB_MIN) / (DB_MAX - DB_MIN)
        val angleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * frac
        val isHot = db >= DB_HOT_THRESHOLD
        val color = if (isHot) hotColor else mutedColor
        val isMajor = db % majorStep == 0
        val tickLen = if (isMajor) tickMajorLen else tickMinorLen
        drawTick(cx, cy, outerRadius - arcStroke / 2f, tickLen, angleDeg, color, isMajor)

        if (isMajor) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val lx = cx + (labelRadius * cos(rad)).toFloat()
            val ly = cy + (labelRadius * sin(rad)).toFloat()
            val text = db.toString()
            val styled = labelStyle.copy(color = color)
            val measured = textMeasurer.measure(text = text, style = styled)
            val tw = measured.size.width.toFloat()
            val th = measured.size.height.toFloat()
            val tx = lx - tw / 2f
            val ty = ly - th / 2f
            // Only draw if fully or mostly within the canvas to avoid negative maxWidth.
            if (tx + tw > 0f && tx < w && ty + th > 0f && ty < h) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = styled,
                    topLeft = Offset(tx.coerceAtLeast(0f), ty.coerceAtLeast(0f)),
                    size = Size(tw, th),   // explicit size → no negative maxWidth
                )
            }
        }
        db += minorStep
    }

    // Min marker — tick on arc + label rotated along tangent, placed well inside the arc.
    if (minDb != null) {
        val minFrac = ((minDb.coerceIn(DB_MIN, DB_MAX) - DB_MIN) / (DB_MAX - DB_MIN))
        val minAngleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * minFrac
        val minRad = Math.toRadians(minAngleDeg.toDouble())
        drawTick(cx, cy, outerRadius - arcStroke / 2f, tickMajorLen * 0.7f, minAngleDeg, mutedColor, major = true)
        val minStyled = smallStyle.copy(color = mutedColor)
        val minMeasured = textMeasurer.measure(minLabel, minStyled)
        val mtw = minMeasured.size.width.toFloat()
        val mth = minMeasured.size.height.toFloat()
        val mlx = cx + (outerRadius * 0.72f * cos(minRad)).toFloat()
        val mly = cy + (outerRadius * 0.72f * sin(minRad)).toFloat()
        withTransform({ rotate(minAngleDeg + 90f, Offset(mlx, mly)) }) {
            drawText(
                textMeasurer = textMeasurer,
                text = minLabel,
                style = minStyled,
                topLeft = Offset(mlx - mtw / 2f, mly - mth / 2f),
                size = Size(mtw, mth),
            )
        }
    }

    // Peak marker — thin red tick crossing the arc (persists as needle moves),
    // then "Peak" + value rotated along the arc tangent.
    if (peakDb != null) {
        val peakFrac = ((peakDb.coerceIn(DB_MIN, DB_MAX) - DB_MIN) / (DB_MAX - DB_MIN))
        val peakAngleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * peakFrac
        val peakRad = Math.toRadians(peakAngleDeg.toDouble())

        // Red tick crossing the arc from inner to outer edge
        val rOuter = outerRadius + arcStroke * 1.8f
        val rInner = outerRadius - arcStroke * 3.2f
        drawLine(
            color = hotColor,
            start = Offset(cx + (rInner * cos(peakRad)).toFloat(), cy + (rInner * sin(peakRad)).toFloat()),
            end = Offset(cx + (rOuter * cos(peakRad)).toFloat(), cy + (rOuter * sin(peakRad)).toFloat()),
            strokeWidth = arcStroke * 0.85f,
            cap = StrokeCap.Butt,
        )

        // "Peak" label + value stacked, rotated so baseline is tangent to the arc
        val blockCenterR = outerRadius * 0.76f
        val plx = cx + (blockCenterR * cos(peakRad)).toFloat()
        val ply = cy + (blockCenterR * sin(peakRad)).toFloat()
        val peakMeasured = textMeasurer.measure(peakLabel, smallStyle)
        val valueMeasured = textMeasurer.measure(peakDb.toInt().toString(), smallStyle)
        val ptw = peakMeasured.size.width.toFloat()
        val pth = peakMeasured.size.height.toFloat()
        val vtw = valueMeasured.size.width.toFloat()
        val vth = valueMeasured.size.height.toFloat()
        val gap = 2f
        val totalH = pth + gap + vth
        withTransform({ rotate(peakAngleDeg + 90f, Offset(plx, ply)) }) {
            drawText(
                textMeasurer = textMeasurer,
                text = peakLabel,
                style = smallStyle,
                topLeft = Offset(plx - ptw / 2f, ply - totalH / 2f),
                size = Size(ptw, pth),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = peakDb.toInt().toString(),
                style = smallStyle,
                topLeft = Offset(plx - vtw / 2f, ply - totalH / 2f + pth + gap),
                size = Size(vtw, vth),
            )
        }
    }

    // Animated needle
    drawNeedle(
        cx = cx,
        cy = cy,
        radius = outerRadius - arcStroke - tickMajorLen,
        valueFrac = ((animatedDb - DB_MIN) / (DB_MAX - DB_MIN)).coerceIn(0f, 1f),
        color = hotColor,
        baseStroke = arcStroke,
    )
}

private fun DrawScope.drawTick(
    cx: Float, cy: Float, rOuter: Float, len: Float,
    angleDeg: Float, color: Color, major: Boolean,
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val rInner = rOuter - len
    drawLine(
        color = color,
        start = Offset(cx + (rOuter * cos(rad)).toFloat(), cy + (rOuter * sin(rad)).toFloat()),
        end = Offset(cx + (rInner * cos(rad)).toFloat(), cy + (rInner * sin(rad)).toFloat()),
        strokeWidth = if (major) 3f else 1.5f,
        cap = StrokeCap.Round,
    )
}


private fun DrawScope.drawNeedle(
    cx: Float, cy: Float, radius: Float,
    valueFrac: Float, color: Color, baseStroke: Float,
) {
    val angleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * valueFrac
    val rad = Math.toRadians(angleDeg.toDouble())
    drawLine(
        color = color,
        start = Offset(cx, cy),
        end = Offset(cx + (radius * cos(rad)).toFloat(), cy + (radius * sin(rad)).toFloat()),
        strokeWidth = baseStroke * 0.9f,
        cap = StrokeCap.Round,
    )
    drawCircle(color = color, radius = baseStroke * 1.3f, center = Offset(cx, cy))
}
