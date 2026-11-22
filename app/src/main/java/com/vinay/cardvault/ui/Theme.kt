package com.vinay.cardvault.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Light theme, same family of colours as Ledger
val Bg = Color(0xFFF7F5FB)
val Panel = Color(0xFFFFFFFF)
val Panel2 = Color(0xFFF1EDF8)
val Line = Color(0xFFDDD7E6)
val TextC = Color(0xFF1C1B1F)
val Muted = Color(0xFF5F5B68)
val Accent = Color(0xFF5B2FC4)
val Accent2 = Color(0xFF1A9E86)
val Danger = Color(0xFFD92D3A)
val Warn = Color(0xFFD98A00)
val Ok = Color(0xFF16935A)

val Ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

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
        colorScheme = lightColorScheme(
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

/** Calmer scrolling, same as Ledger: a flick carries the list about half as far and slows sooner. */
@Composable
fun rememberCalmFling(): androidx.compose.foundation.gestures.FlingBehavior {
    val decay = androidx.compose.animation.rememberSplineBasedDecay<Float>()
    return androidx.compose.runtime.remember(decay) {
        object : androidx.compose.foundation.gestures.FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                val v = initialVelocity * 0.5f
                if (kotlin.math.abs(v) < 50f) return initialVelocity
                var left = v
                var last = 0f
                androidx.compose.animation.core.AnimationState(0f, v).animateDecay(decay) {
                    val delta = value - last
                    val used = scrollBy(delta)
                    last = value
                    left = velocity
                    if (kotlin.math.abs(delta - used) > 0.5f) cancelAnimation()
                }
                return left
            }
        }
    }
}
