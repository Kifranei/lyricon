/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package io.github.proify.lyricon.lyric.view.line

import android.content.Context
import android.graphics.Canvas
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.view.LyricLineConfig
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.createModel
import io.github.proify.lyricon.lyric.view.line.model.emptyLyricModel
import io.github.proify.lyricon.lyric.view.util.dp
import io.github.proify.lyricon.lyric.view.util.sp
import java.lang.ref.WeakReference

class LyricLineView(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    init {
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(15.dp)
    }

    val textPaint: TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f.sp
    }

    var lyricModel: LyricModel = emptyLyricModel()
        private set

    var scrollXOffset: Float = 0f

    var isScrollFinished: Boolean = false
    val marquee: Marquee = Marquee(WeakReference(this))
    val syllable: Syllable = Syllable(this)
    private val animationDriver = AnimationDriver()

    val lyricWidth: Float get() = lyricModel.width

    fun reset() {
        animationDriver.stop()
        marquee.reset()
        syllable.reset()
        scrollXOffset = 0f
        isScrollFinished = false
        lyricModel = emptyLyricModel()
        refreshModelSizes()
        invalidate()
    }

    val textSize: Float get() = textPaint.textSize

    fun setTextSize(size: Float) {
        val needUpdate = textPaint.textSize != size
                || syllable.inactivePaint.textSize != size
                || syllable.activePaint.textSize != size

        if (!needUpdate) return

        textPaint.textSize = size

        syllable.inactivePaint.textSize = size
        syllable.activePaint.textSize = size
        refreshModelSizes()
        invalidate()
    }

    fun updateColor(
        textColor: Int,
        backgroundColor: Int,
        highlightColor: Int
    ) {
        textPaint.apply {
            color = textColor
        }
        syllable.inactivePaint.apply {
            color = backgroundColor
        }
        syllable.activePaint.apply {
            color = highlightColor
        }
        invalidate()
    }

    fun setStyle(configs: LyricLineConfig) {
        val textConfig = configs.text
        val marqueeConfig = configs.marquee
        val syllableConfig = configs.syllable

        textPaint.apply {
            textSize = textConfig.textSize
            typeface = textConfig.typeface
            color = textConfig.textColor
        }

        syllable.inactivePaint.apply {
            textSize = textConfig.textSize
            typeface = textConfig.typeface
            color = syllableConfig.backgroundColor
        }
        syllable.activePaint.apply {
            textSize = textConfig.textSize
            typeface = textConfig.typeface
            color = syllableConfig.highlightColor
        }
        syllable.enableGradient = configs.gradientProgressStyle

        marquee.apply {
            ghostSpacing = marqueeConfig.ghostSpacing
            scrollSpeed = marqueeConfig.scrollSpeed
            initialDelayMs = marqueeConfig.initialDelay
            loopDelayMs = marqueeConfig.loopDelay
            repeatCount = marqueeConfig.repeatCount
            stopAtEnd = marqueeConfig.stopAtEnd
        }
        refreshModelSizes()

        invalidate()
    }

    fun isSyllableMode(): Boolean = lyricModel.isPlainText.not()

    fun seekTo(position: Long) {
        if (isSyllableMode()) {
            syllable.seek(position)
            animationDriver.startIfNoRuning()
        }
    }

    fun setPosition(position: Long) {
        if (isSyllableMode()) {
            syllable.updateProgress(position)
            animationDriver.startIfNoRuning()
        }
    }

    fun refreshModelSizes() {
        lyricModel.updateSizes(textPaint)
    }

    override fun getLeftFadingEdgeStrength(): Float {
        // 基础检查：文本未溢出或未开启渐隐
        if (lyricWidth <= width || horizontalFadingEdgeLength <= 0) return 0f

        val offsetInUnit = if (isMarqueeMode()) {
            marquee.currentUnitOffset
        } else {
            -scrollXOffset
        }

        // 1. 如果还在起始延迟或位移为0，无左渐隐
        if (offsetInUnit <= 0f) return 0f

        // 2. 行业级处理：处理间距区域（Space Area）
        // 如果当前位移超过了歌词文本宽度，说明左边缘现在处于空白间距中，不应有渐变
        if (isMarqueeMode() && offsetInUnit > lyricWidth) {
            return 0f
        }

        // 3. 计算渐入：位移从 0 到 fadingEdgeLength 过程中线性增加强度
        val edgeL = horizontalFadingEdgeLength.toFloat()
        return (offsetInUnit / edgeL).coerceIn(0f, 1f)
    }

    override fun getRightFadingEdgeStrength(): Float {
        // 基础检查
        if (lyricWidth <= width || horizontalFadingEdgeLength <= 0) return 0f

        val viewW = width.toFloat()
        val edgeL = horizontalFadingEdgeLength.toFloat()

        if (isMarqueeMode()) {
            // 如果处于结束停止状态（stopAtEnd），根据剩余内容长度计算
            if (isScrollFinished) {
                val remaining = lyricWidth + scrollXOffset - viewW
                return (remaining / edgeL).coerceIn(0f, 1f)
            }

            // --- 核心逻辑：判断空白区 ---
            val offsetInUnit = marquee.currentUnitOffset

            // 主体文本的右边缘在屏幕上的坐标
            val primaryRightEdge = lyricWidth - offsetInUnit
            // 鬼影文本的左边缘在屏幕上的坐标
            val ghostLeftEdge = primaryRightEdge + marquee.ghostSpacing

            // 如果【主体右边缘】已经滑过屏幕右侧（进入屏幕），
            // 且【鬼影左边缘】还没有到达屏幕右侧，说明此时右边缘是空白
            return if (primaryRightEdge < viewW && ghostLeftEdge > viewW) {
                0f
            } else {
                // 内容还在持续（主体还没走完，或者鬼影已经进场）
                1.0f
            }
        }

        // 非跑马灯模式（逐字模式）：常规计算
        val remaining = lyricWidth + scrollXOffset - viewW
        return (remaining / edgeL).coerceIn(0f, 1f)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val textHeight = (textPaint.descent() - textPaint.ascent()).toInt()
        setMeasuredDimension(w, resolveSize(textHeight, hSpec))
    }

    fun setLyric(line: LyricLine?) {
        reset()
        lyricModel = line?.normalize()?.createModel() ?: emptyLyricModel()

        refreshModelSizes()
        requestLayout()
        invalidate()
    }

    fun startMarquee() {
        if (isMarqueeMode()) {
            scrollXOffset = 0f
            marquee.start()
            animationDriver.startIfNoRuning()
        }
    }

    @Suppress("unused")
    fun pauseMarquee() {
        if (isMarqueeMode()) {
            marquee.pause()
        }
    }

    fun isMarqueeMode(): Boolean = lyricModel.isPlainText
    fun isOverflow(): Boolean = lyricWidth > width

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isMarqueeMode()) {
            marquee.draw(canvas)
        } else {
            syllable.draw(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    fun isPlayStarted(): Boolean = if (isMarqueeMode()) true else syllable.isPlayStarted()
    fun isPlaying(): Boolean = if (isMarqueeMode()) true else syllable.isPlaying()
    fun isPlayFinished(): Boolean = if (isMarqueeMode()) false else syllable.isPlayFinished()

    /**
     * ------------------------
     *  统一动画驱动器（Choreographer）
     * ------------------------
     */
    internal inner class AnimationDriver : Choreographer.FrameCallback {
        private var running = false
        private var lastFrameNanos = 0L

        fun startIfNoRuning() {
            if (!running) {
                running = true
                lastFrameNanos = System.nanoTime()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        fun stop() {
            if (!running) return
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            val deltaNanos = if (lastFrameNanos == 0L) 0L else (frameTimeNanos - lastFrameNanos)
            lastFrameNanos = frameTimeNanos

            var needInvalidate: Boolean

            if (isMarqueeMode()) {
                // 驱动 Marquee（被动 step，保留完整行为）
                marquee.step(deltaNanos)
                needInvalidate = true
            } else {
                // 非跑马灯：推进高亮 & 字偏移
                val changed = syllable.updateFrame(frameTimeNanos)
                needInvalidate = changed
            }

            if (needInvalidate) postInvalidateOnAnimation()

            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }
}