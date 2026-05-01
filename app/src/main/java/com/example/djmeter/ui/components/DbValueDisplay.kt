package com.example.djmeter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.djmeter.R
import kotlin.math.roundToInt

/** Big centered dB value with optional elapsed time and "dB" unit. */
@Composable
fun DbValueDisplay(
    decibel: Float,
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unit = stringResource(R.string.unit_db)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatDecibel(decibel),
            color = onSurface,
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.width(8.dp))
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = formatElapsed(elapsedSeconds),
                color = muted,
                fontSize = 14.sp,
            )
            Text(
                text = unit,
                color = muted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Row with MIN. / AVG. / MAX. (each label may be hidden if value is null). */
@Composable
fun DbStatsRow(
    minDb: Float?,
    avgDb: Float?,
    maxDb: Float?,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatBlock(label = stringResource(R.string.label_avg), value = avgDb, muted = muted)
        StatBlock(label = stringResource(R.string.label_min), value = minDb, muted = muted)
        StatBlock(label = stringResource(R.string.label_max), value = maxDb, muted = muted)
    }
}

@Composable
private fun StatBlock(label: String, value: Float?, muted: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            text = value?.let { formatDecibel(it) } ?: "--",
            color = muted,
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

/** Single line: "90 dB : Tondeuse à gazon". */
@Composable
fun DbLevelDescriptor(decibel: Float, modifier: Modifier = Modifier) {
    val level = SoundLevel.fromDb(decibel)
    val text = stringResource(
        R.string.level_format,
        decibel.roundToInt(),
        stringResource(level.labelRes),
    )
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
    )
}

/** Multi-segment color bar: blue → green → yellow → orange → red. */
@Composable
fun DbLevelColorBar(modifier: Modifier = Modifier) {
    val segments = colorBarSegments()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

private fun colorBarSegments(): List<Color> {
    // 16 fixed segments, sweeping from cool to hot.
    val blue = androidx.compose.ui.graphics.Color(0xFF2D7BE8)
    val cyan = androidx.compose.ui.graphics.Color(0xFF2EA0E8)
    val green = androidx.compose.ui.graphics.Color(0xFF2EB07A)
    val lime = androidx.compose.ui.graphics.Color(0xFF52B848)
    val yellow = androidx.compose.ui.graphics.Color(0xFFE8B72E)
    val orange = androidx.compose.ui.graphics.Color(0xFFE8782E)
    val deep = androidx.compose.ui.graphics.Color(0xFFB6431F)
    val dark = androidx.compose.ui.graphics.Color(0xFF6B2A18)
    return listOf(
        blue, blue, cyan, cyan,
        green, green, lime, yellow,
        yellow, orange, orange, deep,
        deep, dark, dark, dark,
    )
}

private fun formatDecibel(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return "%.1f".format(rounded)
}

private fun formatElapsed(totalSeconds: Long): String {
    val mm = (totalSeconds / 60) % 60
    val ss = totalSeconds % 60
    return "%02d:%02d".format(mm, ss)
}
