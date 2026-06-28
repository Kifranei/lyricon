/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import io.github.proify.android.extensions.crc32
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.lyric.style.LogoStyle
import java.util.Locale

class CoverStrategy(
    private val view: SuperLogo
) : ILogoStrategy {

    companion object {
        private const val TAG = "CoverStrategy"
        private const val DEFAULT_ROTATION_DURATION_MS = 12_000L
        private const val MIN_HDR_RATIO = 1.0f
        private const val MAX_HDR_RATIO_FOR_BOOST = 4.0f
        private const val MAX_SATURATION_BOOST = 1.55f
        private const val MAX_CONTRAST_BOOST = 1.14f
        private const val MAX_BRIGHTNESS_OFFSET = 10f
        private val SQUIRCLE_CORNER_RADIUS_DP by lazy { 3.5f.dp.toFloat() }
    }

    private var rotationAnimator: ObjectAnimator? = null
    private var lastFileSignature: String? = null
    private var lastCompensationFingerprint: String? = null

    override var isEffective: Boolean = false
        private set

    var style: Int = LogoStyle.STYLE_COVER_CIRCLE

    override fun updateContent() {
        view.resetImageVisualState()

        val coverFile = view.coverFile
        if (coverFile == null || !coverFile.exists()) {
            view.setSelfDrawnCover(null)
            view.setSelfDrawnCoverColorFilter(null)
            view.setImageDrawable(null)
            isEffective = false
            lastFileSignature = null
            lastCompensationFingerprint = null
        } else {
            val signature = coverFile.crc32().toString()

            if (signature != lastFileSignature || !view.hasSelfDrawnCover) {
                val bitmap: Bitmap? = coverFile.toBitmap(view.width, view.height)
                view.setImageDrawable(null)
                view.setSelfDrawnCover(bitmap)
                lastFileSignature = signature
                isEffective = bitmap != null

                logCoverBitmapApplied(coverFile, bitmap, signature)
                stopAnimation(true)
            }
        }

        applyStyleAndAnimation()
        applyHdrCoverCompensation()
        view.updateVisibility()
    }

    override fun onColorUpdate() {
        view.resetImageVisualState()
        applyHdrCoverCompensation()
    }

    override fun onAttach() {
        updateContent()
        checkAnimationState()
    }

    override fun onDetach() {
        stopAnimation()
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible) {
            checkAnimationState()
        } else {
            stopAnimation()
        }
    }

    private fun applyStyleAndAnimation() {
        val currentStyle =
            view.lyricStyle?.packageStyle?.logo?.style ?: LogoStyle.STYLE_COVER_CIRCLE
        val oldStyle = style
        style = currentStyle

        applyOutlineProvider(currentStyle)

        if (oldStyle == LogoStyle.STYLE_COVER_CIRCLE && currentStyle != LogoStyle.STYLE_COVER_CIRCLE) {
            view.rotation = 0f
        }

        checkAnimationState()
    }

    private fun applyOutlineProvider(style: Int) {
        val provider = when (style) {
            LogoStyle.STYLE_COVER_CIRCLE -> object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }

            LogoStyle.STYLE_COVER_SQUIRCLE -> object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        SQUIRCLE_CORNER_RADIUS_DP
                    )
                }
            }

            else -> null
        }

        view.outlineProvider = provider
        view.clipToOutline = provider != null
    }

    private fun applyHdrCoverCompensation() {
        if (!isEffective) {
            view.setSelfDrawnCoverColorFilter(null)
            lastCompensationFingerprint = null
            return
        }

        val progress = ((view.hdrHighlightRatio - MIN_HDR_RATIO) /
                (MAX_HDR_RATIO_FOR_BOOST - MIN_HDR_RATIO))
            .coerceIn(0f, 1f)
        if (progress <= 0f) {
            view.setSelfDrawnCoverColorFilter(null)
            logCompensationChanged("off")
            return
        }

        val saturation = MIN_HDR_RATIO + (MAX_SATURATION_BOOST - MIN_HDR_RATIO) * progress
        val contrast = MIN_HDR_RATIO + (MAX_CONTRAST_BOOST - MIN_HDR_RATIO) * progress
        val brightness = MAX_BRIGHTNESS_OFFSET * progress

        val saturationMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, 128f * (1f - contrast) + brightness,
                0f, contrast, 0f, 0f, 128f * (1f - contrast) + brightness,
                0f, 0f, contrast, 0f, 128f * (1f - contrast) + brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        saturationMatrix.postConcat(contrastMatrix)
        view.setSelfDrawnCoverColorFilter(ColorMatrixColorFilter(saturationMatrix))
        logCompensationChanged(
            "ratio=${view.hdrHighlightRatio.format2()} " +
                    "saturation=${saturation.format2()} " +
                    "contrast=${contrast.format2()} " +
                    "brightness=${brightness.format2()}"
        )
    }

    private fun logCoverBitmapApplied(coverFile: java.io.File, bitmap: Bitmap?, signature: String) {
        if (bitmap == null) {
            Log.w(TAG, "Cover bitmap decode failed: path=${coverFile.absolutePath}")
            return
        }

        val centerColor = runCatching {
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        }.getOrDefault(Color.TRANSPARENT)
        val hsv = FloatArray(3)
        Color.colorToHSV(centerColor, hsv)
        Log.i(
            TAG,
            "Cover bitmap applied: selfDraw=true " +
                    "path=${coverFile.absolutePath} " +
                    "signature=$signature " +
                    "size=${bitmap.width}x${bitmap.height} " +
                    "config=${bitmap.config} " +
                    "center=${centerColor.toColorHex()} " +
                    "centerSat=${hsv[1].format2()} " +
                    sampledSaturationSummary(bitmap) + " " +
                    "hdrRatio=${view.hdrHighlightRatio.format2()}"
        )
    }

    private fun sampledSaturationSummary(bitmap: Bitmap): String {
        val hsv = FloatArray(3)
        val xs = intArrayOf(bitmap.width / 4, bitmap.width / 2, bitmap.width * 3 / 4)
        val ys = intArrayOf(bitmap.height / 4, bitmap.height / 2, bitmap.height * 3 / 4)
        var count = 0
        var coloredCount = 0
        var satSum = 0f
        var maxSat = 0f
        var maxSatColor = Color.TRANSPARENT

        for (x in xs) {
            for (y in ys) {
                val color = bitmap.getPixel(
                    x.coerceIn(0, bitmap.width - 1),
                    y.coerceIn(0, bitmap.height - 1)
                )
                Color.colorToHSV(color, hsv)
                val sat = hsv[1]
                satSum += sat
                count++
                if (sat >= 0.12f) coloredCount++
                if (sat > maxSat) {
                    maxSat = sat
                    maxSatColor = color
                }
            }
        }

        val avgSat = if (count > 0) satSum / count else 0f
        return "sampleAvgSat=${avgSat.format2()} " +
                "sampleMaxSat=${maxSat.format2()} " +
                "sampleMax=${maxSatColor.toColorHex()} " +
                "sampleColored=$coloredCount/$count"
    }

    private fun logCompensationChanged(fingerprint: String) {
        if (fingerprint == lastCompensationFingerprint) return
        lastCompensationFingerprint = fingerprint
        Log.i(TAG, "Cover HDR compensation: $fingerprint selfDraw=${view.hasSelfDrawnCover}")
    }

    private fun Float.format2(): String = String.format(Locale.US, "%.2f", this)

    private fun Int.toColorHex(): String = String.format(Locale.US, "#%08X", this)

    private fun checkAnimationState() {
        if (view.isAttachedToWindow &&
            view.isShown &&
            isEffective &&
            style == LogoStyle.STYLE_COVER_CIRCLE
        ) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimation() {
        if (rotationAnimator?.isRunning == true) return

        rotationAnimator =
            ObjectAnimator.ofFloat(
                view,
                "rotation",
                view.rotation,
                view.rotation + 360f
            ).apply {
                duration = DEFAULT_ROTATION_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                start()
            }
    }

    private fun stopAnimation(resetRotation: Boolean = false) {
        rotationAnimator?.cancel()
        rotationAnimator = null
        if (resetRotation) view.rotation = 0f
    }
}
