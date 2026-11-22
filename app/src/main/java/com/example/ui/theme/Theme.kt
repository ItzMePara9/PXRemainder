package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.domain.models.ThemeMode

private val PXDarkColorScheme = darkColorScheme(
    primary = AccentVioletDark,
    onPrimary = AccentVioletDarkOn,
    primaryContainer = AccentVioletDarkContainer,
    onPrimaryContainer = AccentVioletDarkOnContainer,
    secondary = DarkTextSecondary,
    onSecondary = ObsidianBg,
    secondaryContainer = ObsidianSurfaceHigh,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = SemanticSuccess,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = DarkTextPrimary,
    surface = ObsidianSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = SemanticErrorDark,
    onError = ObsidianBg
)

private val PXLightColorScheme = lightColorScheme(
    primary = AccentVioletLight,
    onPrimary = AccentVioletLightOn,
    primaryContainer = AccentVioletLightContainer,
    onPrimaryContainer = AccentVioletLightOnContainer,
    secondary = LightTextSecondary,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceHigh,
    onSecondaryContainer = LightTextPrimary,
    tertiary = SemanticSuccess,
    onTertiary = LightSurface,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineSubtle,
    error = SemanticErrorLight,
    onError = LightSurface
)

val PXShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PXReminderTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) PXDarkColorScheme else PXLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PXTypography,
        shapes = PXShapes,
        content = content
    )
}
