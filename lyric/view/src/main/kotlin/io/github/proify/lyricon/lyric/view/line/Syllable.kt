/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel
import io.github.proify.lyricon.lyric.view.util.Interpolates
import kotlin.math.abs
import kotlin.math.max

/**
 * 歌词行渲染控制器，负责协调动画、滚动与绘制逻辑
 */
class Syllable(private val ownerView: LyricLineView) {

    val inactivePaint: TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    val activePaint: TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    var enableGradient: Boolean = true
        set(value) {
            field = value
            renderer.enableGradient = value
        }

    var onlyScrollMode: Boolean = false
        set(value) {
            field = value
            renderer.onlyScrollMode = value
        }

    var playListener: LyricPlayListener? = null

    private val karaokeAnimator = KaraokeAnimator()
    private val autoScroller = AutoScroller()
    private val renderer = LineRenderer()

    private val cachedLayoutInfo = LineRenderer.LayoutInfo()
    private val cachedPaintContext = LineRenderer.PaintContext(
        ownerView.textPaint, inactivePaint, activePaint
    )

    private var lastPlayPosition: Long = Long.MIN_VALUE

    fun isPlayStarted(): Boolean = karaokeAnimator.hasStarted

    fun isPlaying(): Boolean = karaokeAnimator.isPlaying

    fun isPlayFinished(): Boolean = karaokeAnimator.hasFinished

    /**
     * 重置所有动画状态与滚动偏移
     */
    fun reset() {
        karaokeAnimator.reset()
        autoScroller.reset(ownerView)
        lastPlayPosition = Long.MIN_VALUE
    }

    /**
     * 立即跳转至指定播放位置，无平滑过渡
     */
    fun seek(position: Long) {
        val model = ownerView.lyricModel
        val currentWord = model.wordTimingNavigator.first(position)
        val targetWidth = calculateTargetWidth(position, model, currentWord)

        karaokeAnimator.jumpTo(targetWidth)
        autoScroller.update(targetWidth, ownerView, smooth = false)

        lastPlayPosition = position
        dispatchProgressEvents()
    }

    /**
     * 根据当前播放进度更新高亮目标值
     */
    fun updateProgress(position: Long) {
        if (lastPlayPosition != Long.MIN_VALUE && position < lastPlayPosition) {
            seek(position)
            return
        }

        val model = ownerView.lyricModel
        val currentWord = model.wordTimingNavigator.first(position)
        val targetWidth = calculateTargetWidth(position, model, currentWord)

        if (currentWord != null && karaokeAnimator.currentWidth == 0f) {
            currentWord.previous?.let { karaokeAnimator.jumpTo(it.endPosition) }
        }

        if (targetWidth != karaokeAnimator.targetWidth) {
            karaokeAnimator.animateTo(targetWidth, currentWord?.duration ?: 0)
        }

        lastPlayPosition = position
    }

    /**
     * 驱动每一帧的动画计算
     * @return 如果状态发生改变需要重绘则返回 true
     */
    fun updateFrame(frameTimeNanos: Long): Boolean {
        val isHighlightChanged = karaokeAnimator.update(frameTimeNanos)

        if (isHighlightChanged) {
            autoScroller.update(karaokeAnimator.currentWidth, ownerView, smooth = true)
            dispatchProgressEvents()
        }

        return isHighlightChanged
    }

    /**
     * 执行 Canvas 绘制指令
     */
    fun draw(canvas: Canvas) {
        cachedLayoutInfo.update(
            viewWidth = ownerView.measuredWidth,
            viewHeight = ownerView.measuredHeight,
            scrollX = ownerView.scrollXOffset,
            isOverflow = ownerView.isOverflow()
        )

        renderer.draw(
            canvas = canvas,
            model = ownerView.lyricModel,
            layoutInfo = cachedLayoutInfo,
            paints = cachedPaintContext,
            highlightWidth = karaokeAnimator.currentWidth
        )
    }

    private fun calculateTargetWidth(
        position: Long,
        model: LyricModel,
        currentWord: WordModel?
    ): Float {
        return when {
            currentWord != null -> currentWord.endPosition
            position >= model.end -> ownerView.lyricWidth
            position <= model.begin -> 0f
            else -> karaokeAnimator.currentWidth
        }
    }

    private fun dispatchProgressEvents() {
        val current = karaokeAnimator.currentWidth
        val total = ownerView.lyricWidth

        if (!karaokeAnimator.hasStarted && current > 0f) {
            karaokeAnimator.hasStarted = true
            playListener?.onPlayStarted(ownerView)
        }
        if (!karaokeAnimator.hasFinished && current >= total) {
            karaokeAnimator.hasFinished = true
            playListener?.onPlayEnded(ownerView)
        }
        playListener?.onPlayProgress(ownerView, total, current)
    }

    /**
     * 处理卡拉OK高亮宽度的数值平滑过渡
     */
    private class KaraokeAnimator {
        private val interpolator = Interpolates.linear
        var currentWidth = 0f; private set
        var targetWidth = 0f; private set
        var hasStarted = false
        var hasFinished = false
        val isPlaying get() = hasStarted && !hasFinished

        private var startWidth = 0f
        private var startTimeNanos = 0L
        private var durationNanos = 0L
        private var isAnimating = false

