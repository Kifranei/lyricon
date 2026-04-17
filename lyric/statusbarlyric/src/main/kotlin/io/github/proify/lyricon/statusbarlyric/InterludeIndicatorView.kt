/*
* Copyright 2026 Proify, Tomakino
* Licensed under the Apache License, Version 2.0
* http://www.apache.org/licenses/LICENSE-2.0
*/

package io.github.proify.lyricon.statusbarlyric

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.view.visibleIfChanged
import kotlin.math.PI
import kotlin.math.sin

/**
 * 间奏等待指示器 — 三个圆点
 *
 * 动画逻辑：
 * - 三个点只做一次轻微上浮入场，随后轻微呼吸缩放
 * - 临近下一句歌词时三个点按 3 -> 2 -> 1 逐个淡出
 *
 * 倒计时消失规则：
 *   - 剩余时间进入尾段时，三个点按 3 -> 2 -> 1 逐个淡出
 */
class InterludeIndicatorView(context: Context) : View(context) {

    companion object {
        private const val PROGRESS_TICK_MS = 16L
        private const val DOT_COUNT = 3
        private const val DOT_SIZE_DP = 10
        private const val DOT_SPACING_DP = 6
        private const val DOT_FLOAT_HEIGHT_DP = 2.2f
        private const val DOT_BASE_OFFSET_Y_DP = 4f
        private const val DOT_ENTRY_DURATION_MS = 220L
        private const val DOT_ENTRY_FADE_MS = 180L
        private const val DOT_FADE_DURATION_MS = 1200L
        private const val DOT_BREATHE_PERIOD_MS = 1500L
        private const val DOT_BREATHE_SCALE_AMPLITUDE = 0.07f

        private val RAINBOW_COLORS = intArrayOf(
            Color.parseColor("#FF4D4F"),
            Color.parseColor("#FF9F43"),
            Color.parseColor("#FFD93D"),
            Color.parseColor("#2ED573"),
            Color.parseColor("#1E90FF"),
            Color.parseColor("#5352ED"),
            Color.parseColor("#A55EEA")
        )
    }

    private var currentStyle: LyricStyle? = null
    private var currentStatusColor = StatusColor()
    private var indicatorStyle = TextStyle.InterludeIndicatorStyle.DOTS
    private var isAnimating = false
    private var startedAtMs = 0L
    private var durationMs = 0L

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val frameRunner = object : Runnable {
        override fun run() {
            if (!isAnimating) return
            invalidate()
            val finished = durationMs > 0L && SystemClock.uptimeMillis() - startedAtMs >= durationMs
            if (finished) {
                // 间奏结束后不再继续动画帧
                return
            }
            postDelayed(this, PROGRESS_TICK_MS)
        }
    }

    init {
        updatePadding(left = 2.dp, right = 2.dp)
        visibleIfChanged = false
    }

    fun applyStyle(style: LyricStyle) {
        currentStyle = style
        indicatorStyle = style.packageStyle.text.interludeIndicatorStyle
        refreshPaints()

        if (indicatorStyle == TextStyle.InterludeIndicatorStyle.NONE) {
            hide()
        } else if (isAnimating) {
            restartAnimation()
        } else {
            requestLayout()
            invalidate()
        }
    }

    fun setStatusBarColor(color: StatusColor) {
        currentStatusColor = color
        refreshPaints()
        invalidate()
    }

    fun show(duration: Long): Boolean {
        if (indicatorStyle == TextStyle.InterludeIndicatorStyle.NONE) return false

        if (isAnimating) {
            durationMs = duration.coerceAtLeast(0L)
            startedAtMs = SystemClock.uptimeMillis()
            return true
        }

        isAnimating = true
        durationMs = duration.coerceAtLeast(0L)
        startedAtMs = SystemClock.uptimeMillis()
        restartAnimation()
        visibleIfChanged = true
        return true
    }

