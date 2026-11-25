package com.salmix.pillbox.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    secondary = LightBlue,
    background = BgDark,
    surface = CardDark,
    onPrimary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun SalmixPillBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
