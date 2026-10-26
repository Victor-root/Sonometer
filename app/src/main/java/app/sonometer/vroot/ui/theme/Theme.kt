package app.sonometer.vroot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
fun SonometerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
