package com.aurora.notes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AuroraRoyalViolet,
    secondary = AuroraAmethyst,
    tertiary = AuroraLightPurple,
    background = AuroraBackgroundDark,
    surface = AuroraSurfaceDark,
    onPrimary = AuroraSurfaceLight,
    onSecondary = AuroraSurfaceLight,
    onBackground = AuroraTextPrimaryDark,
    onSurface = AuroraTextPrimaryDark,
    outline = AuroraBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = AuroraRoyalViolet,
    secondary = AuroraAmethyst,
    tertiary = AuroraLightPurple,
    background = AuroraBackgroundLight,
    surface = AuroraSurfaceLight,
    onPrimary = AuroraSurfaceLight,
    onSecondary = AuroraSurfaceLight,
    onBackground = AuroraTextPrimaryLight,
    onSurface = AuroraTextPrimaryLight,
    outline = AuroraBorderLight
)

@Composable
fun AuroraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun IrisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AuroraTheme(darkTheme = darkTheme, content = content)
}
