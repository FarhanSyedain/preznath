package com.yimberzol.preznath.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PreznathColors = lightColorScheme(
    primary = PantoneRed,
    onPrimary = Paper,
    secondary = PantoneRedDark,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Ink2,
    outline = Rule,
    error = FraudRed,
    onError = Paper,
)

@Composable
fun PreznathTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PreznathColors,
        typography = PreznathTypography,
        content = content,
    )
}
