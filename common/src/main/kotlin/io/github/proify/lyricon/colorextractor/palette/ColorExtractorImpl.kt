/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import io.github.proify.lyricon.colorextractor.palette.ColorExtractorImpl.KMEANS_TRIALS
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 主色主导级颜色提取器（Theme-Adaptive Edition）。
 *
 * 从位图中提取代表性颜色，并针对浅色/深色背景自动生成高对比度衍生色。
 * 核心流程：
 * 1. 缩放图片以限制最大采样像素数
 * 2. 转换至 Lab 空间，通过色度及全局质心距离赋予权重
 * 3. 使用加权 K-means++ 聚类，多次运行取类内误差最小结果
 * 4. 按色相和 Lab 距离去重，保留顶部颜色
 * 5. 在 HSL 空间根据目标背景亮度区间调整 L 分量，并验证 WCAG 对比度
 */
object ColorExtractorImpl {

    // ---------- 默认提取参数 ----------
    /** 未指定 [maxColors] 时的默认提取颜色数 */
    private const val DEFAULT_MAX_COLORS = 4
    /** 最大采样像素总数，用于控制性能 */
    private const val MAX_SAMPLE_PIXELS = 150 * 150

    // ---------- K-means 聚类常量 ----------
    /** K-means 迭代次数 */
    private const val KMEANS_ITERATIONS = 15
    /** K-means 聚类中心倍数：以 maxColors × 此倍数的中心数进行聚类，再合并筛选 */
    private const val KMEANS_MULTIPLIER = 3
    /** K-means 运行次数，取类内加权误差平方和最小的解 */
    private const val KMEANS_TRIALS = 3

    // ---------- 全局质心衰减 ----------
    /** 全局锚点高斯衰减系数中的方差项（2σ²） */
    private const val SIGMA_SQ_2 = 2.0f * 25.0f * 25.0f
    /** 锚点权重衰减基础值（完全远离锚点时权重保留比例） */
    private const val CENTER_WEIGHT_BASE = 0.3f
    /** 锚点权重衰减缩放值（靠近锚点时的额外权重） */
    private const val CENTER_WEIGHT_SCALE = 0.7f

    // ---------- 去重阈值 ----------
    /** 默认色相阈值（度），高饱和颜色之间需大于此值才视为不同 */
    private const val DEFAULT_HUE_THRESHOLD = 45f
    /** 默认 Lab 距离阈值，低饱和颜色需大于此值才保留 */
    private const val DEFAULT_DIST_THRESHOLD = 20.0

    // ---------- 背景自适应 ----------
    /** 深色背景上推荐颜色亮度的最低值 */
    private const val DARK_BG_LIGHTNESS_MIN = 0.70f
    /** 深色背景上推荐颜色亮度的最高值 */
    private const val DARK_BG_LIGHTNESS_MAX = 0.85f
    /** 浅色背景上推荐颜色亮度的最低值 */
    private const val LIGHT_BG_LIGHTNESS_MIN = 0.30f
    /** 浅色背景上推荐颜色亮度的最高值 */
    private const val LIGHT_BG_LIGHTNESS_MAX = 0.45f
    /** WCAG 最小对比度要求（AA 级正常文本） */
    private const val MIN_CONTRAST_RATIO = 4.5f
    /** 对比度不足时亮度调整步长 */
    private const val L_ADJUST_STEP = 0.02f
    /** 对比度满足前的最大尝试次数 */
    private const val MAX_ADJUST_ATTEMPTS = 10

    /**
     * 提取具备背景适配能力的主题调色板。
     *
     * @param bitmap 输入位图，不可为已回收状态
     * @param maxColors 期望提取的最大颜色数量
     * @param hueThreshold 色相去重阈值（度），值越大允许更相近的色相
     * @param distThreshold Lab 距离去重阈值，值越小保留越多相近颜色
     * @param seed 随机种子，用于固定聚类结果；为 null 时每次随机
     * @return [ThemePalette] 包含原始代表色及深/浅背景适配色
     */
    fun extractThemePalette(
        bitmap: Bitmap,
        maxColors: Int = DEFAULT_MAX_COLORS,
        hueThreshold: Float = DEFAULT_HUE_THRESHOLD,
        distThreshold: Double = DEFAULT_DIST_THRESHOLD,
        seed: Long? = null
    ): ThemePalette {
        val raw = extract(bitmap, maxColors, hueThreshold, distThreshold, seed)
        return ThemePalette(
            rawColors = raw,
            onWhiteBackground = raw.map { adaptForBackground(it, isDarkBg = false) },
            onBlackBackground = raw.map { adaptForBackground(it, isDarkBg = true) }
        )
    }

