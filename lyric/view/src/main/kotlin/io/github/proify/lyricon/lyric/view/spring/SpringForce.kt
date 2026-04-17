/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.spring

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 弹簧力模型
 * 基于阻尼谐振子方程：F = -kx - cv
 *
 * 支持三种模式：
 * - 过阻尼 (dampingRatio > 1.0)：平滑趋近目标，无回弹
 * - 临界阻尼 (dampingRatio = 1.0)：最快无回弹趋近
 * - 欠阻尼 (dampingRatio < 1.0)：有弹跳
 */
class SpringForce(
    /** 最终位置/目标值 */
    var finalPosition: Double = Double.MAX_VALUE
) {
    companion object {
        /** 默认刚度（参考 AndroidX SpringAnimation） */
        const val DEFAULT_STIFFNESS = 1500.0f
        /** 默认阻尼比（0.5 = 欠阻尼，有弹跳） */
        const val DEFAULT_DAMPING_RATIO = 0.5f
    }

    /** 自然角频率 = √stiffness */
    private var naturalFreq: Double = sqrt(DEFAULT_STIFFNESS.toDouble())

    /** 阻尼比 */
    private var dampingRatio: Double = DEFAULT_DAMPING_RATIO.toDouble()

    /** 参数是否已更新需要重新计算系数 */
    private var configDirty = true

    // --- 过阻尼参数 ---
    private var gammaPos: Double = 0.0  // r1 = (-ζ + √(ζ²-1)) × ω_n
    private var gammaNeg: Double = 0.0  // r2 = (-ζ - √(ζ²-1)) × ω_n

    // --- 欠阻尼参数 ---
    private var dampedFreq: Double = 0.0  // ω_d = √(1-ζ²) × ω_n

    // --- 阈值 ---
    private var valueThreshold: Double = 0.0
    private var velocityThreshold: Double = 0.0

    /**
     * 设置阻尼比
     * @param ratio 阻尼比（< 1.0 欠阻尼, = 1.0 临界阻尼, > 1.0 过阻尼）
     */
    fun setDampingRatio(ratio: Float): SpringForce {
        require(ratio >= 0f) { "Damping ratio must be non-negative" }
        dampingRatio = ratio.toDouble()
        configDirty = true
        return this
    }

    /**
     * 设置刚度
     * @param stiffness 弹簧刚度（必须 > 0）
     */
    fun setStiffness(stiffness: Float): SpringForce {
        require(stiffness > 0f) { "Spring stiffness constant must be positive." }
        naturalFreq = sqrt(stiffness.toDouble())
        configDirty = true
        return this
    }

    /**
     * 初始化阈值（在动画开始时调用）
     */
    fun initThresholds(velocityScale: Float) {
        val absScale = abs(velocityScale.toDouble())
        valueThreshold = absScale
        velocityThreshold = absScale * 62.5
    }

    /**
     * 根据当前状态计算下一步的位置和速度
     * @param position 当前位置
     * @param velocity 当前速度
     * @param deltaMs 时间增量（毫秒）
     * @return 包含新位置和新速度的结果
     */
    fun compute(position: Double, velocity: Double, deltaMs: Long): Result {
        if (configDirty) {
            computeCoefficients()
            configDirty = false
        }

        val dt = deltaMs / 1000.0  // 转换为秒
        val displacement = position - finalPosition

        val newPos: Double
        val newVel: Double

        when {
            dampingRatio > 1.0 -> {
                // 过阻尼：x(t) = C1·e^(r1·t) + C2·e^(r2·t) + finalPos
                val c1 = (gammaNeg * displacement - velocity) / (gammaNeg - gammaPos)
                val c2 = displacement - c1

                val ePos = Math.E.pow(gammaPos * dt)
                val eNeg = Math.E.pow(gammaNeg * dt)

                newPos = ePos * c1 + eNeg * c2
                newVel = ePos * c1 * gammaPos + eNeg * c2 * gammaNeg
            }
            dampingRatio == 1.0 -> {
                // 临界阻尼：x(t) = (C1 + C2·t)·e^(-ω_n·t) + finalPos
                val c1 = naturalFreq * displacement + velocity
                val c2 = displacement
                val timeFunc = c1 * dt + c2
                val expDecay = Math.E.pow(-naturalFreq * dt)

                newPos = expDecay * timeFunc
                newVel = expDecay * c1 + expDecay * timeFunc * (-naturalFreq)
            }
            else -> {
                // 欠阻尼：x(t) = e^(-ζ·ω_n·t) × [C1·cos(ω_d·t) + C2·sin(ω_d·t)] + finalPos
                val invDampedFreq = 1.0 / dampedFreq
                val c1 = displacement
                val c2 = (dampingRatio * naturalFreq * displacement + velocity) * invDampedFreq

                val expDecay = Math.E.pow(-dampingRatio * naturalFreq * dt)
                val cosVal = kotlin.math.cos(dampedFreq * dt)
                val sinVal = kotlin.math.sin(dampedFreq * dt)

                newPos = expDecay * (cosVal * c1 + sinVal * c2)

                val dCos = -dampedFreq * sinVal * c1
                val dSin = dampedFreq * cosVal * c2
                newVel = (dCos + dSin) * expDecay - dampingRatio * naturalFreq * newPos
            }
        }

        result.position = (newPos + finalPosition).toFloat()
        result.velocity = newVel.toFloat()
        return result
    }

    /**
     * 检查动画是否可以结束
     */
    fun isAtRest(position: Float, velocity: Float): Boolean {
        return abs(velocity) < velocityThreshold &&
                abs(position.toDouble() - finalPosition) < valueThreshold
    }

    private fun computeCoefficients() {
        if (finalPosition == Double.MAX_VALUE) {
            throw IllegalStateException("Final position of the spring must be set before the animation starts")
        }
        when {
            dampingRatio > 1.0 -> {
                gammaPos = -dampingRatio * naturalFreq +
                        sqrt(dampingRatio * dampingRatio - 1.0) * naturalFreq
                gammaNeg = -dampingRatio * naturalFreq -
                        sqrt(dampingRatio * dampingRatio - 1.0) * naturalFreq
            }
            dampingRatio in 0.0..1.0 -> {
                dampedFreq = sqrt(1.0 - dampingRatio * dampingRatio) * naturalFreq
            }
        }
    }

    /** 复用结果对象，避免分配 */
    class Result {
        var position: Float = 0f
        var velocity: Float = 0f
    }

    private val result = Result()
}
