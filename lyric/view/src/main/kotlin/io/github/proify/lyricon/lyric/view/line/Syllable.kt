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
import kotlin.math.abs
import kotlin.math.max

/**
 * 歌词行渲染控制器
 * 负责单行歌词的动画推进、滚动计算与 Canvas 绘制。
 */
class Syllable(private val view: LyricLineView) {

    private val backgroundPaint = TextPaintX()
    private val highlightPaint = TextPaintX()

    private val renderDelegate: LineRenderDelegate =
        if (Build.VERSION.SDK_INT >= 29) HardwareRenderer() else SoftwareRenderer()

    private val textRenderer = LineTextRenderer()
    private val progressAnimator = ProgressAnimator()
    private val scrollController = ScrollController()

    private var lastPosition = Long.MIN_VALUE

    var playListener: LyricPlayListener? = null

    var isGradientEnabled: Boolean = true
        set(value) {
            field = value
            renderDelegate.isGradientEnabled = value
            renderDelegate.invalidate()
        }

    var isScrollOnly: Boolean = false
        set(value) {
            field = value
            renderDelegate.isOnlyScrollMode = value
            renderDelegate.invalidate()
        }

    val textSize: Float get() = backgroundPaint.textSize
    val isStarted: Boolean get() = progressAnimator.hasStarted
    val isPlaying: Boolean get() = progressAnimator.isAnimating
    val isFinished: Boolean get() = progressAnimator.hasFinished

    init {
        updateLayoutMetrics()
    }

    // --- Public APIs ---

    fun setColor(background: Int, highlight: Int) {
        if (backgroundPaint.color != background || highlightPaint.color != highlight) {
            backgroundPaint.color = background
            highlightPaint.color = highlight
            textRenderer.clearShaderCache()
            renderDelegate.invalidate()
        }
    }

    fun setTextSize(size: Float) {
        if (backgroundPaint.textSize != size) {
            backgroundPaint.textSize = size
            highlightPaint.textSize = size
            reLayout()
        }
    }

    /**
     * 重新计算布局、度量和滚动偏移
     */
    fun reLayout() {
        // 1. 更新字体度量
        textRenderer.updateMetrics(backgroundPaint)

        // 2. 如果播放已经结束，强制进度对齐到新的总宽度
        // 否则 progressAnimator 记录的依然是旧字号下的旧宽度
        if (isFinished) {
            progressAnimator.jumpTo(view.lyricWidth)
        }

        // 3. 重新计算渲染节点的布局边界
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())

        // 4. 强制 ScrollController 修正当前的滚动偏移
        // 这步最关键：它会根据新宽度计算正确的 scrollXOffset，消除空白
        scrollController.update(progressAnimator.currentWidth, view)

