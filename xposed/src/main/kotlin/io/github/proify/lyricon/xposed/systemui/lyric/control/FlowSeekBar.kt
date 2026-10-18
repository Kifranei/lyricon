/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.widget.SeekBar
import io.github.proify.android.extensions.dp
import kotlin.math.max

/**
 * ColorOS 风格流光进度条：圆角轨道，已播放部分带一道沿轨道流动的柔和高光，
 * 拖动时头部出现发光圆点。
 *
 * 继承 [SeekBar] 以保留面板原有的 seek 交互；绘制路径无逐帧分配
 * （Paint / RectF / Matrix / Shader 均预先创建，仅更新 localMatrix）。
 */
internal class FlowSeekBar(context: Context) : SeekBar(context) {

    private val trackHeight = 5.dp.toFloat()
    private val sidePadding = 4.dp.toFloat()
    private val glowRadius = 11.dp.toFloat()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(51, 255, 255, 255) }
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()
    private val matrix = Matrix()

    private var flowShader: LinearGradient? = null
    private var glowShader: RadialGradient? = null
    private var period = 1f
    private var thumbAlpha = 0f
    private var lastFrame = 0L

    private val fraction: Float
        get() = if (max <= 0) 0f else progress.toFloat() / max.toFloat()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val minH = 28.dp
        if (measuredHeight < minH) setMeasuredDimension(measuredWidth, minH)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val trackW = max(1f, w - sidePadding * 2f)
        period = max(trackHeight * 4f, trackW * 0.45f)
        val base = Color.argb(150, 255, 255, 255)
        val bright = Color.argb(255, 255, 255, 255)
        flowShader = LinearGradient(
            0f, 0f, period, 0f,
            intArrayOf(base, base, bright, base, base),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.REPEAT
        )
        playedPaint.shader = flowShader
        glowShader = RadialGradient(
            0f, 0f, glowRadius,
            intArrayOf(Color.argb(190, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = glowShader
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || flowShader == null) return

        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0L else (now - lastFrame).coerceAtMost(64L)
        lastFrame = now
        val target = if (isPressed) 1f else 0f
        thumbAlpha += (target - thumbAlpha) * (dt / 120f).coerceAtMost(1f)

        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val save = canvas.save()
        if (rtl) canvas.scale(-1f, 1f, w / 2f, h / 2f)

        val trackW = max(1f, w - sidePadding * 2f)
        val top = (h - trackHeight) / 2f
        val r = trackHeight / 2f
        rect.set(sidePadding, top, sidePadding + trackW, top + trackHeight)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val playedW = max(trackHeight, trackW * fraction)
        if (fraction > 0f || isPressed) {
            // 高光沿轨道匀速流动（约 2.4s 一个周期）
            val phase = (now % FLOW_PERIOD_MS) / FLOW_PERIOD_MS.toFloat()
            matrix.setTranslate(sidePadding + phase * period, 0f)
            flowShader?.setLocalMatrix(matrix)
            rect.set(sidePadding, top, sidePadding + playedW, top + trackHeight)
            canvas.drawRoundRect(rect, r, r, playedPaint)
        }

        if (thumbAlpha > 0.01f) {
            val cx = sidePadding + playedW - r
            val cy = h / 2f
            glowPaint.alpha = (thumbAlpha * 255).toInt()
            thumbPaint.alpha = (thumbAlpha * 255).toInt()
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, glowRadius, glowPaint)
            canvas.restore()
            canvas.drawCircle(cx, cy, r + 2.dp, thumbPaint)
        }
        canvas.restoreToCount(save)

        // 仅在可见且有进度/动画时继续刷新
        if (isShown && (fraction > 0f || thumbAlpha > 0.01f)) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastFrame = 0L
    }

    private companion object {
        const val FLOW_PERIOD_MS = 2400L
    }
}
