/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 针对音乐播放器优化的颜色提取器。
 * 核心逻辑：手动强制干预 HCT 分量，确保暗色模式下的颜色足够明亮。
 */
object ColorExtractor {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private const val DEFAULT_SEED_COLOR = 0xFF6750A4.toInt()
    private const val MAX_IMAGE_DIMENSION = 128

    /**
     * 异步提取颜色并生成调亮后的暗色模式色板。
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

                val quantizerResult = QuantizerCelebi.quantize(pixels, 128)
                val rankedColors = Score.score(quantizerResult)
                val seedColor = rankedColors.firstOrNull() ?: DEFAULT_SEED_COLOR

                val result = ColorPaletteResult(
                    // 亮色模式维持原样
                    lightModeColors = generateSwatches(seedColor, isDarkMode = false),
                    // 暗色模式进行手动强制调亮
                    darkModeColors = generateSwatches(seedColor, isDarkMode = true)
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
     * 手动调整 HCT 分量生成颜色。
     * @param isDarkMode 是否为暗色模式，如果是，则强制提升 Tone。
     */
    private fun generateSwatches(seedColor: Int, isDarkMode: Boolean): ThemeColors {
        val baseHct = Hct.fromInt(seedColor)
        val hue = baseHct.hue
        // 保证色彩存在感，不低于 40
        val chroma = baseHct.chroma.coerceAtLeast(40.0)

        // 定义暗色模式下的阶梯亮度 (从很亮到极亮)
        val targetTones = if (isDarkMode) {
            doubleArrayOf(75.0, 80.0, 85.0, 90.0, 94.0, 98.0)
        } else {
            doubleArrayOf(40.0, 48.0, 56.0, 64.0, 72.0, 80.0)
        }

        val swatches = IntArray(targetTones.size) { i ->
            // 核心修改点：直接新建 HCT 实例，强制注入目标亮度
            Hct.from(hue, chroma, targetTones[i]).toInt()
        }

        return ThemeColors(swatches[0], swatches)
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_IMAGE_DIMENSION) return bitmap

        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide
        return bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
    }
}