    /**
     * 核心颜色提取逻辑，返回按重要性排序的代表色列表。
     *
     * @param bitmap 输入位图，不可为已回收状态
     * @param maxColors 期望提取的最大颜色数量
     * @param hueThreshold 色相去重阈值
     * @param distThreshold Lab 距离去重阈值
     * @param seed 随机种子
     * @return 代表色列表，可能少于 [maxColors] 但不会为空（除非输入全透明）
     */
    fun extract(
        bitmap: Bitmap,
        maxColors: Int = DEFAULT_MAX_COLORS,
        hueThreshold: Float = DEFAULT_HUE_THRESHOLD,
        distThreshold: Double = DEFAULT_DIST_THRESHOLD,
        seed: Long? = null
    ): List<Int> {
        require(!bitmap.isRecycled) { "Bitmap is already recycled" }

        val scaled = scaleBitmap(bitmap, MAX_SAMPLE_PIXELS)
        val size = scaled.width * scaled.height
        val rawPixels = IntArray(size)
        scaled.getPixels(rawPixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled != bitmap) scaled.recycle()

        val lArr = FloatArray(size)
        val aArr = FloatArray(size)
        val bArr = FloatArray(size)
        val wArr = FloatArray(size)
        val outLab = DoubleArray(3)
        var sumL = 0f
        var sumA = 0f
        var sumB = 0f
        var totalW = 0f

        // 1. 转换为 Lab 并计算初始色度权重
        for (i in 0 until size) {
            ColorUtils.colorToLAB(rawPixels[i], outLab)
            val l = outLab[0].toFloat()
            val a = outLab[1].toFloat()
            val b = outLab[2].toFloat()
            val chroma = sqrt(a * a + b * b)
            lArr[i] = l
            aArr[i] = a
            bArr[i] = b
            // 鲜艳颜色权重高，低饱和颜色给予基础权重
            val weight = if (chroma > 5.0f) chroma * chroma else 0.1f
            wArr[i] = weight
            sumL += l * weight
            sumA += a * weight
            sumB += b * weight
            totalW += weight
        }

        if (totalW == 0f) return emptyList()

        // 2. 基于全局加权质心调整权重，增强主体区域，抑制边缘杂色
        val anchorL = sumL / totalW
        val anchorA = sumA / totalW
        val anchorB = sumB / totalW
        for (i in 0 until size) {
            val distSq = (lArr[i] - anchorL).let { it * it } +
                    (aArr[i] - anchorA).let { it * it } +
                    (bArr[i] - anchorB).let { it * it }
            wArr[i] *= (CENTER_WEIGHT_BASE + CENTER_WEIGHT_SCALE * exp(-distSq / SIGMA_SQ_2))
        }

        // 3. K-means++ 聚类
        val k = (maxColors * KMEANS_MULTIPLIER).coerceAtMost(size)
        val clusters = kMeansLabOptimized(lArr, aArr, bArr, wArr, k, seed)

        // 4. 去重并返回最终颜色
        return filterRepresentations(clusters, maxColors, hueThreshold, distThreshold)
    }

    /**
     * 根据目标背景明暗，在 HSL 空间调整亮度，并强制满足 WCAG 对比度要求。
     *
     * @param color 原始颜色
     * @param isDarkBg `true` 表示目标背景为深色，需要亮色前景
     * @return 调整后的颜色
     */
    private fun adaptForBackground(color: Int, isDarkBg: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        // 根据背景类型确定目标亮度区间
        val (targetMin, targetMax) = if (isDarkBg)
            DARK_BG_LIGHTNESS_MIN to DARK_BG_LIGHTNESS_MAX
        else
            LIGHT_BG_LIGHTNESS_MIN to LIGHT_BG_LIGHTNESS_MAX

        // 将亮度限制在目标区间内
        hsl[2] = hsl[2].coerceIn(targetMin, targetMax)
        var adjustedColor = ColorUtils.HSLToColor(hsl)

        // 准备参考背景色（纯黑或纯白）
        val backgroundColor = if (isDarkBg) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        var attempts = 0

        // 若对比度不足，沿方向微调亮度
        while (attempts < MAX_ADJUST_ATTEMPTS &&
            ColorUtils.calculateContrast(adjustedColor, backgroundColor) < MIN_CONTRAST_RATIO
        ) {
            if (isDarkBg) {
                hsl[2] = (hsl[2] + L_ADJUST_STEP).coerceAtMost(1.0f)
            } else {
                hsl[2] = (hsl[2] - L_ADJUST_STEP).coerceAtLeast(0.0f)
            }
            adjustedColor = ColorUtils.HSLToColor(hsl)

            // 如果亮度已达极值仍不满足，终止调整
            if (hsl[2] >= 1.0f || hsl[2] <= 0.0f) break
            attempts++
        }

        return adjustedColor
    }

