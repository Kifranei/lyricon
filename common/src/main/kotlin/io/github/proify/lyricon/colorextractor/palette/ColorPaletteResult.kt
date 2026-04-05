/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

/**
 * 图片颜色提取结果
 */
data class ColorPaletteResult(
    val lightModeColors: ThemeColors,   // 亮主题配色
    val darkModeColors: ThemeColors     // 暗主题配色
)