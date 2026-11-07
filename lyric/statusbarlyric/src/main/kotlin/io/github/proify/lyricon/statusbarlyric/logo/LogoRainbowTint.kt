/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.statusbarlyric.RainbowPalette

/**
 * 图标是否跟随彩虹歌词染色。
 *
 * 必须在策略写入 imageTintList 之后调用：没有 tint（保留原色 / 自定义图标未开染色）时不染。
 * 用户为图标指定了固定颜色（启用自定义颜色且未选「跟随文字」）时保持指定色。
 */
internal fun shouldFollowRainbow(
    textStyle: TextStyle?,
    logoStyle: LogoStyle?,
    lightMode: Boolean,
    hasTint: Boolean
): Boolean {
    if (!hasTint || textStyle?.enableRainbowTextColor != true) return false
    if (logoStyle == null || !logoStyle.enableCustomColor) return true
    return logoStyle.color(lightMode).followTextColor
}

internal fun SuperLogo.resolveRainbowTintColors(): IntArray? {
    val style = lyricStyle ?: return null
    val text = style.packageStyle.text
    val lightMode = currentStatusColor.isLightMode
    if (!shouldFollowRainbow(text, style.packageStyle.logo, lightMode, imageTintList != null)) {
        return null
    }
    return RainbowPalette.resolve(text, lightMode).normal.takeIf { it.isNotEmpty() }
}
