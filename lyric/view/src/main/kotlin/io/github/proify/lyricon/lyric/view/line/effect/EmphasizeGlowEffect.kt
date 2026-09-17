/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.effect

import io.github.proify.lyricon.lyric.view.line.effect.EmpEasing.get
import kotlin.math.abs
import kotlin.math.min

/**
 * 内置「Apple Music 式强调辉光」特效（默认启用）。
 *
 * 移植自 applemusic-like-lyrics 的逐字 emphasize 动画：
 * 满足强调条件的词（见 [io.github.proify.lyricon.lyric.view.line.model.EmphasisGroup]）
 * 被唱到时，组内字符按 `du / 2.5 / 字符数` 逐字错峰，依次播放
 *
 * - 缩放：`1 + eased · 0.1 · amount`（以字符中心为锚点）；
 * - 左右展开：`-eased · 0.03 · amount · (n/2 - i)` em（离组中心越远的字位移越大）；
 * - 上浮：`-eased · 0.025 · amount` em；
 * - 白色辉光：半径恒定 `min(0.3, blur · 0.3)` em，强度 `eased · blur`；
 *
 * 缓动与源项目一致：分段三次贝塞尔（前段 `cubic-bezier(0.2, 0.4, 0.58, 1.0)`，
 * 后段 `1 - cubic-bezier(0.3, 0, 0.58, 1.0)`，中点 0.5）；动画未开始时保持
 * 第一帧（源项目 `fill: both` 行为），完成后保持最终帧。
 */
internal class EmphasizeGlowEffect : LyricUnitEffect {

    override val name: String = "emphasize_glow"

    override fun apply(unit: DrawUnit, param: EffectParam, out: UnitTransform) {
        val group = unit.emphasisGroup ?: return

        val startMs = group.charStartMs(unit.emphasisCharIndex)
        val elapsed = param.currentTimeMs - startMs
        val du = group.durationMs

        // 动画未开始时保持第一帧（源项目 fill:both）；完成后停在最终帧（x = 1）。
        val x = when {
            elapsed <= 0L -> FIRST_FRAME_X
            elapsed >= du -> 1f
            else -> elapsed.toFloat() / du
        }
        val eased = EmpEasing.get(x)

        // 空间受限时整体收缩幅度，避免字形被挤出视图边界。
        val strength = param.strength

        // 缩放（绝对量，与其它特效的 scale 相乘叠加以保持链条语义）。
        out.scale *= 1f + eased * SCALE_FACTOR * group.amount * strength

        // 左右展开：离组中心越远的字位移越大（em 单位）。
        val spread = group.charCount / 2f - unit.emphasisCharIndex
        out.dx += -eased * SPREAD_FACTOR * group.amount * spread * param.textSize * strength

        // 上浮（em 单位）。
        out.dy += -eased * LIFT_FACTOR * group.amount * param.textSize * strength

        // 白色辉光：半径恒定，强度随 eased 变化。
        val glowLevel = eased * group.blur * strength
        if (glowLevel > GLOW_ALPHA_THRESHOLD) {
            out.glowRadius = min(GLOW_RADIUS_MAX, group.blur * GLOW_RADIUS_SCALE) * param.textSize * strength
            out.glowAlpha = glowLevel
        }
    }

    private companion object {
        /** 动画帧数（源项目 ANIMATION_FRAME_QUANTITY = 32）。 */
        const val ANIMATION_FRAME_QUANTITY = 32

        /** 未开始时的第一帧 x（fill:both 期间保持）。 */
        const val FIRST_FRAME_X = 1f / ANIMATION_FRAME_QUANTITY

        const val SCALE_FACTOR = 0.1f
        const val SPREAD_FACTOR = 0.03f
        const val LIFT_FACTOR = 0.025f
        const val GLOW_RADIUS_MAX = 0.3f
        const val GLOW_RADIUS_SCALE = 0.3f

        /**
         * 辉光强度阈值：低于该值的辉光肉眼不可见，跳过 shadowLayer（Canvas 阴影生成
         * 是逐字绘制热路径的主要开销之一）。
         */
        const val GLOW_ALPHA_THRESHOLD = 0.01f
    }
}

/**
 * 分段三次贝塞尔缓动（源项目 `makeEmpEasing(0.5)`）：
 * `x < 0.5` 时取 bezIn(0.2, 0.4, 0.58, 1.0) 的前半段，
 * `x >= 0.5` 时取 1 - bezOut(0.3, 0, 0.58, 1.0) 的后半段。
 *
 * 结果预计算为查找表（512 项），[get] 为 O(1) 索引：逐字特效每帧被调用上百次，
 * 直接求解贝塞尔方程会成为 CPU 热路径。
 */
internal object EmpEasing {

    private const val MID = 0.5f

    private const val LUT_SIZE = 512

    private val lut: FloatArray = FloatArray(LUT_SIZE + 1).also { table ->
        for (i in 0..LUT_SIZE) {
            val x = i.toFloat() / LUT_SIZE
            table[i] = if (x < MID) {
                CubicBezierEasing.eval(x / MID, 0.2f, 0.4f, 0.58f, 1f)
            } else {
                1f - CubicBezierEasing.eval((x - MID) / (1f - MID), 0.3f, 0f, 0.58f, 1f)
            }
        }
    }

    fun get(x: Float): Float {
        if (x <= 0f) return lut[0]
        if (x >= 1f) return lut[LUT_SIZE]
        val pos = x * LUT_SIZE
        val i = pos.toInt()
        val frac = pos - i
        return lut[i] + (lut[i + 1] - lut[i]) * frac
    }
}

/**
 * 三次贝塞尔缓动求解器：以 x 为时间轴求 y，算法与 bezier-easing 库一致
 * （Newton-Raphson 迭代 + 二分兜底）。纯函数、无分配。
 */
internal object CubicBezierEasing {

    private const val NEWTON_ITERATIONS = 4
    private const val NEWTON_MIN_SLOPE = 0.001f
    private const val SUBDIVISION_PRECISION = 0.0000001f
    private const val SUBDIVISION_MAX_ITERATIONS = 10

    fun eval(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        var t = x
        // Newton-Raphson 迭代求 t(x)
        repeat(NEWTON_ITERATIONS) {
            val currentSlope = slope(t, x1, x2)
            if (abs(currentSlope) < NEWTON_MIN_SLOPE) return bezier(t, y1, y2)
            val currentX = bezier(t, x1, x2) - x
            t -= currentX / currentSlope
        }
        // 牛顿已收敛时直接返回，避免二分劣化精度
        if (abs(bezier(t, x1, x2) - x) < SUBDIVISION_PRECISION) {
            return bezier(t, y1, y2)
        }
        // 二分兜底（牛顿不收敛时）
        var low = 0f
        var high = 1f
        repeat(SUBDIVISION_MAX_ITERATIONS) {
            val currentX = bezier(t, x1, x2)
            if (currentX > x) {
                high = t
            } else {
                low = t
            }
            t = (low + high) / 2f
        }
        return bezier(t, y1, y2)
    }

    private fun slope(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * a + 6f * u * t * (b - a) + 3f * t * t * (1f - b)
    }

    private fun bezier(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
    }
}
