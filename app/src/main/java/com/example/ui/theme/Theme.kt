package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CodeForgeDarkScheme = darkColorScheme(
    primary = ForgeCyan,
    onPrimary = ObsidianBg,
    primaryContainer = ForgeCyanContainer,
    onPrimaryContainer = TextPrimary,
    secondary = ForgeEmerald,
    onSecondary = ObsidianBg,
    secondaryContainer = ForgeEmeraldContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = ForgeAmber,
    onTertiary = ObsidianBg,
    tertiaryContainer = ForgeAmberContainer,
    onTertiaryContainer = TextPrimary,
    error = ForgeCrimson,
    onError = ObsidianBg,
    errorContainer = ForgeCrimsonContainer,
    onErrorContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorder
)

private val CodeForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CodeForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CodeForgeDarkScheme,
        typography = AppTypography,
        shapes = CodeForgeShapes,
        content = content
    )
}
