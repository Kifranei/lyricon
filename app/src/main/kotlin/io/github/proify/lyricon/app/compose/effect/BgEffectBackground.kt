package io.github.proify.lyricon.app.compose.effect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    effectBackground: Boolean = true,
    baseColor: Color = MiuixTheme.colorScheme.surface,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    val isDark = isSystemInDarkTheme()
    val surface = MiuixTheme.colorScheme.surface
    val palette = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF221A44),
                Color(0xFF1F3B7A),
                Color(0xFF4B267A),
                Color(0xFF0F5E7A),
            )
        } else {
            listOf(
                Color(0xFFFFE4ED),
                Color(0xFFD7E7FF),
                Color(0xFFF4DFFF),
                Color(0xFFD6F4F0),
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "about_bg")
    val phase =
        if (dynamicBackground) {
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = if (isDark) 18000 else 14000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "about_bg_phase",
            ).value
        } else {
            0f
        }

    Box(modifier = modifier) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(bgModifier),
        ) {
            drawRect(baseColor)
            if (effectBackground) {
                val width = size.width
                val height = size.height
                val t = phase * (2f * PI).toFloat()
                val overlayAlpha = alpha().coerceIn(0f, 1f)
                val blobs =
                    listOf(
                        Triple(Offset(width * (0.18f + 0.08f * sin(t)), height * (0.16f + 0.06f * cos(t * 0.82f))), width * 0.62f, palette[0]),
                        Triple(Offset(width * (0.82f + 0.07f * cos(t * 0.9f)), height * (0.22f + 0.05f * sin(t * 1.1f))), width * 0.54f, palette[1]),
                        Triple(Offset(width * (0.28f + 0.08f * cos(t * 1.15f + 1.2f)), height * (0.72f + 0.06f * sin(t * 0.7f + 0.8f))), width * 0.66f, palette[2]),
                        Triple(Offset(width * (0.78f + 0.08f * sin(t * 0.75f + 2.4f)), height * (0.8f + 0.06f * cos(t * 1.05f + 1.4f))), width * 0.58f, palette[3]),
                    )

                blobs.forEach { (center, radius, color) ->
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.72f * overlayAlpha), Color.Transparent),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                    )
                }

                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = if (isDark) 0.015f else 0.16f * overlayAlpha),
                                    Color.Transparent,
                                    surface.copy(alpha = if (isDark) 0.28f else 0.08f),
                                ),
                        ),
                )
            }
        }
        content()
    }
}