        fun reset() {
            currentWidth = 0f; targetWidth = 0f; startWidth = 0f
            isAnimating = false; hasStarted = false; hasFinished = false
        }

        fun jumpTo(width: Float) {
            currentWidth = width; targetWidth = width; startWidth = width
            isAnimating = false
        }

        fun animateTo(target: Float, durationMs: Long) {
            startWidth = currentWidth
            targetWidth = target
            startTimeNanos = System.nanoTime()
            durationNanos = max(1L, durationMs) * 1_000_000L
            isAnimating = true
        }

        fun update(now: Long): Boolean {
            if (!isAnimating) return false
            val elapsed = (now - startTimeNanos).coerceAtLeast(0L)
            if (elapsed >= durationNanos) {
                currentWidth = targetWidth
                isAnimating = false
                return true
            }
            val progress = elapsed.toFloat() / durationNanos
            currentWidth =
                startWidth + (targetWidth - startWidth) * interpolator.getInterpolation(progress)
            return true
        }
    }

    /**
     * 处理长歌词的水平滚动计算
     */
    private class AutoScroller {
        fun reset(view: LyricLineView) {
            view.scrollXOffset = 0f; view.isScrollFinished = false
        }

        @Suppress("unused")
        fun update(currentX: Float, view: LyricLineView, smooth: Boolean) {
            if (!view.isOverflow()) {
                view.scrollXOffset = 0f
                return
            }

            val halfWidth = view.measuredWidth / 2f
            if (currentX > halfWidth) {
                val minScroll = -view.lyricWidth + view.measuredWidth
                view.scrollXOffset = max(halfWidth - currentX, minScroll)
                view.isScrollFinished = view.scrollXOffset <= minScroll
            } else {
                view.scrollXOffset = 0f
            }
        }
    }

    /**
     * 歌词文本渲染实现类
     */
    private class LineRenderer {

        class LayoutInfo {
            var viewWidth = 0
            var viewHeight = 0
            var scrollX = 0f
            var isOverflow = false

            fun update(viewWidth: Int, viewHeight: Int, scrollX: Float, isOverflow: Boolean) {
                this.viewWidth = viewWidth
                this.viewHeight = viewHeight
                this.scrollX = scrollX
                this.isOverflow = isOverflow
            }
        }

        data class PaintContext(
            val normal: TextPaint,
            val inactive: TextPaint,
            val active: TextPaint
        )

        private val gradColors = intArrayOf(0, 0, Color.TRANSPARENT)
        private val gradPositions = floatArrayOf(0f, 0.86f, 1f)
        private var cachedShader: LinearGradient? = null

        // 用于检测是否需要刷新 Shader
        private var lastHighlightWidth = -1f
        private var lastColor = 0
        private var lastTextWidth = -1f

        var enableGradient = true
        var onlyScrollMode: Boolean = false

        fun draw(
            canvas: Canvas,
            model: LyricModel,
            layoutInfo: LayoutInfo,
            paints: PaintContext,
            highlightWidth: Float
        ) {
            val baseline = calculateBaseline(layoutInfo.viewHeight, paints.inactive)

            canvas.withSave {
                val tx = when {
                    layoutInfo.isOverflow -> layoutInfo.scrollX
                    model.isAlignedRight -> -model.width + layoutInfo.viewWidth
                    else -> 0f
                }
                translate(tx, 0f)

                if (!onlyScrollMode) {
                    // 1. 绘制底色文字
                    canvas.drawText(model.wordText, 0f, baseline, paints.inactive)

                    // 2. 绘制高亮文字
                    if (highlightWidth > 0f) {
                        canvas.clipRect(0f, 0f, highlightWidth, layoutInfo.viewHeight.toFloat())

                        if (enableGradient) {
                            applyEnhancedGradient(paints.active, highlightWidth, model.width)
                        } else {
                            paints.active.shader = null
                        }

                        canvas.drawText(model.wordText, 0f, baseline, paints.active)
                    }
                } else {
                    canvas.drawText(model.wordText, 0f, baseline, paints.normal)
                }
            }
        }

        private fun calculateBaseline(h: Int, paint: Paint): Float {
            val fm = paint.fontMetrics
            return (h - (fm.descent - fm.ascent)) / 2f - fm.ascent
        }

        private fun applyEnhancedGradient(paint: Paint, highlightWidth: Float, textWidth: Float) {
            if (textWidth <= 0f) {
                paint.shader = null
                return
            }

            val color = paint.color
            val ratio = (highlightWidth / textWidth).coerceIn(0f, 1f)
            val posEdge = ratio.coerceAtLeast(0.86f)

            val needUpdate = cachedShader == null ||
                    lastColor != color ||
                    lastTextWidth != textWidth ||
                    abs(lastHighlightWidth - highlightWidth) > 0.5f

            if (needUpdate) {
                gradColors[0] = color
                gradColors[1] = color
                gradPositions[1] = posEdge

                lastColor = color
                lastTextWidth = textWidth
                lastHighlightWidth = highlightWidth

                cachedShader = LinearGradient(
                    0f, 0f, highlightWidth, 0f,
                    gradColors,
                    gradPositions,
                    Shader.TileMode.CLAMP
                )
            }

            paint.shader = cachedShader
        }
    }
}