    fun hide() {
        if (!isAnimating && visibility != VISIBLE) return
        isAnimating = false
        removeCallbacks(frameRunner)
        visibleIfChanged = false
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (indicatorStyle) {
            TextStyle.InterludeIndicatorStyle.DOTS -> {
                paddingLeft + paddingRight + DOT_COUNT * DOT_SIZE_DP.dp + (DOT_COUNT - 1) * DOT_SPACING_DP.dp
            }
            else -> 0
        }

        val height = when (indicatorStyle) {
            TextStyle.InterludeIndicatorStyle.DOTS -> {
                val floatHeightExtra = DOT_FLOAT_HEIGHT_DP.dp.toInt()
                val baseOffsetExtra = DOT_BASE_OFFSET_Y_DP.dp.toInt()
                paddingTop + paddingBottom + DOT_SIZE_DP.dp + floatHeightExtra + baseOffsetExtra
            }
            else -> 0
        }

        setMeasuredDimension(
            resolveSize(width.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(height.coerceAtLeast(suggestedMinimumHeight), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (indicatorStyle) {
            TextStyle.InterludeIndicatorStyle.DOTS -> drawDots(canvas)
        }
    }

    private fun restartAnimation() {
        removeCallbacks(frameRunner)
        frameRunner.run()
    }

    private fun drawDots(canvas: Canvas) {
        val baseColor = resolveColor()
        val centerY = height / 2f + DOT_BASE_OFFSET_Y_DP.dp / 2f
        val radius = DOT_SIZE_DP.dp / 2f
        val startX = paddingLeft + radius
        val spacing = DOT_SIZE_DP.dp + DOT_SPACING_DP.dp

        val elapsed = SystemClock.uptimeMillis() - startedAtMs
        val remainingMs = (durationMs - elapsed).coerceAtLeast(0L)
        val floatHeight = DOT_FLOAT_HEIGHT_DP.dp.toFloat()
        val breatheScale = resolveBreathScale(elapsed, remainingMs)

        repeat(DOT_COUNT) { index ->
            val offsetY = calculateEntryOffset(elapsed, floatHeight)
            val alphaRatio = resolveDotAlpha(index, elapsed, remainingMs)
            if (alphaRatio <= 0f) return@repeat
            dotPaint.color = baseColor.withAlpha(alphaRatio)
            val cx = startX + index * spacing
            canvas.drawCircle(cx, centerY + offsetY, radius * breatheScale, dotPaint)
        }
    }

    private fun calculateEntryOffset(elapsedMs: Long, floatHeight: Float): Float {
        val progress = (elapsedMs.toFloat() / DOT_ENTRY_DURATION_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        return -floatHeight * eased
    }

    private fun resolveDotAlpha(index: Int, elapsedMs: Long, remainingMs: Long): Float {
        val entryAlpha = (elapsedMs.toFloat() / DOT_ENTRY_FADE_MS).coerceIn(0f, 1f)
        if (remainingMs > DOT_FADE_DURATION_MS) return entryAlpha
        val segmentDuration = DOT_FADE_DURATION_MS.toFloat() / DOT_COUNT
        val fadeElapsed = (DOT_FADE_DURATION_MS - remainingMs).coerceAtLeast(0L).toFloat()
        val reverseIndex = DOT_COUNT - 1 - index
        val segmentStart = reverseIndex * segmentDuration
        val localProgress = ((fadeElapsed - segmentStart) / segmentDuration).coerceIn(0f, 1f)
        return entryAlpha * (1f - localProgress)
    }

    private fun resolveBreathScale(elapsedMs: Long, remainingMs: Long): Float {
        if (remainingMs <= DOT_FADE_DURATION_MS) return 1f
        val progress = ((elapsedMs - DOT_ENTRY_DURATION_MS).coerceAtLeast(0L) % DOT_BREATHE_PERIOD_MS).toFloat() / DOT_BREATHE_PERIOD_MS
        val wave = ((sin(progress * 2f * PI - PI / 2f) + 1f) / 2f).toFloat()
        return 1f + DOT_BREATHE_SCALE_AMPLITUDE * wave
    }

    private fun refreshPaints() {
        currentStyle?.packageStyle?.text ?: return
    }

    private fun resolveColor(): Int {
        val textStyle = currentStyle?.packageStyle?.text
        if (textStyle?.enableRainbowTextColor == true) {
            return RAINBOW_COLORS[1]
        }
        val customColor = textStyle?.color(currentStatusColor.isLightMode)
        return if (textStyle?.enableCustomTextColor == true && customColor?.normal?.isNotEmpty() == true) {
            customColor.normal.first()
        } else {
            currentStatusColor.color.firstOrNull() ?: currentStatusColor.firstColor()
        }
    }

    private fun Int.withAlpha(ratio: Float): Int {
        val alpha = (ratio.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameRunner)
        super.onDetachedFromWindow()
    }
}
