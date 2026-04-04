/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 针对音乐播放器优化的颜色提取器。
 * 核心逻辑：完全基于封面主色的同色相变体生成渐变，确保视觉统一。
 */
object ColorExtractor {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private const val DEFAULT_SEED_COLOR = 0xFF6750A4.toInt()
    private const val MAX_IMAGE_DIMENSION = 128

    /** 亮色模式：主色调的浓郁变体 (Tone 40 -> 80) */
    private val LIGHT_TONES = intArrayOf(40, 48, 56, 64, 72, 80)

    /** 暗色模式：主色调的明亮变体 (Tone 25 -> 55) */
    private val DARK_TONES = intArrayOf(25, 31, 37, 43, 49, 55)

    /**
     * 异步提取 [bitmap] 的主色并生成同色相渐变。
     */
    fun extractAsync(bitmap: Bitmap, callback: (ColorPaletteResult?) -> Unit) {
        scope.launch {
            try {
                val scaledBitmap = scaleBitmap(bitmap)
                val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
                scaledBitmap.getPixels(
                    pixels,
                    0,
                    scaledBitmap.width,
                    0,
                    0,
                    scaledBitmap.width,
                    scaledBitmap.height
                )

                if (scaledBitmap != bitmap) scaledBitmap.recycle()

                // 1. 获取量化结果
                val quantizerResult = QuantizerCelebi.quantize(pixels, 128)

                // 2. 评分获取最核心的主色
                val rankedColors = Score.score(quantizerResult)
                val seedColor = rankedColors.firstOrNull() ?: DEFAULT_SEED_COLOR

                // 3. 生成基于单一主色的结果
                val result = ColorPaletteResult(
                    lightModeColors = generateSingleHueSwatches(seedColor, LIGHT_TONES),
                    darkModeColors = generateSingleHueSwatches(seedColor, DARK_TONES)
                )

                withContext(Dispatchers.Main) {
                    callback(result)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }

    /**
     * 基于单一颜色种子，在同色相空间内根据不同 Tone 生成渐变色。
     */
    private fun generateSingleHueSwatches(seedColor: Int, tones: IntArray): ThemeColors {
        val hct = Hct.fromInt(seedColor)

        // 保证彩度足够，避免主色变体看起来像灰色
        val chroma = hct.chroma.coerceAtLeast(40.0)
        val tonalPalette = TonalPalette.fromHueAndChroma(hct.hue, chroma)

        val swatches = IntArray(tones.size) { i ->
            tonalPalette.tone(tones[i])
        }

        // 使用第一个 Tone 作为 primary 代表色
        return ThemeColors(swatches[0], swatches)
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_IMAGE_DIMENSION) return bitmap

        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide
        return bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
    }
}