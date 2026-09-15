package com.ontimestack.webkiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val CyberDarkColorScheme = darkColorScheme(
    primary = CyberPrimary,
    onPrimary = CyberOnPrimary,
    primaryContainer = CyberAccentSurface,
    onPrimaryContainer = CyberPrimary,
    secondary = CyberFocus,
    onSecondary = CyberOnPrimary,
    background = CyberBackground,
    onBackground = CyberForeground,
    surface = CyberCard,
    onSurface = CyberForeground,
    surfaceVariant = CyberSurfaceSoft,
    onSurfaceVariant = CyberMuted,
    outline = CyberOutline,
    outlineVariant = CyberBorder,
    error = CyberError,
    onError = CyberForeground
)

private val CyberShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun OtsKioskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberDarkColorScheme,
        typography = Typography,
        shapes = CyberShapes,
        content = content
    )
}
