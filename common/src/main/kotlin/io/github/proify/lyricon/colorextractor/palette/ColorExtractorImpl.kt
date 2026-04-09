/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 主色主导级颜色提取器（Primary-Dominant Edition）。
 * 优化点：使用 AndroidX ColorUtils 减少数学运算开销，采用原始数组消除 GC 压力。
 */
object ColorExtractorImpl {

    private const val DEFAULT_NUM_COLORS = 4
    private const val MAX_SAMPLE_PIXELS = 100 * 100 // 采样 100x100 像素足以覆盖大部分特征
    private const val KMEANS_ITERATIONS = 15 // 颜色空间中 15 次迭代通常即可收敛
    private const val SIGMA_SQ_2 = 2.0f * 25.0f * 25.0f // 引力常数：2 * sigma^2

    /**
     * 提取以主色为核心的主题色列表。
     * @param bitmap 输入图片
     * @param numColors 需要提取的颜色数量
     * @return 颜色 ARGB 列表
     */
    fun extract(bitmap: Bitmap, numColors: Int = DEFAULT_NUM_COLORS): List<Int> {
        if (bitmap.isRecycled) return emptyList()

        // 1. 缩放采样以提升性能
        val scaled = scaleBitmap(bitmap, MAX_SAMPLE_PIXELS)
        val w = scaled.width
        val h = scaled.height
        val size = w * h
        val rawPixels = IntArray(size)
        scaled.getPixels(rawPixels, 0, w, 0, 0, w, h)
        if (scaled != bitmap) scaled.recycle()

        // 2. 准备数据容器（使用原始数组避免对象包装）
        val lArr = FloatArray(size)
        val aArr = FloatArray(size)
        val bArr = FloatArray(size)
        val wArr = FloatArray(size)

        val outLab = DoubleArray(3) // 循环外复用转换容器

        for (i in 0 until size) {
            val pix = rawPixels[i]
            ColorUtils.colorToLAB(pix, outLab)

            val l = outLab[0].toFloat()
            val a = outLab[1].toFloat()
            val b = outLab[2].toFloat()
            val chroma = sqrt(a * a + b * b)

            lArr[i] = l
            aArr[i] = a
            bArr[i] = b
            // 基础权重：对高饱和度颜色进行加权 (pow 3 优化为乘法)
            wArr[i] = if (chroma > 5.0f) chroma * chroma * chroma else 0.0001f
        }

        // 3. 第一阶段：计算全局加权中心作为“锚点主色”
        var totalW = 0.0f
        var sumL = 0.0f
        var sumA = 0.0f
        var sumB = 0.0f
        for (i in 0 until size) {
            val weight = wArr[i]
            sumL += lArr[i] * weight
            sumA += aArr[i] * weight
            sumB += bArr[i] * weight
            totalW += weight
        }
        if (totalW == 0f) return emptyList()
        val anchorL = sumL / totalW
        val anchorA = sumA / totalW
        val anchorB = sumB / totalW

        // 4. 第二阶段：应用“主色引力”重新分配权重
        // 距离主色感知距离越近的颜色，权重增益越高，确保结果与主色视觉关联
        for (i in 0 until size) {
            val dl = lArr[i] - anchorL
            val da = aArr[i] - anchorA
            val db = bArr[i] - anchorB
            val distSq = dl * dl + da * da + db * db
            val gravity = exp(-distSq / SIGMA_SQ_2)
            wArr[i] *= (0.3f + 0.7f * gravity)
        }

        // 5. 最终聚类并按权重排序
        val k = numColors.coerceAtMost(size)
        val clusters = kMeansLabOptimized(lArr, aArr, bArr, wArr, k)

        return clusters.map {
            ColorUtils.LABToColor(it[0].toDouble(), it[1].toDouble(), it[2].toDouble())
        }
    }

