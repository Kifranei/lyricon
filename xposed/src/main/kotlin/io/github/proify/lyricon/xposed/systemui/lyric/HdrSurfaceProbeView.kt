/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.view.isVisible
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.HdrStatusBarController

@SuppressLint("ViewConstructor")
class HdrSurfaceProbeView(
    context: Context,
    private val source: String = DEFAULT_SOURCE
) : SurfaceView(context), SurfaceHolder.Callback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var enabled = false
    private var ratio = MIN_RATIO
    private var frameCount = 0L
    private var frameCallbackScheduled = false
    private var lastSurfaceControl: Any? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!enabled || !isAttachedToWindow || !isVisible) {
                frameCallbackScheduled = false
                return
            }

            frameCount++
            val shouldLog = frameCount == 1L || frameCount % 300L == 0L
            drawProbe(shouldLog)
            val surfaceControl = getSurfaceControlCompat(shouldLog)
            lastSurfaceControl = surfaceControl
            HdrStatusBarController.applyHdrToProbeSurface(
                surface = surfaceControl,
                ratio = ratio,
                source = source,
                shouldLog = shouldLog
            )

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        tag = VIEW_TAG
        setZOrderMediaOverlay(true)
        val requestedFormat = preferredPixelFormat()
        holder.setFormat(requestedFormat)
        holder.addCallback(this)
        YLog.info(TAG, "Probe Surface requested format=${formatName(requestedFormat)}")
    }

    fun setProbeEnabled(enabled: Boolean, ratio: Float) {
        val normalizedRatio = ratio.normalizedRatio()
        val changed = this.enabled != enabled || this.ratio != normalizedRatio
        this.enabled = enabled
        this.ratio = normalizedRatio
        isVisible = enabled
        if (!enabled) {
            stopPulse(restore = true)
            return
        }
        if (changed) {
            frameCount = 0L
        }
        startPulse()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        YLog.info(TAG, "Probe Surface created")
        if (enabled) {
            frameCount = 0L
            startPulse()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        YLog.info(TAG, "Probe Surface changed: format=${formatName(format)} width=$width height=$height")
        if (enabled) {
            frameCount = 0L
            drawProbe(shouldLog = true)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        YLog.info(TAG, "Probe Surface destroyed")
        stopPulse(restore = true)
    }

    override fun onDetachedFromWindow() {
        stopPulse(restore = true)
        super.onDetachedFromWindow()
    }

    private fun startPulse() {
        if (frameCallbackScheduled) return
        frameCallbackScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
        YLog.info(TAG, "Probe Surface HDR pulse started")
    }

    private fun stopPulse(restore: Boolean) {
        if (frameCallbackScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameCallbackScheduled = false
            YLog.info(TAG, "Probe Surface HDR pulse stopped")
        }
        if (restore) {
            restoreLastSurface()
        }
    }

    private fun restoreLastSurface() {
        val surfaceControl = lastSurfaceControl ?: return
        HdrStatusBarController.restoreProbeSurfaceSdr(surfaceControl, source)
        lastSurfaceControl = null
    }

    private fun drawProbe(shouldLog: Boolean) {
        val canvas = lockProbeCanvas(shouldLog) ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawBlocks(canvas)
        } finally {
            runCatching { holder.unlockCanvasAndPost(canvas) }
                .onFailure { if (shouldLog) YLog.error(TAG, "unlockCanvasAndPost failed", it) }
        }
    }

    private fun lockProbeCanvas(shouldLog: Boolean): Canvas? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas()
            } else {
                holder.lockCanvas()
            }
        }.onFailure {
            if (shouldLog) YLog.error(TAG, "lockCanvas failed", it)
        }.getOrNull()
    }

    private fun drawBlocks(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val gap = (w * 0.12f).coerceAtLeast(1f)
        val blockWidth = ((w - gap) / 2f).coerceAtLeast(1f)
        val top = h * 0.22f
        val bottom = h * 0.78f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(96, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = Color.rgb(235, 235, 235)
        canvas.drawRect(0f, top, blockWidth, bottom, paint)

        setHdrPaintColor()
        canvas.drawRect(blockWidth + gap, top, w, bottom, paint)
    }

    @SuppressLint("NewApi")
    private fun setHdrPaintColor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ratio <= MIN_RATIO) {
            paint.color = Color.WHITE
            return
        }
        paint.style = Paint.Style.FILL
        runCatching {
            paint.setColor(
                Color.pack(
                    ratio,
                    ratio,
                    ratio,
                    1.0f,
                    ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)
                )
            )
        }.onFailure {
            paint.color = Color.WHITE
        }
    }

    private fun getSurfaceControlCompat(shouldLog: Boolean): Any? {
        return runCatching {
            val method = SurfaceView::class.java.getDeclaredMethod("getSurfaceControl")
            method.isAccessible = true
            method.invoke(this)
        }.onFailure {
            if (shouldLog) YLog.error(TAG, "getSurfaceControl failed", it)
        }.getOrNull()
    }

    private fun Float.normalizedRatio(): Float =
        if (isFinite() && this > MIN_RATIO) coerceAtMost(MAX_RATIO) else MIN_RATIO

    private fun preferredPixelFormat(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PIXEL_FORMAT_RGBA_FP16
        } else {
            PixelFormat.TRANSLUCENT
        }

    private fun formatName(format: Int): String =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && format == PIXEL_FORMAT_RGBA_FP16 -> {
                "RGBA_FP16($format)"
            }
            format == PixelFormat.TRANSLUCENT -> "TRANSLUCENT($format)"
            format == PixelFormat.RGBA_8888 -> "RGBA_8888($format)"
            format == PixelFormat.RGBX_8888 -> "RGBX_8888($format)"
            format == PixelFormat.RGB_888 -> "RGB_888($format)"
            else -> format.toString()
        }

    companion object {
        private const val TAG = "HdrSurfaceProbeView"
        private const val DEFAULT_SOURCE = "independent-surface-probe"
        private const val VIEW_TAG = "lyricon:hdr_surface_probe"
        private const val MIN_RATIO = 1.0f
        private const val MAX_RATIO = 8.0f
        private const val PIXEL_FORMAT_RGBA_FP16 = 22
    }
}
