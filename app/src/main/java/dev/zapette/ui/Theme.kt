package dev.zapette.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ZColors {
    val Bg = Color(0xFF0E1015)
    val Surface = Color(0xFF1A1D24)
    val SurfaceFocused = Color(0xFF2B303B)
    val Placeholder = Color(0xFF242833)
    val Accent = Color(0xFFFF8A1F)
    val Text = Color(0xFFF2F3F5)
    val TextDim = Color(0xFF9AA0AB)
    val Error = Color(0xFFFF5A5F)
}

@Composable
fun ZapetteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ZColors.Accent,
            onPrimary = Color.Black,
            background = ZColors.Bg,
            onBackground = ZColors.Text,
            surface = ZColors.Surface,
            onSurface = ZColors.Text,
            onSurfaceVariant = ZColors.TextDim,
            error = ZColors.Error,
        ),
        content = content,
    )
}
