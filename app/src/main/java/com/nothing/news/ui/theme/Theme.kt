package com.nothing.news.ui.theme

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
    primary = White,
    secondary = LightGrey,
    tertiary = NothingRed,
    background = Black,
    surface = Black,
    onPrimary = Black,
    onSecondary = Black,
    onTertiary = White,
    onBackground = White,
    onSurface = White,
    surfaceVariant = DarkGrey,
    onSurfaceVariant = LightGrey,
    outline = MediumGrey
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    secondary = DarkGrey,
    tertiary = NothingRed,
    background = White,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = Black,
    onSurface = Black,
    surfaceVariant = LightGrey,
    onSurfaceVariant = DarkGrey,
    outline = MediumGrey
)

@Composable
fun NothingNewsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