        // 5. 触发重绘
        renderDelegate.invalidate()
        view.invalidate()
    }

    fun setTypeface(typeface: Typeface?) {
        if (backgroundPaint.typeface != typeface) {
            backgroundPaint.typeface = typeface
            highlightPaint.typeface = typeface
            textRenderer.updateMetrics(backgroundPaint)
            renderDelegate.invalidate()
        }
    }

    fun reset() {
        progressAnimator.reset()
        scrollController.reset(view)
        lastPosition = Long.MIN_VALUE
        renderDelegate.onHighlightUpdate(0f)
    }

    fun seek(position: Long) {
        val targetWidth = calculateTargetWidth(position)
        progressAnimator.jumpTo(targetWidth)
        scrollController.update(targetWidth, view)
        renderDelegate.onHighlightUpdate(targetWidth)
        lastPosition = position
        notifyProgressUpdate()
    }

    fun updateProgress(position: Long) {
        // 防止进度回退时的抖动，如果回退则执行 seek
        if (lastPosition != Long.MIN_VALUE && position < lastPosition) {
            seek(position)
            return
        }

        val model = view.lyricModel
        val currentWord = model.wordTimingNavigator.first(position)
        val targetWidth = calculateTargetWidth(position, currentWord)

        // 自动补全上一词的进度
        if (currentWord != null && progressAnimator.currentWidth == 0f) {
            currentWord.previous?.let { progressAnimator.jumpTo(it.endPosition) }
        }

        if (targetWidth != progressAnimator.targetWidth) {
            progressAnimator.start(targetWidth, currentWord?.duration ?: 0)
        }
        lastPosition = position
    }

    fun onFrameUpdate(nanoTime: Long): Boolean {
        if (progressAnimator.step(nanoTime)) {
            scrollController.update(progressAnimator.currentWidth, view)
            renderDelegate.onHighlightUpdate(progressAnimator.currentWidth)
            notifyProgressUpdate()
            return true
        }
        return false
    }

    fun draw(canvas: Canvas) {
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
        renderDelegate.draw(canvas, view.scrollXOffset)
    }

    // --- Private Helpers ---

    private fun updateLayoutMetrics() {
        textRenderer.updateMetrics(backgroundPaint)
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
    }

    private fun calculateTargetWidth(
        pos: Long,
        word: WordModel? = view.lyricModel.wordTimingNavigator.first(pos)
    ): Float = when {
        word != null -> word.endPosition
        pos >= view.lyricModel.end -> view.lyricWidth
        pos <= view.lyricModel.begin -> 0f
        else -> progressAnimator.currentWidth
    }

    private fun notifyProgressUpdate() {
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

    // --- Internal Components ---

    private class ProgressAnimator {
        var currentWidth = 0f
        var targetWidth = 0f
        var isAnimating = false
        var hasStarted = false
        var hasFinished = false

        private var startWidth = 0f
        private var startTimeNano = 0L
        private var durationNano = 1L

        fun reset() {
            currentWidth = 0f
            targetWidth = 0f
            isAnimating = false
            hasStarted = false
            hasFinished = false
        }

        fun jumpTo(width: Float) {
            currentWidth = width
            targetWidth = width
            isAnimating = false
        }

        fun start(target: Float, durationMs: Long) {
            startWidth = currentWidth
            targetWidth = target
            durationNano = max(1L, durationMs) * 1_000_000L
            startTimeNano = System.nanoTime()
            isAnimating = true
        }

        fun step(now: Long): Boolean {
            if (!isAnimating) return false
            val elapsed = (now - startTimeNano).coerceAtLeast(0L)
            if (elapsed >= durationNano) {
                currentWidth = targetWidth
                isAnimating = false
                return true
            }
            val progress = elapsed.toFloat() / durationNano
            currentWidth = startWidth + (targetWidth - startWidth) * progress
            return true
        }
    }

    private class ScrollController {
        fun reset(v: LyricLineView) {
            v.scrollXOffset = 0f
            v.isScrollFinished = false
        }

        fun update(currentX: Float, v: LyricLineView) {
            val lyricW = v.lyricWidth
            val viewW = v.measuredWidth.toFloat()

            if (lyricW <= viewW) {
                v.scrollXOffset = 0f
                return
            }

            val minScroll = -(lyricW - viewW)

            // 如果已经唱完了，不管 currentX 是多少，偏移量必须是 minScroll
            if (v.isPlayFinished()) {
                v.scrollXOffset = minScroll
                v.isScrollFinished = true
                return
            }

            val halfWidth = viewW / 2f
            if (currentX > halfWidth) {
                v.scrollXOffset = (halfWidth - currentX).coerceIn(minScroll, 0f)
                v.isScrollFinished = v.scrollXOffset <= minScroll
            } else {
                v.scrollXOffset = 0f
            }
        }
    }

    private interface LineRenderDelegate {
        var isGradientEnabled: Boolean
        var isOnlyScrollMode: Boolean
        fun onLayout(width: Int, height: Int, overflow: Boolean)
        fun onHighlightUpdate(highlightWidth: Float)
        fun invalidate()
        fun draw(canvas: Canvas, scrollX: Float)
    }

    private inner class SoftwareRenderer : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f

        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            this.width = width
            this.height = height
            this.overflow = overflow
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            this.highlightWidth = highlightWidth
        }

        override fun invalidate() {}

        override fun draw(canvas: Canvas, scrollX: Float) {
            textRenderer.draw(
                canvas, view.lyricModel, width, height, scrollX, overflow,
                highlightWidth, isGradientEnabled, isOnlyScrollMode,
                backgroundPaint, highlightPaint, view.textPaint
            )
        }
    }

    @RequiresApi(29)
    private inner class HardwareRenderer : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private val renderNode = RenderNode("LyricLine").apply { clipToBounds = false }
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f
        private var isDirty = true

        override fun invalidate() {
            isDirty = true
        }

        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            if (this.width != width || this.height != height || this.overflow != overflow) {
                this.width = width
                this.height = height
                this.overflow = overflow
                renderNode.setPosition(0, 0, width, height)
                isDirty = true
            }
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            if (abs(this.highlightWidth - highlightWidth) > 0.1f) {
                this.highlightWidth = highlightWidth
                isDirty = true
            }
        }

        override fun draw(canvas: Canvas, scrollX: Float) {
            if (isDirty) {
                val recordingCanvas = renderNode.beginRecording(width, height)
                recordingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                textRenderer.draw(
                    recordingCanvas, view.lyricModel, width, height, scrollX, overflow,
                    highlightWidth, isGradientEnabled, isOnlyScrollMode,
                    backgroundPaint, highlightPaint, view.textPaint
                )
                renderNode.endRecording()
                isDirty = false
            }
            canvas.drawRenderNode(renderNode)
        }
    }

    private class LineTextRenderer {
        private val minEdgePosition = 0.9f
        private val fontMetrics = Paint.FontMetrics()
        private var baselineOffset = 0f
        private val shaderMatrix = Matrix()
        private val shaderColors = intArrayOf(0, 0, 0)
        private val shaderPositions = floatArrayOf(0f, minEdgePosition, 1f)
        private var cachedShader: LinearGradient? = null
        private var lastPaintColor = 0
        private var isStandardRatio = true

        fun updateMetrics(paint: TextPaint) {
            paint.getFontMetrics(fontMetrics)
            baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
        }

        fun clearShaderCache() {
            cachedShader = null
        }

        fun draw(
            canvas: Canvas,
            model: LyricModel,
            viewWidth: Int,
            viewHeight: Int,
            scrollX: Float,
            isOverflow: Boolean,
            highlightWidth: Float,
            useGradient: Boolean,
            scrollOnly: Boolean,
            bgPaint: TextPaint,
            hlPaint: TextPaint,
            normPaint: TextPaint
        ) {
            val y = (viewHeight / 2f) + baselineOffset
            canvas.withSave {
                val xOffset = if (isOverflow) scrollX
                else if (model.isAlignedRight) viewWidth - model.width
                else 0f
                translate(xOffset, 0f)

                if (scrollOnly) {
                    canvas.drawText(model.wordText, 0f, y, normPaint)
                } else if (!useGradient) {
                    // 非渐变模式：裁剪绘制
                    canvas.withSave {
                        canvas.clipRect(highlightWidth, 0f, Float.MAX_VALUE, viewHeight.toFloat())
                        canvas.drawText(model.wordText, 0f, y, bgPaint)
                    }
                    if (highlightWidth > 0f) {
                        canvas.withSave {
                            canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())
                            hlPaint.shader = null
                            canvas.drawText(model.wordText, 0f, y, hlPaint)
                        }
                    }
                } else {
                    // 渐变高亮模式
                    canvas.drawText(model.wordText, 0f, y, bgPaint)
                    if (highlightWidth > 0f) {
                        canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())
                        applyHighlightShader(hlPaint, highlightWidth, model.width)
                        canvas.drawText(model.wordText, 0f, y, hlPaint)
                    }
                }
            }
        }

        private fun applyHighlightShader(paint: Paint, highlightWidth: Float, totalWidth: Float) {
            if (totalWidth <= 0f) return
            val ratio = (highlightWidth / totalWidth).coerceIn(0f, 1f)
            val isStd = ratio <= minEdgePosition
            val edgePosition = if (isStd) minEdgePosition else ratio

            val needsNewShader = cachedShader == null ||
                    lastPaintColor != paint.color ||
                    isStandardRatio != isStd ||
                    (!isStd && abs(shaderPositions[1] - edgePosition) > 0.01f)

            if (needsNewShader) {
                shaderColors[0] = paint.color
                shaderColors[1] = paint.color
                shaderPositions[1] = edgePosition
                cachedShader = LinearGradient(
                    0f,
                    0f,
                    1f,
                    0f,
                    shaderColors,
                    shaderPositions,
                    Shader.TileMode.CLAMP
                )
                lastPaintColor = paint.color
                isStandardRatio = isStd
            }

            shaderMatrix.setScale(highlightWidth, 1f)
            cachedShader?.setLocalMatrix(shaderMatrix)
            paint.shader = cachedShader
        }
    }
}