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

private const val GAUGE_START_ANGLE_DEG = 200f      // 0% (left)
private const val GAUGE_SWEEP_DEG = 140f            // total arc

/**
 * Semi-circular dB gauge from [DB_MIN] to [DB_MAX].
 *
 * Drawing strategy: a single arc is split into two stroked sub-arcs
 * (cool zone + hot zone) so we can color the high-SPL section red.
 * Tick marks and labels are positioned with polar math; the needle is
 * an animated rotation around the arc center.
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
        targetValue = decibel.coerceIn(0f, DB_MAX),
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
        fontSize = 12.sp,
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

    // Center & radius — arc center sits below the visible canvas a bit
    // so the semi-circle uses the full width.
    val cx = w / 2f
    val cy = h * 0.95f
    val outerRadius = (minOf(w / 2f, h) * 0.95f)
    val arcStroke = (w * 0.012f).coerceAtLeast(4f)

    val tickMajorLen = w * 0.045f
    val tickMinorLen = w * 0.022f
    val labelOffset = w * 0.10f

    // Where the hot zone starts along the arc (90 dB by default)
    val hotFraction = ((DB_HOT_THRESHOLD - DB_MIN) / (DB_MAX - DB_MIN)).coerceIn(0f, 1f)
    val coolSweep = GAUGE_SWEEP_DEG * hotFraction
    val hotSweep = GAUGE_SWEEP_DEG - coolSweep

    // Arc bounding box (drawn around the offset center).
    val arcTopLeft = Offset(cx - outerRadius, cy - outerRadius)
    val arcSize = Size(outerRadius * 2f, outerRadius * 2f)

    // Cool half
    drawArc(
        color = mutedColor,
        startAngle = GAUGE_START_ANGLE_DEG,
        sweepAngle = coolSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = arcStroke, cap = StrokeCap.Butt),
    )
    // Hot half
    drawArc(
        color = hotColor,
        startAngle = GAUGE_START_ANGLE_DEG + coolSweep,
        sweepAngle = hotSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = arcStroke, cap = StrokeCap.Butt),
    )

    // Tick marks every 10 dB (major) with 4 minor between.
    val majorStep = 10
    val minorStep = 2
    var db = DB_MIN.toInt()
    while (db <= DB_MAX.toInt()) {
        val frac = (db - DB_MIN) / (DB_MAX - DB_MIN)
        val angleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * frac
        val isHot = db >= DB_HOT_THRESHOLD
        val color = if (isHot) hotColor else mutedColor
        val isMajor = db % majorStep == 0
        val len = if (isMajor) tickMajorLen else tickMinorLen
        drawTick(cx, cy, outerRadius - arcStroke / 2f, len, angleDeg, color, isMajor)

        if (isMajor) {
            // Number labels outside of the arc
            val labelRadius = outerRadius + labelOffset
            val rad = Math.toRadians(angleDeg.toDouble())
            val lx = cx + (labelRadius * cos(rad)).toFloat()
            val ly = cy + (labelRadius * sin(rad)).toFloat()
            val text = db.toString()
            val styled = labelStyle.copy(color = color)
            val measured = textMeasurer.measure(text = text, style = styled)
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = styled,
                topLeft = Offset(
                    lx - measured.size.width / 2f,
                    ly - measured.size.height / 2f,
                ),
            )
        }
        db += minorStep
    }

    // Optional Min marker (small tick + label).
    if (minDb != null) {
        val frac = ((minDb - DB_MIN) / (DB_MAX - DB_MIN)).coerceIn(0f, 1f)
        val angleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * frac
        drawTick(cx, cy, outerRadius - arcStroke / 2f, tickMajorLen * 0.7f, angleDeg, mutedColor, true)
        drawArcLabel(textMeasurer, minLabel, smallStyle.copy(color = mutedColor),
            cx, cy, outerRadius + labelOffset * 0.3f, angleDeg)
    }

    // Optional Peak marker (red bar + label).
    if (peakDb != null) {
        val frac = ((peakDb - DB_MIN) / (DB_MAX - DB_MIN)).coerceIn(0f, 1f)
        val angleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * frac
        val rad = Math.toRadians(angleDeg.toDouble())
        val rOuter = outerRadius + arcStroke
        val rInner = outerRadius - arcStroke * 2f
        drawLine(
            color = hotColor,
            start = Offset(cx + (rInner * cos(rad)).toFloat(), cy + (rInner * sin(rad)).toFloat()),
            end = Offset(cx + (rOuter * cos(rad)).toFloat(), cy + (rOuter * sin(rad)).toFloat()),
            strokeWidth = arcStroke * 0.9f,
        )
        drawArcLabel(
            textMeasurer, peakLabel, smallStyle,
            cx, cy, outerRadius + labelOffset * 0.55f, angleDeg,
        )
        drawArcLabel(
            textMeasurer, peakDb.toInt().toString(), smallStyle,
            cx, cy, outerRadius + labelOffset * 0.95f, angleDeg,
        )
    }

    // Needle — animated rotation around the arc center.
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
    val sx = cx + (rOuter * cos(rad)).toFloat()
    val sy = cy + (rOuter * sin(rad)).toFloat()
    val ex = cx + (rInner * cos(rad)).toFloat()
    val ey = cy + (rInner * sin(rad)).toFloat()
    drawLine(
        color = color,
        start = Offset(sx, sy),
        end = Offset(ex, ey),
        strokeWidth = if (major) 3f else 1.5f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawArcLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    cx: Float, cy: Float, radius: Float, angleDeg: Float,
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val px = cx + (radius * cos(rad)).toFloat()
    val py = cy + (radius * sin(rad)).toFloat()
    val measured = measurer.measure(text, style)
    drawText(
        textMeasurer = measurer,
        text = text,
        style = style,
        topLeft = Offset(px - measured.size.width / 2f, py - measured.size.height / 2f),
    )
}

private fun DrawScope.drawNeedle(
    cx: Float, cy: Float, radius: Float,
    valueFrac: Float, color: Color, baseStroke: Float,
) {
    val angleDeg = GAUGE_START_ANGLE_DEG + GAUGE_SWEEP_DEG * valueFrac
    val rad = Math.toRadians(angleDeg.toDouble())
    val tipX = cx + (radius * cos(rad)).toFloat()
    val tipY = cy + (radius * sin(rad)).toFloat()
    drawLine(
        color = color,
        start = Offset(cx, cy),
        end = Offset(tipX, tipY),
        strokeWidth = baseStroke * 0.9f,
        cap = StrokeCap.Round,
    )
    drawCircle(color = color, radius = baseStroke * 1.3f, center = Offset(cx, cy))
}
