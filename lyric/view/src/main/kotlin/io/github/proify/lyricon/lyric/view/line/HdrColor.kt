/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.ColorSpace

/**
 * 歌词高亮的扩展色域配色。
 *
 * 公开可见是有意为之：状态栏的图标 / 封面 HDR 绘制
 * （`io.github.proify.lyricon.statusbarlyric.logo.HdrLogoShader`）需要复用同一套
 * 配色数学，才能保证单色图标与歌词的高光表现一致。
 */
object HdrColor {

    /**
     * 把 SDR 颜色按 [ratio] 打包成 `EXTENDED_SRGB` 的颜色长整型。
     *
     * 返回值的 RGB 分量可以超过 1.0，即超过 SDR 白点 —— 这正是歌词能在 HDR 屏上
     * 真正变亮、而普通 `Int` 颜色做不到的原因。
     */
    @SuppressLint("NewApi")
    fun packHighlightColor(color: Int, ratio: Float): Long {
        val saturationBoost = saturationBoost(color, ratio)
        val redBase = boostChannel(Color.red(color) / 255f, saturationBoost)
        val greenBase = boostChannel(Color.green(color) / 255f, saturationBoost)
        val blueBase = boostChannel(Color.blue(color) / 255f, saturationBoost)
        val effectiveRatio = colorPreservingRatio(redBase, greenBase, blueBase, ratio)
        val alpha = Color.alpha(color) / 255f
        val red = redBase * effectiveRatio
        val green = greenBase * effectiveRatio
        val blue = blueBase * effectiveRatio
        return Color.pack(
            red,
            green,
            blue,
            alpha,
            ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)
        )
    }

    private fun colorPreservingRatio(
        red: Float,
        green: Float,
        blue: Float,
        ratio: Float
    ): Float {
        if (!ratio.isFinite() || ratio <= MIN_RATIO) return MIN_RATIO

        val maxChannel = maxOf(red, green, blue)
        if (maxChannel <= 0f) return ratio

        if (!isColored(red, green, blue)) return ratio

        val cappedRatio = COLORED_MAX_EXTENDED_CHANNEL / maxChannel
        return ratio.coerceAtMost(cappedRatio).coerceAtLeast(MIN_RATIO)
    }

    private fun saturationBoost(color: Int, ratio: Float): Float {
        if (!ratio.isFinite() || ratio <= MIN_RATIO) return MIN_SATURATION_BOOST

        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        if (!isColored(red, green, blue)) return MIN_SATURATION_BOOST

        val ratioProgress = ((ratio - MIN_RATIO) / (MAX_RATIO_FOR_BOOST - MIN_RATIO))
            .coerceIn(0f, 1f)
        return MIN_SATURATION_BOOST + (MAX_SATURATION_BOOST - MIN_SATURATION_BOOST) * ratioProgress
    }

    private fun boostChannel(channel: Float, boost: Float): Float {
        if (boost <= MIN_SATURATION_BOOST) return channel
        return (SATURATION_PIVOT + (channel - SATURATION_PIVOT) * boost).coerceIn(0f, 1f)
    }

    private fun isColored(red: Float, green: Float, blue: Float): Boolean {
        val maxChannel = maxOf(red, green, blue)
        if (maxChannel <= 0f) return false

        val minChannel = minOf(red, green, blue)
        val chroma = maxChannel - minChannel
        val saturation = chroma / maxChannel
        return chroma >= COLORED_CHROMA_THRESHOLD &&
                saturation >= COLORED_SATURATION_THRESHOLD
    }

    private const val MIN_RATIO = 1.0f
    private const val MAX_RATIO_FOR_BOOST = 4.0f
    private const val MIN_SATURATION_BOOST = 1.0f
    private const val MAX_SATURATION_BOOST = 1.35f
    private const val SATURATION_PIVOT = 0.5f
    private const val COLORED_CHROMA_THRESHOLD = 0.08f
    private const val COLORED_SATURATION_THRESHOLD = 0.12f
    private const val COLORED_MAX_EXTENDED_CHANNEL = 2.0f
}
