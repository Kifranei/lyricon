/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.os.Build
import android.view.View

class HdrProbeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var ratio: Float = MIN_RATIO

    fun updateRatio(value: Float) {
        val normalized = value.normalizedRatio()
        if (ratio == normalized) return
        ratio = normalized
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = (width * 0.12f).coerceAtLeast(1f)
        val blockWidth = ((width - gap) / 2f).coerceAtLeast(1f)
        val top = height * 0.22f
        val bottom = height * 0.78f

        paint.color = Color.WHITE
        canvas.drawRect(0f, top, blockWidth, bottom, paint)

        setHdrPaintColor()
        canvas.drawRect(blockWidth + gap, top, width.toFloat(), bottom, paint)
    }

    @SuppressLint("NewApi")
    private fun setHdrPaintColor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ratio <= MIN_RATIO) {
            paint.color = Color.WHITE
            return
        }
        runCatching {
            paint.setColor(
                Color.pack(
                    ratio,
                    ratio,
                    ratio,
                    1.0f,
                    ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)
                )
            )
        }.onFailure {
            paint.color = Color.WHITE
        }
    }

    private fun Float.normalizedRatio(): Float =
        if (isFinite() && this > MIN_RATIO) coerceAtMost(MAX_RATIO) else MIN_RATIO

    companion object {
        private const val MIN_RATIO = 1.0f
        private const val MAX_RATIO = 8.0f
    }
}