    /**
     * 从聚类结果中滤出最多 [maxColors] 个互不相似的代表色。
     *
     * 判断依据：
     * - 高饱和色（S > 0.15）使用色相角差 [hueThreshold] 去重
     * - 低饱和色使用 Lab 距离 [distThreshold] 去重
     */
    private fun filterRepresentations(
        clusters: List<FloatArray>,
        maxColors: Int,
        hueThreshold: Float,
        distThreshold: Double
    ): List<Int> {
        val selected = mutableListOf<Int>()
        val selectedHues = mutableListOf<Float>()
        val outHsl = FloatArray(3)

        for (cluster in clusters) {
            if (selected.size >= maxColors) break
            val color = ColorUtils.LABToColor(
                cluster[0].toDouble(), cluster[1].toDouble(), cluster[2].toDouble()
            )
            ColorUtils.colorToHSL(color, outHsl)
            val h = outHsl[0]
            val s = outHsl[1]

            var isDistinct = true
            if (s > 0.15f) {
                // 高饱和色：检查色相差异
                for (sh in selectedHues) {
                    val diff = abs(h - sh)
                    if ((if (diff > 180) 360 - diff else diff) < hueThreshold) {
                        isDistinct = false
                        break
                    }
                }
            } else {
                // 低饱和色：检查 Lab 距离
                for (sc in selected) {
                    if (calculateLabDistance(color, sc) < distThreshold) {
                        isDistinct = false
                        break
                    }
                }
            }

            if (isDistinct) {
                selected.add(color)
                selectedHues.add(h)
            }
        }

        // 安全回退：如果过滤后为空，至少包含权重最高的聚类
        if (selected.isEmpty() && clusters.isNotEmpty()) {
            selected.add(
                ColorUtils.LABToColor(
                    clusters[0][0].toDouble(), clusters[0][1].toDouble(), clusters[0][2].toDouble()
                )
            )
        }
        return selected
    }

    /** 计算两个 sRGB 颜色在 CIELAB 空间中的欧氏距离 */
    private fun calculateLabDistance(c1: Int, c2: Int): Double {
        val lab1 = DoubleArray(3)
        val lab2 = DoubleArray(3)
        ColorUtils.colorToLAB(c1, lab1)
        ColorUtils.colorToLAB(c2, lab2)
        return sqrt(
            (lab1[0] - lab2[0]).let { it * it } +
                    (lab1[1] - lab2[1]).let { it * it } +
                    (lab1[2] - lab2[2]).let { it * it }
        )
    }