    /**
     * 根据背景色自动调节颜色亮度，确保视觉可读性。
     * @param color 原始颜色
     * @param isDarkBackground 背景是否为深色
     * @param threshold 自定义亮度阈值 (0.0-1.0)，-1 表示使用默认
     */
    fun adaptiveLuminance(
        color: IntArray,
        isDarkBackground: Boolean,
        threshold: Float = -1f
    ): IntArray {
        return color.map {
            adaptiveLuminance(it, isDarkBackground, threshold)
        }.toIntArray()
    }

    /**
     * 根据背景色自动调节颜色亮度，确保视觉可读性。
     * @param color 原始颜色
     * @param isDarkBackground 目标背景是否为深色
     * @param threshold 自定义亮度阈值 (0.0-1.0)，-1 表示使用默认
     */
    fun adaptiveLuminance(color: Int, isDarkBackground: Boolean, threshold: Float = -1f): Int {
        val outLab = DoubleArray(3)
        ColorUtils.colorToLAB(color, outLab)

        val currentL = outLab[0].toFloat()
        val targetL = if (threshold > 0) {
            threshold * 100f
        } else {
            if (isDarkBackground) 65f else 35f // 深色背景拉升亮度，浅色背景压低亮度
        }

        val finalL = if (isDarkBackground) {
            if (currentL < targetL) targetL else currentL
        } else {
            if (currentL > targetL) targetL else currentL
        }

        return ColorUtils.LABToColor(finalL.toDouble(), outLab[1], outLab[2])
    }

    /**
     * 优化后的 K-means 聚类逻辑，移除所有集合操作。
     */
    private fun kMeansLabOptimized(
        lArr: FloatArray, aArr: FloatArray, bArr: FloatArray, wArr: FloatArray, k: Int
    ): List<FloatArray> {
        val size = lArr.size
        val cL = FloatArray(k)
        val cA = FloatArray(k)
        val cB = FloatArray(k)
        val cW = FloatArray(k)
        val assignments = IntArray(size)

        // 初始化中心点：随机采样
        repeat(k) { i ->
            val idx = Random.nextInt(size)
            cL[i] = lArr[idx]; cA[i] = aArr[idx]; cB[i] = bArr[idx]
        }

        repeat(KMEANS_ITERATIONS) {
            // 归类阶段
            for (i in 0 until size) {
                var minDist = Float.MAX_VALUE
                var closest = 0
                for (ci in 0 until k) {
                    val dl = lArr[i] - cL[ci]
                    val da = aArr[i] - cA[ci]
                    val db = bArr[i] - cB[ci]
                    val d = dl * dl + da * da + db * db
                    if (d < minDist) {
                        minDist = d
                        closest = ci
                    }
                }
                assignments[i] = closest
            }

            // 更新中心点阶段
            val nextL = FloatArray(k)
            val nextA = FloatArray(k)
            val nextB = FloatArray(k)
            val nextW = FloatArray(k)
            for (i in 0 until size) {
                val ci = assignments[i]
                val w = wArr[i]
                nextL[ci] += lArr[i] * w
                nextA[ci] += aArr[i] * w
                nextB[ci] += bArr[i] * w
                nextW[ci] += w
            }

            for (ci in 0 until k) {
                if (nextW[ci] > 0) {
                    cL[ci] = nextL[ci] / nextW[ci]
                    cA[ci] = nextA[ci] / nextW[ci]
                    cB[ci] = nextB[ci] / nextW[ci]
                    cW[ci] = nextW[ci]
                }
            }
        }

        // 返回包含 L, A, B, Weight 的数组列表并按权重排序
        return List(k) { i ->
            floatArrayOf(cL[i], cA[i], cB[i], cW[i])
        }.sortedByDescending { it[3] }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxPixels: Int): Bitmap {
        val totalPixels = bitmap.width * bitmap.height
        if (totalPixels <= maxPixels) return bitmap
        val scale = sqrt(maxPixels.toFloat() / totalPixels)
        return bitmap.scale(
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1)
        )
    }
}