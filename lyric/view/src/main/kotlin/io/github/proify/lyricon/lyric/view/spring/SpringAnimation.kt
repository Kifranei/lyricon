/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.spring

import android.view.Choreographer

/**
 * Spring 弹簧动画
 * 使用 Choreographer 驱动每帧计算，实现物理真实的弹簧动画效果。
 *
 * 使用示例：
 * ```kotlin
 * val spring = SpringAnimation(targetValue = 0f)
 *     .setSpring(SpringForce(100f).setDampingRatio(1.2f).setStiffness(100f))
 *     .addUpdateListener { value -> view.translationY = value }
 *     .start()
 * ```
 */
class SpringAnimation(
    /** 初始值 */
    initialValue: Float = 0f
) {
    /** 当前值 */
    var value: Float = initialValue
        private set

    /** 当前速度 */
    var velocity: Float = 0f
        private set

    /** 弹簧力配置 */
    var spring: SpringForce = SpringForce()
        private set

    /** 是否正在运行动画 */
    var isRunning: Boolean = false
        private set

    /** 值变化监听器 */
    private val updateListeners = mutableListOf<(Float) -> Unit>()

    /** 动画结束监听器 */
    private val endListeners = mutableListOf<(Boolean) -> Unit>()

    /** Choreographer 帧回调 */
    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        onFrame(frameTimeNanos)
    }

    /** 上一帧时间 */
    private var lastFrameTimeNanos: Long = 0L

    /** 速度缩放（用于阈值计算） */
    private var velocityScale: Float = 1.0f

    /** 是否需要跳到最终位置 */
    private var skipToEnd = false

    /** 待设置的最终位置（动画运行中修改时暂存） */
    private var pendingFinalPosition: Float? = null

    /**
     * 设置弹簧力
     */
    fun setSpring(spring: SpringForce): SpringAnimation {
        this.spring = spring
        return this
    }

    /**
     * 设置目标值（最终位置）
     * 动画运行中也可调用，会平滑过渡到新目标
     */
    fun setTargetValue(target: Float): SpringAnimation {
        if (isRunning) {
            pendingFinalPosition = target
        } else {
            spring.finalPosition = target.toDouble()
        }
        return this
    }

    /**
     * 设置速度缩放
     */
    fun setVelocityScale(scale: Float): SpringAnimation {
        velocityScale = scale
        return this
    }

    /**
     * 添加值更新监听器
     */
    fun addUpdateListener(listener: (Float) -> Unit): SpringAnimation {
        updateListeners.add(listener)
        return this
    }

    /**
     * 添加动画结束监听器
     * @param listener 参数为 Boolean: true=自然结束, false=被取消
     */
    fun addEndListener(listener: (Boolean) -> Unit): SpringAnimation {
        endListeners.add(listener)
        return this
    }

    /**
     * 移除值更新监听器
     */
    fun removeUpdateListener(listener: (Float) -> Unit): SpringAnimation {
        updateListeners.remove(listener)
        return this
    }

    /**
     * 开始动画
     */
    fun start(): SpringAnimation {
        if (isRunning) return this

        isRunning = true
        lastFrameTimeNanos = 0L
        spring.initThresholds(velocityScale)

        Choreographer.getInstance().postFrameCallback(frameCallback)
        return this
    }

    /**
     * 取消动画
     */
    fun cancel(): SpringAnimation {
        if (!isRunning) return this

        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lastFrameTimeNanos = 0L
        notifyEnd(canceled = true)
        return this
    }

    /**
     * 跳到最终位置并结束
     */
    fun skipToEnd(): SpringAnimation {
        if (!isRunning) return this
        skipToEnd = true
        return this
    }

    /**
     * 立即更新值（用于外部直接设置初始位置）
     */
    fun updateValue(newValue: Float): SpringAnimation {
        value = newValue
        velocity = 0f
        notifyUpdate()
        return this
    }

    /**
     * 对当前值施加一个偏移量（用于行切换时统一偏移所有行）
     * 不会中断正在进行的弹簧动画
     */
    fun offsetBy(delta: Float): SpringAnimation {
        spring.finalPosition += delta.toDouble()
        value += delta
        notifyUpdate()
        return this
    }

    private fun onFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        // 处理跳到最终位置
        if (skipToEnd) {
            skipToEnd = false
            value = spring.finalPosition.toFloat()
            velocity = 0f
            isRunning = false
            lastFrameTimeNanos = 0L
            notifyUpdate()
            notifyEnd(canceled = false)
            return
        }

        // 处理待设置的目标位置
        pendingFinalPosition?.let {
            spring.finalPosition = it.toDouble()
            pendingFinalPosition = null
        }

        // 计算帧间隔
        if (lastFrameTimeNanos == 0L) {
            lastFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(frameCallback)
            return
        }

        val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000L
        lastFrameTimeNanos = frameTimeNanos

        // 弹簧物理计算
        val result = spring.compute(value.toDouble(), velocity.toDouble(), deltaMs)
        value = result.position
        velocity = result.velocity

        // 检查是否达到静止状态
        if (spring.isAtRest(value, velocity)) {
            value = spring.finalPosition.toFloat()
            velocity = 0f
            isRunning = false
            lastFrameTimeNanos = 0L
            notifyUpdate()
            notifyEnd(canceled = false)
            return
        }

        notifyUpdate()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun notifyUpdate() {
        for (listener in updateListeners) {
            listener(value)
        }
    }

    private fun notifyEnd(canceled: Boolean) {
        for (listener in endListeners) {
            listener(canceled)
        }
    }
}