    /**
     * 加权 K-means 聚类（CIELAB 空间）。
     *
     * 优化点：
     * - 使用 K-means++ 初始化，分散初始中心
     * - 执行 [KMEANS_TRIALS] 次，选择加权误差平方和最小的解
     * - 支持可选的随机种子
     *
     * @param lArr,aArr,bArr CIELAB 分量数组
     * @param wArr 各像素权重
     * @param k 聚类中心数量
     * @param seed 随机种子，为 null 则不固定
     * @return 按总权重降序排列的聚类中心列表，每个元素为 [L, A, B, totalWeight]
     */
    private fun kMeansLabOptimized(
        lArr: FloatArray, aArr: FloatArray, bArr: FloatArray,
        wArr: FloatArray, k: Int, seed: Long?
    ): List<FloatArray> {
        if (k <= 0 || lArr.isEmpty()) return emptyList()

        val size = lArr.size
        var bestClusters: List<FloatArray>? = null
        var bestError = Double.MAX_VALUE

        val random = seed?.let { Random(it) } ?: Random

        // 多次运行，保留最优
        repeat(KMEANS_TRIALS) {
            // --- K-means++ 初始化 ---
            val cL = FloatArray(k)
            val cA = FloatArray(k)
            val cB = FloatArray(k)

            val firstIdx = random.nextInt(size)
            cL[0] = lArr[firstIdx]
            cA[0] = aArr[firstIdx]
            cB[0] = bArr[firstIdx]

            val minDistSq = FloatArray(size) { Float.MAX_VALUE }
            for (ci in 1 until k) {
                var sumDistSq = 0.0
                // 计算到当前最近中心的距离
                for (i in 0 until size) {
                    val d = (lArr[i] - cL[ci - 1]).let { it * it } +
                            (aArr[i] - cA[ci - 1]).let { it * it } +
                            (bArr[i] - cB[ci - 1]).let { it * it }
                    if (d < minDistSq[i]) minDistSq[i] = d
                    sumDistSq += minDistSq[i].toDouble()
                }
                // 按距离平方比例选取下一个中心
                val threshold = random.nextDouble() * sumDistSq
                var cumulative = 0.0
                var nextIdx = 0
                for (i in 0 until size) {
                    cumulative += minDistSq[i]
                    if (cumulative >= threshold) {
                        nextIdx = i
                        break
                    }
                }
                cL[ci] = lArr[nextIdx]
                cA[ci] = aArr[nextIdx]
                cB[ci] = bArr[nextIdx]
            }

            val assignments = IntArray(size)

            // Lloyd 迭代
            repeat(KMEANS_ITERATIONS) {
                // 分配每个像素到最近中心
                for (i in 0 until size) {
                    var minDist = Float.MAX_VALUE
                    var closest = 0
                    for (ci in 0 until k) {
                        val d = (lArr[i] - cL[ci]).let { it * it } +
                                (aArr[i] - cA[ci]).let { it * it } +
                                (bArr[i] - cB[ci]).let { it * it }
                        if (d < minDist) {
                            minDist = d
                            closest = ci
                        }
                    }
                    assignments[i] = closest
                }
                // 用加权均值更新中心
                val nL = FloatArray(k)
                val nA = FloatArray(k)
                val nB = FloatArray(k)
                val nW = FloatArray(k)
                for (i in 0 until size) {
                    val ci = assignments[i]
                    val w = wArr[i]
                    nL[ci] += lArr[i] * w
                    nA[ci] += aArr[i] * w
                    nB[ci] += bArr[i] * w
                    nW[ci] += w
                }
                for (ci in 0 until k) {
                    if (nW[ci] > 0) {
                        cL[ci] = nL[ci] / nW[ci]
                        cA[ci] = nA[ci] / nW[ci]
                        cB[ci] = nB[ci] / nW[ci]
                    }
                }
            }

            // 计算加权误差平方和
            var error = 0.0
            for (i in 0 until size) {
                val ci = assignments[i]
                val d = (lArr[i] - cL[ci]).let { it * it } +
                        (aArr[i] - cA[ci]).let { it * it } +
                        (bArr[i] - cB[ci]).let { it * it }
                error += wArr[i] * d
            }

            if (error < bestError) {
                bestError = error
                bestClusters = List(k) { i ->
                    var tw = 0f
                    for (j in 0 until size) if (assignments[j] == i) tw += wArr[j]
                    floatArrayOf(cL[i], cA[i], cB[i], tw)
                }.sortedByDescending { it[3] }
            }
        }

        return bestClusters ?: emptyList()
    }

    /**
     * 缩放位图使其总像素数不超过 [maxPixels]。
     * 若原图已在限制内，直接返回原图。
     */
    private fun scaleBitmap(bitmap: Bitmap, maxPixels: Int): Bitmap {
        val totalPixels = bitmap.width * bitmap.height
        if (totalPixels <= maxPixels) return bitmap
        val scale = sqrt(maxPixels.toFloat() / totalPixels)
        return bitmap.scale(
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1)
        )
    }

    /**
     * 主题调色板结果集。
     *
     * @param rawColors 从图片直接提取的原始代表色列表
     * @param onWhiteBackground 适合显示在白色背景上的颜色（高对比度暗色）
     * @param onBlackBackground 适合显示在黑色背景上的颜色（高对比度亮色）
     */
    data class ThemePalette(
        val rawColors: List<Int>,
        val onWhiteBackground: List<Int>,
        val onBlackBackground: List<Int>
    )
}