package com.devil.phoenixproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkColorScheme = darkColorScheme(
    // Primary (Rose)
    primary = Primary80,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = Color.White,

    // Secondary (Rose Light)
    secondary = Secondary80,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,

    // Tertiary (Neutral Grey)
    tertiary = Tertiary80,
    onTertiary = Color.White,
    tertiaryContainer = Slate700,
    onTertiaryContainer = Slate200,

    // Backgrounds & Surfaces (AMOLED black)
    background = Slate950,
    onBackground = OnSurfaceDark,

    surface = Slate900,
    onSurface = OnSurfaceDark,
    surfaceVariant = Slate800,
    onSurfaceVariant = OnSurfaceVariantDark,

    // Surface container roles (AMOLED scale)
    surfaceDim = Slate950,
    surfaceBright = Slate700,
    surfaceContainerLowest = Slate950,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate900,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,

    // Status
    error = SignalError,
    onError = Color.White,

    outline = Slate400,
    outlineVariant = Slate700
)

private val LightColorScheme = lightColorScheme(
    // Primary (Rose)
    primary = PhoenixOrangeLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,

    // Secondary (Rose Darker)
    secondary = EmberYellowLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDA4AF).copy(alpha = 0.3f),
    onSecondaryContainer = Secondary20,

    // Tertiary (Neutral Grey)
    tertiary = AshBlueLight,
    onTertiary = Color.White,
    tertiaryContainer = AshBlueDark.copy(alpha = 0.2f),
    onTertiaryContainer = AshBlueLight,

    // Backgrounds & Surfaces
    background = SurfaceContainerLight,
    onBackground = Slate900,

    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = SurfaceContainerHighLight,
    onSurfaceVariant = Slate700,

    // Surface container roles
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,

    // Status
    error = SignalError,
    onError = Color.White,

    outline = Slate400,
    outlineVariant = Slate200
)

@Composable
fun VitruvianTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkColors = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkColors) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = ExpressiveShapes, // Material 3 Expressive: More rounded shapes
        content = content
    )
}