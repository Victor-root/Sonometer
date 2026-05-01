package com.example.djmeter

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Animated splash-screen logo: gradient ring + pulsing sound wave. */
@Composable
fun LogoSection(modifier: Modifier = Modifier.size(120.dp)) {
    Box(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
                ),
                radius = radius,
                style = Stroke(width = 4.dp.toPx()),
            )
        }

        val infiniteTransition = rememberInfiniteTransition(label = "logo")
        val waveScale by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wave",
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(waveScale)
                .padding(24.dp),
        ) {
            val width = size.width
            val height = size.height
            val wavePath = Path()
            val points = 32
            val amplitude = height * 0.2f
            val step = width / points

            wavePath.moveTo(0f, height / 2)
            for (i in 0..points) {
                val x = i * step
                val y = height / 2 + (amplitude * sin(i * Math.PI / 8)).toFloat()
                wavePath.lineTo(x, y)
            }

            drawPath(
                path = wavePath,
                color = Color.White,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
