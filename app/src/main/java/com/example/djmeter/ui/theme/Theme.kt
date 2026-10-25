package com.example.djmeter.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SonoRed,
    onPrimary = Color.White,
    secondary = SonoOnDarkMuted,
    background = SonoBlack,
    onBackground = SonoOnDark,
    surface = SonoSurfaceDark,
    onSurface = SonoOnDark,
    surfaceVariant = SonoSurfaceVariantDark,
    onSurfaceVariant = SonoOnDarkMuted,
    error = SonoRed,
)

private val LightColorScheme = lightColorScheme(
    primary = SonoRed,
    onPrimary = Color.White,
    secondary = SonoOnLightMuted,
    background = SonoWhite,
    onBackground = SonoOnLight,
    surface = SonoSurfaceLight,
    onSurface = SonoOnLight,
    surfaceVariant = SonoSurfaceVariantLight,
    onSurfaceVariant = SonoOnLightMuted,
    error = SonoRed,
)

@Composable
fun DjMeterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent system bars so the background extends edge-to-edge.
            // We do NOT hide them; insets are handled by the screen.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
