package net.wigle.wigleandroid.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00E5FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF00FF9D),
    background = androidx.compose.ui.graphics.Color(0xFF0B0F14),
    surface = androidx.compose.ui.graphics.Color(0xFF111821),
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White
)

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0066FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF00C853)
)

@Composable
fun WigleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}