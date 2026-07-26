/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import io.github.proify.android.extensions.crc32
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.lyric.style.LogoStyle

/**
 * 策略：显示专辑封面。
 * 支持圆形旋转（唱片模式）和圆角矩形，不跟随状态栏颜色。
 */
class CoverStrategy(
    private val view: SuperLogo
) : ILogoStrategy {

    companion object {
        private const val DEFAULT_ROTATION_DURATION_MS = 12_000L
        private val SQUIRCLE_CORNER_RADIUS_DP by lazy { 3.5f.dp.toFloat() }
    }

    private var rotationAnimator: ObjectAnimator? = null
    private var lastFileSignature: String? = null

    override var isEffective: Boolean = false
        private set

    var style: Int = LogoStyle.STYLE_COVER_CIRCLE

    override fun updateContent() {
        // 封面模式清除 Tint
        if (view.imageTintList != null) view.imageTintList = null

        val coverFile = view.coverFile
        if (coverFile == null || !coverFile.exists()) {
            view.setImageDrawable(null)
            isEffective = false
            lastFileSignature = null
        } else {
            val signature = coverFile.crc32().toString()

            // 只有文件变动或未初始化时才重新加载
            if (signature != lastFileSignature || view.drawable == null) {
                val bitmap: Bitmap? = coverFile.toBitmap(view.width, view.height)
                view.setImageBitmap(bitmap)
                lastFileSignature = signature
                isEffective = bitmap != null

                stopAnimation(true)
            }
        }

        // 始终应用 Outline 和 动画状态检查，以防 Style 变更
        applyStyleAndAnimation()
        view.updateVisibility()
    }

    private fun applyStyleAndAnimation() {
        val currentStyle =
            view.lyricStyle?.packageStyle?.logo?.style ?: LogoStyle.Companion.STYLE_COVER_CIRCLE
        val oldStyle = this.style
        this.style = currentStyle

        // 设置裁剪轮廓
        applyOutlineProvider(currentStyle)

        // 如果从圆形切换到其他样式，必须强制重置旋转角度
        if (oldStyle == LogoStyle.STYLE_COVER_CIRCLE && currentStyle != LogoStyle.Companion.STYLE_COVER_CIRCLE) {
            view.rotation = 0f
        }

        // 检查动画状态
        checkAnimationState()
    }

    override fun onColorUpdate() {
        // 封面保持原色，不应用 Tint
    }

    override fun onAttach() {
        // 恢复视图状态
        updateContent()
        checkAnimationState()
    }

    override fun onDetach() {
        stopAnimation()
        // 可以在此释放图片，下次 onAttach 时会通过 updateContent 重新加载
        // setImageDrawable(null)
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible) checkAnimationState() else stopAnimation()
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

    private fun checkAnimationState() {
        if (view.isAttachedToWindow && view.isShown && isEffective && style == LogoStyle.Companion.STYLE_COVER_CIRCLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimation() {
        if (rotationAnimator?.isRunning == true) return

        rotationAnimator =
            ObjectAnimator.ofFloat(
                view, "rotation",
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

        // 注意：这里不自动重置 rotation 为 0，以便暂停后恢复时视觉连贯。
        // 彻底重置由 LyricLogoView.resetViewAttributes() 在切换策略时处理。

        if (resetRotation) view.rotation = 0f
    }
}
