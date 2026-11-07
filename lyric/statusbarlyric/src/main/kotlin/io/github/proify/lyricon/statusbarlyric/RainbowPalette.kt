/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.graphics.Color
import io.github.proify.lyricon.lyric.style.RainbowTextColor
import io.github.proify.lyricon.lyric.style.TextStyle

/** 彩虹歌词配色，供歌词文本与图标共用，保证两者颜色一致。 */
internal object RainbowPalette {

    /** 预设彩虹渐变（暗色模式）：饱和明亮，一键开启无需手动配置 */
    private val DARK = intArrayOf(
        Color.parseColor("#FF4D4F"),
        Color.parseColor("#FF9F43"),
        Color.parseColor("#FFD93D"),
        Color.parseColor("#2ED573"),
        Color.parseColor("#1E90FF"),
        Color.parseColor("#5352ED"),
        Color.parseColor("#A55EEA")
    )

    /** 预设彩虹渐变（亮色模式）：适当压暗，保证白底可读 */
    private val LIGHT = intArrayOf(
        Color.parseColor("#C53A3D"),
        Color.parseColor("#C06A00"),
        Color.parseColor("#B28800"),
        Color.parseColor("#1E8E3E"),
        Color.parseColor("#1565C0"),
        Color.parseColor("#3949AB"),
        Color.parseColor("#7B1FA2")
    )

    /** 优先使用用户在亮/暗色项里自定义的颜色，未配置则回退到预设彩虹渐变。 */
    fun resolve(textStyle: TextStyle, lightMode: Boolean): RainbowTextColor {
        val custom = textStyle.color(lightMode)
        val fallback = preset(lightMode)
        return RainbowTextColor(
            normal = custom?.normal?.takeIf { it.isNotEmpty() } ?: fallback.normal,
            background = custom?.background?.takeIf { it.isNotEmpty() } ?: fallback.background,
            highlight = custom?.highlight?.takeIf { it.isNotEmpty() } ?: fallback.highlight
        )
    }

    private fun preset(lightMode: Boolean): RainbowTextColor {
        val normal = if (lightMode) LIGHT else DARK
        val bgAlpha = if (lightMode) 0.26f else 0.45f
        return RainbowTextColor(
            normal = normal,
            background = normal.map { it.withAlpha(bgAlpha) }.toIntArray(),
            highlight = normal
        )
    }

    private fun Int.withAlpha(ratio: Float): Int {
        val alpha = (ratio.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
    }
}
