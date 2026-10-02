package com.payceylon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PayCeylonRed,
    secondary = PayCeylonRed,
    background = Ink,
    surface = Ink,
)

private val LightColorScheme = lightColorScheme(
    primary = PayCeylonRed,
    secondary = PayCeylonRed,
    background = androidx.compose.ui.graphics.Color.White,
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun PayCeylonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Brand colors intentionally stay stable instead of following Android dynamic color.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        ProvideTextStyle(value = Typography.bodyLarge, content = content)
    }
}
