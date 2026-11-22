package com.vinay.cardvault.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Same palette as the original Card Vault
val Bg = Color(0xFF0E1116)
val Panel = Color(0xFF161A22)
val Panel2 = Color(0xFF1D222C)
val Line = Color(0xFF2A303C)
val TextC = Color(0xFFE8EBF1)
val Muted = Color(0xFF8B93A3)
val Accent = Color(0xFF7C5CFF)
val Accent2 = Color(0xFF22C3A6)
val Danger = Color(0xFFFF5D6C)
val Warn = Color(0xFFFFB648)
val Ok = Color(0xFF3DDC97)

val Ease = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

private val gradients = mapOf(
    "midnight" to (0xFF24345E to 0xFF0B1020),
    "ocean" to (0xFF0F4C81 to 0xFF1AA0C8),
    "emerald" to (0xFF0B6B4F to 0xFF1FBF8A),
    "crimson" to (0xFF7A0F1F to 0xFFD6304A),
    "gold" to (0xFF6E500E to 0xFFD9A93A),
    "violet" to (0xFF3B1E7A to 0xFF8A5CFF),
    "rose" to (0xFF7A1E4F to 0xFFE0609A),
    "metal" to (0xFF111111 to 0xFF4A4A4A),
    "slate" to (0xFF2F3A4A to 0xFF61708A),
    "sunset" to (0xFFA63B14 to 0xFFF39C3D),
)

fun cardBrush(name: String): Brush {
    val (a, b) = gradients[name] ?: gradients.getValue("midnight")
    return Brush.linearGradient(listOf(Color(a), Color(b)), start = Offset.Zero, end = Offset.Infinite)
}

@Composable
fun CardVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.White,
            secondary = Accent2,
            background = Bg, onBackground = TextC,
            surface = Panel, onSurface = TextC,
            surfaceVariant = Panel2, onSurfaceVariant = Muted,
            surfaceContainer = Panel, surfaceContainerHigh = Panel2, surfaceContainerHighest = Panel2,
            outline = Line, outlineVariant = Line,
            error = Danger,
        ),
        content = content,
    )
}
