/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel
import io.github.proify.lyricon.lyric.view.util.Interpolates
import kotlin.math.abs
import kotlin.math.max

/**
 * 歌词行渲染控制器
 * 采用委托模式管理渲染路径（Legacy Canvas 或 API 29+ RenderNode），
 * 并通过 Matrix 复用 Shader 彻底消除逐帧动画过程中的 GC 内存抖动。
 */
class Syllable(private val view: LyricLineView) {

    // --- 绘图画笔 ---
    private val inactivePaint = TextPaint(Paint.ANTI_ALIAS_FLAG) // 底色画笔
    private val activePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)   // 高亮色画笔

    // --- 核心组件 ---
    private val renderDelegate: LineRenderDelegate = createRenderDelegate()
    private val sharedRenderer = SharedLineRenderer()

    // --- 状态管理 ---
    private val progressAnimator = ProgressAnimator() // 处理高亮宽度动画
    private val scrollController = ScrollController() // 处理溢出文本的 X 轴偏移
    private var lastUpdatePosition = Long.MIN_VALUE

    var playListener: LyricPlayListener? = null

    /** 是否启用高亮边缘的渐变消隐效果 */
    var isGradientEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                renderDelegate.isGradientEnabled = value
                renderDelegate.notifyDirty()
            }
        }

    /** 纯滚动模式：仅根据时间滚动文本，不绘制高亮覆盖层 */
    var isOnlyScrollMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                renderDelegate.isOnlyScrollMode = value
                renderDelegate.notifyDirty()
            }
        }

    // 状态对外暴露
    val textSize: Float get() = inactivePaint.textSize
    val isStarted: Boolean get() = progressAnimator.hasStarted
    val isPlaying: Boolean get() = progressAnimator.isAnimating
    val isFinished: Boolean get() = progressAnimator.hasFinished

    init {
        syncLayoutInfo()
    }

    // --- 公开 API 配置接口 ---

    fun setColor(backgroundColor: Int, highlightColor: Int) {
        if (inactivePaint.color != backgroundColor || activePaint.color != highlightColor) {
            inactivePaint.color = backgroundColor
            activePaint.color = highlightColor
            sharedRenderer.clearShaderCache() // 颜色改变需强制刷新渐变器
            renderDelegate.notifyDirty()
        }
    }

    fun setTextSize(size: Float) {
        if (inactivePaint.textSize != size) {
            inactivePaint.textSize = size
            activePaint.textSize = size
            sharedRenderer.updateFontMetrics(inactivePaint) // 重新计算基线对齐参数
            renderDelegate.notifyDirty()
        }
    }

    fun setTypeface(typeface: Typeface) {
        if (inactivePaint.typeface != typeface) {
            inactivePaint.setTypeface(typeface)
            activePaint.setTypeface(typeface)
            sharedRenderer.updateFontMetrics(inactivePaint)
            renderDelegate.notifyDirty()
        }
    }

    /** 重置行状态，清空所有播放进度与滚动位置 */
    fun reset() {
        progressAnimator.reset()
        scrollController.reset(view)
        lastUpdatePosition = Long.MIN_VALUE
        renderDelegate.onHighlightUpdated(0f)
    }

    /** 定位到特定时间戳，用于 Seek 操作，不触发平滑插值 */
    fun seek(position: Long) {
        val model = view.lyricModel
        val word = model.wordTimingNavigator.first(position)
        val targetWidth = resolveTargetWidth(position, model, word)

        progressAnimator.jumpTo(targetWidth)
        scrollController.updateScroll(targetWidth, view)
        renderDelegate.onHighlightUpdated(targetWidth)

        lastUpdatePosition = position
        notifyProgress()
    }

    /** 由播放器时钟驱动，根据当前时间更新高亮目标宽度 */
    fun updateProgress(position: Long) {
        if (lastUpdatePosition != Long.MIN_VALUE && position < lastUpdatePosition) {
            seek(position)
            return
        }

        val model = view.lyricModel
        val word = model.wordTimingNavigator.first(position)
        val targetWidth = resolveTargetWidth(position, model, word)

        // 单词间衔接处理：若当前词刚开始，先跳转到上一个词末尾，避免进度跳跃
        if (word != null && progressAnimator.currentWidth == 0f) {
            word.previous?.let { progressAnimator.jumpTo(it.endPosition) }
        }

        if (targetWidth != progressAnimator.targetWidth) {
            progressAnimator.startAnimation(targetWidth, word?.duration ?: 0)
        }

        lastUpdatePosition = position
    }

    /** 逐帧刷新入口，返回是否需要重绘 UI */
    fun onFrameUpdate(frameTimeNanos: Long): Boolean {
        if (progressAnimator.doStep(frameTimeNanos)) {
            scrollController.updateScroll(progressAnimator.currentWidth, view)
            renderDelegate.onHighlightUpdated(progressAnimator.currentWidth)
            notifyProgress()
            return true
        }
        return false
    }

    /** 绘制入口 */
    fun draw(canvas: Canvas) {
        syncLayoutInfo()
        renderDelegate.draw(canvas, view.scrollXOffset)
    }

    // --- 内部辅助逻辑 ---

    private fun syncLayoutInfo() {
        sharedRenderer.updateFontMetrics(inactivePaint)
        renderDelegate.onLayoutChanged(view.measuredWidth, view.measuredHeight, view.isOverflow())
    }

    private fun resolveTargetWidth(pos: Long, model: LyricModel, word: WordModel?): Float = when {
        word != null -> word.endPosition
        pos >= model.end -> view.lyricWidth
        pos <= model.begin -> 0f
        else -> progressAnimator.currentWidth
    }

    private fun notifyProgress() {
        val current = progressAnimator.currentWidth
        val total = view.lyricWidth

        if (!progressAnimator.hasStarted && current > 0f) {
            progressAnimator.hasStarted = true
            playListener?.onPlayStarted(view)
        }
        if (!progressAnimator.hasFinished && current >= total) {
            progressAnimator.hasFinished = true
            playListener?.onPlayEnded(view)
        }
        playListener?.onPlayProgress(view, total, current)
    }

    // --- 动画与滚动控制器 ---

    private class ProgressAnimator {
        private val interpolator = Interpolates.linear
        var currentWidth = 0f; private set
        var targetWidth = 0f; private set
        var hasStarted = false
        var hasFinished = false
        var isAnimating = false; private set

        private var startWidth = 0f
        private var startTimeNanos = 0L
        private var durationNanos = 0L

        fun reset() {
            currentWidth = 0f; targetWidth = 0f; startWidth = 0f
            isAnimating = false; hasStarted = false; hasFinished = false
        }

        fun jumpTo(width: Float) {
            currentWidth = width; targetWidth = width; startWidth = width
            isAnimating = false
        }

        fun startAnimation(target: Float, durationMs: Long) {
            startWidth = currentWidth
            targetWidth = target
            startTimeNanos = System.nanoTime()
            durationNanos = max(1L, durationMs) * 1_000_000L
            isAnimating = true
        }

        fun doStep(now: Long): Boolean {
            if (!isAnimating) return false
            val elapsed = (now - startTimeNanos).coerceAtLeast(0L)
            if (elapsed >= durationNanos) {
                currentWidth = targetWidth
                isAnimating = false
                return true
            }
            val fraction = elapsed.toFloat() / durationNanos
            currentWidth =
                startWidth + (targetWidth - startWidth) * interpolator.getInterpolation(fraction)
            return true
        }
    }

    private class ScrollController {
        fun reset(view: LyricLineView) {
            view.scrollXOffset = 0f
            view.isScrollFinished = false
        }

        fun updateScroll(currentX: Float, view: LyricLineView) {
            if (!view.isOverflow()) {
                if (view.scrollXOffset != 0f) view.scrollXOffset = 0f
                return
            }
            // 文本中心对齐逻辑：当进度超过 View 一半时开始滚动
            val halfWidth = view.measuredWidth / 2f
            if (currentX > halfWidth) {
                val minScroll = -view.lyricWidth + view.measuredWidth
                val targetScroll = max(halfWidth - currentX, minScroll)
                view.scrollXOffset = targetScroll
                view.isScrollFinished = targetScroll <= minScroll
            } else {
                view.scrollXOffset = 0f
            }
        }
    }

    // --- 渲染实现路径 ---

    private interface LineRenderDelegate {
        var isGradientEnabled: Boolean
        var isOnlyScrollMode: Boolean
        fun onLayoutChanged(width: Int, height: Int, overflow: Boolean)
        fun onHighlightUpdated(highlightWidth: Float)
        fun draw(canvas: Canvas, scrollX: Float)
        fun notifyDirty()
    }

    /** 兼容模式：传统 Canvas 直接绘制 */
    private inner class LegacyRenderDelegate : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f

        override fun onLayoutChanged(width: Int, height: Int, overflow: Boolean) {
            this.width = width; this.height = height; this.overflow = overflow
        }

        override fun onHighlightUpdated(highlightWidth: Float) {
            this.highlightWidth = highlightWidth
        }

        override fun notifyDirty() {}

        override fun draw(canvas: Canvas, scrollX: Float) {
            sharedRenderer.executeDraw(
                canvas,
                view.lyricModel,
                width,
                height,
                scrollX,
                overflow,
                highlightWidth,
                isGradientEnabled,
                isOnlyScrollMode,
                inactivePaint,
                activePaint,
                view.textPaint
            )
        }
    }

    /** 高效模式：API 29+ 硬件加速 RenderNode，仅在数据变化时重录指令 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private inner class V29RenderDelegate : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private val renderNode by lazy { RenderNode("LyricLineNode") }
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f
        private var isDirty = true // 脏标记，用于判断是否需要重新记录指令

        override fun notifyDirty() {
            isDirty = true
        }

        override fun onLayoutChanged(width: Int, height: Int, overflow: Boolean) {
            if (this.width != width || this.height != height || this.overflow != overflow) {
                this.width = width; this.height = height; this.overflow = overflow
                renderNode.setPosition(0, 0, width, height)
                isDirty = true
            }
        }

        override fun onHighlightUpdated(highlightWidth: Float) {
            if (abs(this.highlightWidth - highlightWidth) > 0.1f) {
                this.highlightWidth = highlightWidth
                isDirty = true
            }
        }

        override fun draw(canvas: Canvas, scrollX: Float) {
            if (isDirty) {
                val recordingCanvas = renderNode.beginRecording(width, height)
                try {
                    recordingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    sharedRenderer.executeDraw(
                        recordingCanvas,
                        view.lyricModel,
                        width,
                        height,
                        scrollX,
                        overflow,
                        highlightWidth,
                        isGradientEnabled,
                        isOnlyScrollMode,
                        inactivePaint,
                        activePaint,
                        view.textPaint
                    )
                } finally {
                    renderNode.endRecording()
                }
                isDirty = false
            }
            canvas.drawRenderNode(renderNode)
        }
    }

    /**
     * 渲染逻辑核心类
     */
    private class SharedLineRenderer {
        private val fontMetrics = Paint.FontMetrics()
        private var baselineOffset = 0f

        private val shaderMatrix = Matrix()
        private val gradColors = intArrayOf(0, 0, Color.TRANSPARENT)
        private val gradPositions = floatArrayOf(0f, 0.9f, 1f) // 0~90%为纯色，90%~100%为渐变消隐
        private var cachedShader: LinearGradient? = null

        private var lastColor = 0
        private var isStandardRatio = true

        /** 更新字体度量信息并计算垂直居中基线偏移量 */
        fun updateFontMetrics(paint: TextPaint) {
            paint.getFontMetrics(fontMetrics)
            // 基线 = Y中心点 + (-(descent + ascent) / 2)
            baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
        }

        fun clearShaderCache() {
            cachedShader = null
        }

        /** 执行具体绘制指令 */
        fun executeDraw(
            canvas: Canvas, model: LyricModel, viewW: Int, viewH: Int,
            scrollX: Float, isOverflow: Boolean, highlightW: Float,
            enableGrad: Boolean, onlyScroll: Boolean,
            inactiveP: TextPaint, activeP: TextPaint, normalP: TextPaint
        ) {
            val baseline = (viewH / 2f) + baselineOffset

            canvas.withSave {
                // 处理文本对齐与滚动位移
                val tx =
                    if (isOverflow) scrollX else if (model.isAlignedRight) viewW - model.width else 0f
                translate(tx, 0f)

                if (onlyScroll) {
                    canvas.drawText(model.wordText, 0f, baseline, normalP)
                } else {
                    // 1. 绘制底色文本
                    canvas.drawText(model.wordText, 0f, baseline, inactiveP)

                    // 2. 根据进度裁剪区域绘制高亮色文本
                    if (highlightW > 0f) {
                        canvas.clipRect(0f, 0f, highlightW, viewH.toFloat())
                        if (enableGrad) {
                            applyGradient(activeP, highlightW, model.width)
                        } else {
                            activeP.shader = null
                        }
                        canvas.drawText(model.wordText, 0f, baseline, activeP)
                    }
                }
            }
        }

        /** 应用优化的渐变 Shader */
        private fun applyGradient(paint: Paint, highlightW: Float, textW: Float) {
            if (textW <= 0f) return
            val ratio = (highlightW / textW).coerceIn(0f, 1f)

            // 优化：只有在词末尾(>90%)渐变边缘才会动态收缩，其余时间比例固定
            val isStandard = ratio <= 0.90f
            val edge = if (isStandard) 0.90f else ratio

            // 检查缓存有效性：颜色变化、比例阶段切换、或词尾精细变动时才重建 Shader
            if (cachedShader == null || lastColor != paint.color || isStandardRatio != isStandard
                || (!isStandard && abs(gradPositions[1] - edge) > 0.01f)
            ) {

                gradColors[0] = paint.color
                gradColors[1] = paint.color
                gradPositions[1] = edge

                // 创建基准宽度为 1px 的 Shader
                cachedShader =
                    LinearGradient(0f, 0f, 1f, 0f, gradColors, gradPositions, Shader.TileMode.CLAMP)
                lastColor = paint.color
                isStandardRatio = isStandard
            }

            // 通过 Matrix 直接改变 Shader 覆盖范围，不会触发新对象分配
            shaderMatrix.setScale(highlightW, 1f)
            cachedShader?.setLocalMatrix(shaderMatrix)
            paint.shader = cachedShader
        }
    }

    private fun createRenderDelegate(): LineRenderDelegate =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) V29RenderDelegate() else LegacyRenderDelegate()
}