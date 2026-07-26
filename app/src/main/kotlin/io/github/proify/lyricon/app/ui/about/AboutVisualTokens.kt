package io.github.proify.lyricon.app.ui.about

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode

internal fun aboutTitleBlendColors(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) {
        listOf(
            BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
        )
    }

internal fun aboutCardBlendColors(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) {
        listOf(
            BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
        )
    }

internal fun aboutCardFallbackColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF252528) else Color.White
