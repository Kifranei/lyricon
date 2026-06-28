/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.HdrStatusBarController

class HdrOverlayProbeController(private val context: Context) {
    private val windowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private val probeView by lazy {
        HdrSurfaceProbeView(context, SOURCE).apply {
            visibility = View.GONE
        }
    }

    private var added = false
    private var activeType: Int? = null
    private var lastRatio = MIN_RATIO

    fun show(anchor: View, ratio: Float) {
        val normalizedRatio = ratio.normalizedRatio()
        lastRatio = normalizedRatio
        HdrStatusBarController.enableOverlayWindowProbe(normalizedRatio)

        if (!anchor.isAttachedToWindow || anchor.width <= 0 || anchor.height <= 0) {
            YLog.warning(TAG, "Overlay probe skipped: anchor not ready attached=${anchor.isAttachedToWindow}")
            return
        }

        if (!added) {
            addWithFallback(anchor, normalizedRatio)
        } else {
            val params = createLayoutParams(anchor, normalizedRatio, activeType ?: preferredType())
            updateLayout(params)
        }
        probeView.setProbeEnabled(added, normalizedRatio)
    }

    fun updateLocation(anchor: View) {
        if (!added) return
        val params = createLayoutParams(anchor, lastRatio, activeType ?: preferredType())
        updateLayout(params)
    }

    fun hide() {
        probeView.setProbeEnabled(false, lastRatio)
        HdrStatusBarController.disableOverlayWindowProbe()
        if (!added) return
        runCatching {
            windowManager.removeViewImmediate(probeView)
        }.onFailure {
            YLog.error(TAG, "Overlay probe remove failed", it)
        }
        added = false
        activeType = null
        YLog.info(TAG, "HDR overlay probe removed")
    }

    private fun addWithFallback(
        anchor: View,
        ratio: Float
    ) {
        val triedTypes = mutableSetOf<Int>()
        val candidates = listOf(preferredType()) + FALLBACK_WINDOW_TYPES
        for (type in candidates) {
            if (!triedTypes.add(type)) continue
            val params = createLayoutParams(anchor, ratio, type)
            HdrStatusBarController.applyHdrToWindowLayoutParams(params, ratio, SOURCE)
            val added = runCatching {
                windowManager.addView(probeView, params)
            }.onFailure {
                YLog.error(TAG, "Overlay probe add failed: type=$type", it)
            }.isSuccess

            if (added) {
                this.added = true
                activeType = type
                YLog.info(TAG, "HDR overlay probe added: type=$type x=${params.x} y=${params.y}")
                return
            }
        }
    }

    private fun updateLayout(params: WindowManager.LayoutParams) {
        HdrStatusBarController.applyHdrToWindowLayoutParams(params, lastRatio, SOURCE, shouldLog = false)
        runCatching {
            windowManager.updateViewLayout(probeView, params)
        }.onFailure {
            YLog.error(TAG, "Overlay probe update failed", it)
        }
    }

    private fun createLayoutParams(
        anchor: View,
        ratio: Float,
        type: Int
    ): WindowManager.LayoutParams {
        val width = PROBE_WIDTH_DP.dp
        val height = PROBE_HEIGHT_DP.dp
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val displayWidth = anchor.resources.displayMetrics.widthPixels
        val x = (location[0] + anchor.width + 4.dp)
            .coerceIn(0, (displayWidth - width).coerceAtLeast(0))
        val y = (location[1] + (anchor.height - height) / 2)
            .coerceAtLeast(0)

        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            this.x = x
            this.y = y
            alpha = 1.0f
            setTitle(WINDOW_TITLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            HdrStatusBarController.applyHdrToWindowLayoutParams(this, ratio, SOURCE)
        }
    }

    private fun preferredType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun Float.normalizedRatio(): Float =
        if (isFinite() && this > MIN_RATIO) coerceAtMost(MAX_RATIO) else MIN_RATIO

    companion object {
        private const val TAG = "HdrOverlayProbeController"
        private const val SOURCE = "overlay-window-probe"
        const val WINDOW_TITLE = "Lyricon HDR Overlay Probe"
        private const val MIN_RATIO = 1.0f
        private const val MAX_RATIO = 8.0f
        private const val PROBE_WIDTH_DP = 36
        private const val PROBE_HEIGHT_DP = 14
        private const val TYPE_SYSTEM_OVERLAY = 2006
        private const val TYPE_STATUS_BAR_PANEL = 2014
        private const val TYPE_STATUS_BAR_SUB_PANEL = 2017
        private val FALLBACK_WINDOW_TYPES = listOf(
            TYPE_STATUS_BAR_SUB_PANEL,
            TYPE_STATUS_BAR_PANEL,
            TYPE_SYSTEM_OVERLAY
        )
        private const val WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
