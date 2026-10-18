/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.widget.SeekBar
import io.github.proify.android.extensions.dp
import kotlin.math.max

/**
 * ColorOS 风格进度条：细圆角轨道，未播放部分为暗色，已播放部分为淡薄荷白，
 * 播放头处带一团向左拖尾、柔和模糊的彗星状光晕（无拖动圆点）。
 *
 * 继承 [SeekBar] 以保留面板原有的 seek 交互。静态绘制：Paint / Shader / RectF 预先创建，
 * 绘制期无分配、无持续动画，仅在进度变化时重绘。
 */
internal class FlowSeekBar(context: Context) : SeekBar(context) {

    private val trackHeight = 3.dp.toFloat()
    private val sidePadding = 2.dp.toFloat()
    private val glowRadius = 14.dp.toFloat()
    private val glowStretch = 3.2f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 255, 255, 255)
    }
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 232, 213)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        glowPaint.shader = RadialGradient(
            0f, 0f, glowRadius,
            intArrayOf(
                Color.argb(235, 246, 255, 249),
                Color.argb(120, 214, 240, 222),
                Color.argb(0, 205, 232, 213)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        corePaint.shader = RadialGradient(
            0f, 0f, trackHeight * 3f,
            intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val fraction: Float
        get() = if (max <= 0) 0f else progress.toFloat() / max.toFloat()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val minH = 28.dp
        if (measuredHeight < minH) setMeasuredDimension(measuredWidth, minH)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val save = canvas.save()
        if (layoutDirection == LAYOUT_DIRECTION_RTL) canvas.scale(-1f, 1f, w / 2f, h / 2f)

        val trackW = max(1f, w - sidePadding * 2f)
        val cy = h / 2f
        val top = cy - trackHeight / 2f
        val r = trackHeight / 2f

        rect.set(sidePadding, top, sidePadding + trackW, top + trackHeight)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val playedW = max(trackHeight, trackW * fraction)
        val headX = sidePadding + playedW
        rect.set(sidePadding, top, headX, top + trackHeight)
        canvas.drawRoundRect(rect, r, r, playedPaint)

        if (fraction > 0f) {
            // 彗星光晕：横向拉伸的径向渐变，裁掉播放头右侧，只向左拖尾
            canvas.save()
            canvas.clipRect(sidePadding, 0f, headX + r, h)
            canvas.translate(headX, cy)
            canvas.save()
            canvas.scale(glowStretch, 1f)
            canvas.drawCircle(0f, 0f, glowRadius, glowPaint)
            canvas.restore()
            canvas.drawCircle(0f, 0f, trackHeight * 3f, corePaint)
            canvas.restore()
        }
        canvas.restoreToCount(save)
    }
}
