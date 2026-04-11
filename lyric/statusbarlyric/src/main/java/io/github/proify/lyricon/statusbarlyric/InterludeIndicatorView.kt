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
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.sp
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.view.visibleIfChanged
import java.io.File
import kotlin.math.max
import kotlin.math.min

class InterludeIndicatorView(context: Context) : View(context) {

    companion object {
        private const val MAX_FONT_WEIGHT = 1000
        private const val PROGRESS_TICK_MS = 48L
        private const val DOT_COUNT = 3
        private const val DOT_SIZE_DP = 8
        private const val DOT_SPACING_DP = 5
        private const val DOT_VERTICAL_PADDING_DP = 2

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
            requestLayout()
            val finished = durationMs > 0L && SystemClock.uptimeMillis() - startedAtMs >= durationMs
            if (finished) return
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
        if (isAnimating) return true

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
                paddingTop + paddingBottom + DOT_SIZE_DP.dp + DOT_VERTICAL_PADDING_DP.dp * 2
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
        val centerY = height / 2f
        val radius = DOT_SIZE_DP.dp / 2f
        val startX = paddingLeft + radius
        val spacing = DOT_SIZE_DP.dp + DOT_SPACING_DP.dp
        val progress = if (durationMs <= 0L) {
            0f
        } else {
            ((SystemClock.uptimeMillis() - startedAtMs).toFloat() / durationMs.toFloat())
                .coerceIn(0f, 1f)
        }

        repeat(DOT_COUNT) { index ->
            dotPaint.color = baseColor.withAlpha(dotAlphaForIndex(index, progress))
            val cx = startX + index * spacing
            canvas.drawCircle(cx, centerY, radius, dotPaint)
        }
    }

    private fun dotAlphaForIndex(index: Int, progress: Float): Float {
        val bright = 0.92f
        val dim = 0.18f
        val phase = progress * DOT_COUNT
        return when (index) {
            0 -> bright
            1 -> when {
                phase <= 1f -> bright
                phase >= 2f -> dim
                else -> lerp(bright, dim, phase - 1f)
            }

            2 -> when {
                phase <= 0f -> bright
                phase >= 1f -> dim
                else -> lerp(bright, dim, phase)
            }

            else -> dim
        }
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

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        return start + (end - start) * clamped
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
