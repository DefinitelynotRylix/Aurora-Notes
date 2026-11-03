package com.aurora.notes.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalAuroraDarkTheme = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = AuroraRoyalViolet,
    secondary = AuroraAmethyst,
    tertiary = AuroraLightPurple,
    background = AuroraBackgroundDark,
    surface = AuroraSurfaceDark,
    surfaceVariant = Color(0xFF241C30),
    surfaceContainer = AuroraSurfaceDark,
    surfaceContainerHigh = Color(0xFF241C30),
    surfaceContainerLow = AuroraBackgroundDark,
    surfaceTint = Color.Transparent,
    onPrimary = AuroraSurfaceLight,
    onSecondary = AuroraSurfaceLight,
    onBackground = AuroraTextPrimaryDark,
    onSurface = AuroraTextPrimaryDark,
    onSurfaceVariant = AuroraTextSecondaryDark,
    outline = AuroraBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = AuroraRoyalViolet,
    secondary = AuroraAmethyst,
    tertiary = AuroraLightPurple,
    background = AuroraBackgroundLight,
    surface = AuroraSurfaceLight,
    surfaceVariant = Color(0xFFF3EDF9),
    surfaceContainer = AuroraSurfaceLight,
    surfaceContainerHigh = Color(0xFFF3EDF9),
    surfaceContainerLow = AuroraBackgroundLight,
    surfaceTint = Color.Transparent,
    onPrimary = AuroraSurfaceLight,
    onSecondary = AuroraSurfaceLight,
    onBackground = AuroraTextPrimaryLight,
    onSurface = AuroraTextPrimaryLight,
    onSurfaceVariant = AuroraTextSecondaryLight,
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.isForceDarkAllowed = false
                window.decorView.isForceDarkAllowed = false
                var parent = view.parent
                while (parent is View) {
                    parent.isForceDarkAllowed = false
                    parent = parent.parent
                }
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalAuroraDarkTheme provides darkTheme,
        LocalContentColor provides colorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun IrisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AuroraTheme(darkTheme = darkTheme, content = content)
}
