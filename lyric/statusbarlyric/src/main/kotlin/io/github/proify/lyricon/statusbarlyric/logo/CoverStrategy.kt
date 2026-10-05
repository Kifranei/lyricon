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
import android.os.Build
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

        // 真 HDR 路径：SuperLogo 走 AGSL 把亮部推到 SDR 白点之上，此时再叠加 SDR 色彩补偿
        // 只会把扩展色域输出重新拉回「用力过猛的 SDR」观感，因此必须让位。
        if (HdrLogoShader.isActive(view.hdrHighlightRatio)) {
            view.setSelfDrawnCoverColorFilter(null)
            logCompensationChanged("hdr-shader ratio=${view.hdrHighlightRatio.format2()} (sdr compensation skipped)")
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

        // HARDWARE 位图读不回像素，采样类信息直接跳过，避免 getPixel 抛异常
        val readable = bitmap.config != Bitmap.Config.HARDWARE && !bitmap.isRecycled
        val centerColor = if (readable) {
            runCatching { bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) }
                .getOrDefault(Color.TRANSPARENT)
        } else {
            Color.TRANSPARENT
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(centerColor, hsv)
        val colorSpaceName = bitmap.colorSpace?.name ?: "none"

        Log.i(
            TAG,
            "Cover bitmap applied: selfDraw=true " +
                    "path=${coverFile.absolutePath} " +
                    "signature=$signature " +
                    "size=${bitmap.width}x${bitmap.height} " +
                    "config=${bitmap.config} " +
                    "colorSpace=$colorSpaceName " +
                    "gainmap=${describeGainmap(bitmap)} " +
                    "center=${centerColor.toColorHex()} " +
                    "centerSat=${hsv[1].format2()} " +
                    (if (readable) sampledSaturationSummary(bitmap) else "sample=skipped(hardware)") + " " +
                    "hdrRatio=${view.hdrHighlightRatio.format2()} " +
                    // 注意：这里只表示「能力 + 比率就绪」，是否真的绑定成功要看 HdrLogoShader 的 bound 日志
                    "hdrPathArmed=${HdrLogoShader.isActive(view.hdrHighlightRatio)}"
        )
    }

    /**
     * Android 14+ 的 gainmap 探测。
     *
     * gainmap 是媒体源里「SDR 基底 + 高光增量」的载体，能反映封面是否真的携带 HDR 信息；
     * 当前封面链路会把它丢掉（[android.graphics.Bitmap.compress] 不写 gainmap），
     * 所以这里只做诊断，便于在设备上确认降级发生在哪一步。
     */
    private fun describeGainmap(bitmap: Bitmap): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return "unsupported(sdk=${Build.VERSION.SDK_INT})"
        }
        return runCatching {
            if (bitmap.gainmap != null) "present" else "none"
        }.getOrElse { "probe-failed(${it.javaClass.simpleName})" }
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
        var lumaSum = 0f
        var maxLuma = 0f

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
                // HDR 提亮是按亮度加权的，这里同时给出亮度分布，便于判断「不亮」是绑定失败还是封面本来就暗
                val luma = (0.2126f * Color.red(color) +
                        0.7152f * Color.green(color) +
                        0.0722f * Color.blue(color)) / 255f
                lumaSum += luma
                if (luma > maxLuma) maxLuma = luma
            }
        }

        val avgSat = if (count > 0) satSum / count else 0f
        val avgLuma = if (count > 0) lumaSum / count else 0f
        return "sampleAvgSat=${avgSat.format2()} " +
                "sampleMaxSat=${maxSat.format2()} " +
                "sampleMax=${maxSatColor.toColorHex()} " +
                "sampleColored=$coloredCount/$count " +
                "sampleAvgLuma=${avgLuma.format2()} " +
                "sampleMaxLuma=${maxLuma.format2()}"
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
