/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.util.Log
import io.github.proify.lyricon.common.util.SVGHelper
import io.github.proify.lyricon.subscriber.ProviderLogo

/**
 * 策略：显示歌词提供方（Provider）的 Logo。
 * 通常为单色 SVG 或 Bitmap，需要适配状态栏颜色。
 */
class ProviderStrategy(
    private val view: SuperLogo
) : ILogoStrategy {
    companion object {
        private const val TAG = "ProviderStrategy"
    }

    override var isEffective: Boolean = false
        private set

    private var cachedBitmap: Bitmap? = null
    private var lastProviderSignature: String? = null

    fun invalidateCache() {
        cachedBitmap = null
        lastProviderSignature = null
    }

    override fun updateContent() {
        // 提供者 Logo 通常无需裁剪 Outline
        if (view.outlineProvider != null) view.outlineProvider = null

        val bitmap = loadProviderBitmap()
        view.setImageBitmap(bitmap)
        isEffective = bitmap != null

        onColorUpdate() // 内容更新后立即应用颜色
        view.updateVisibility()
    }

    override fun onColorUpdate() {
        view.imageTintList = when {
            view.providerLogo?.colorful == true -> null
            else -> calculateTint()
        }
    }

    override fun onAttach() {
        // 如果在 detach 期间清理了 Bitmap，这里可以尝试重新加载
        if (view.drawable == null && isEffective) {
            updateContent()
        }
    }

    override fun onDetach() {
        // 释放 Bitmap 引用以减轻内存压力
        cachedBitmap = null
        lastProviderSignature = null
        view.setImageDrawable(null)
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible && view.drawable == null) {
            updateContent()
        }
    }

    private fun loadProviderBitmap(): Bitmap? {
        val logo = view.providerLogo ?: return null

        val lp = view.layoutParams ?: return null
        val w = lp.width
        val h = lp.height

        val signature = "${logo.hashCode()}_${w}_${h}_${logo.type}"
        Log.d(TAG, "Provider logo signature: $signature")

        if (signature == lastProviderSignature && cachedBitmap != null) {
            return cachedBitmap
        }

        val bmp = when (logo.type) {
            ProviderLogo.Companion.TYPE_BITMAP -> logo.toBitmap()
            ProviderLogo.Companion.TYPE_SVG -> {
                val svgString = logo.toSvg()
                if (svgString.isNullOrBlank()) {
                    Log.w(TAG, "Invalid SVG string")
                    null
                } else {
                    Log.d(
                        TAG,
                        "SVG string: w:$w, h:$h, svg: $svgString"
                    )
                    runCatching {
                        SVGHelper.Companion.create(svgString).createBitmap(w, h)
                    }.getOrNull()
                }
            }

            else -> null
        }

        cachedBitmap = bmp
        lastProviderSignature = signature
        return bmp
    }

    private fun calculateTint(): ColorStateList {
        val currentStatusColor = view.currentStatusColor
        val logoStyle = view.lyricStyle?.packageStyle?.logo
            ?: return ColorStateList.valueOf(currentStatusColor.firstColor())

        if (!logoStyle.enableCustomColor) {
            return ColorStateList.valueOf(currentStatusColor.firstColor())
        }

        val logoColorConfig = logoStyle.color(currentStatusColor.isLightMode)
        val finalColor = when {
            logoColorConfig.followTextColor -> resolveFollowTextColor()
            logoColorConfig.color != 0 -> logoColorConfig.color
            else -> currentStatusColor.firstColor()
        }

        return ColorStateList.valueOf(finalColor)
    }

    private fun resolveFollowTextColor(): Int {
        val currentStatusColor = view.currentStatusColor

        val textStyle = view.lyricStyle?.packageStyle?.text
        if (textStyle?.enableCustomTextColor != true) {
            return currentStatusColor.firstColor()
        }
        val textColorConfig = textStyle.color(currentStatusColor.isLightMode)
        return if (textColorConfig != null && textColorConfig.normal.isNotEmpty()) {
            textColorConfig.normal.firstOrNull() ?: currentStatusColor.firstColor()
        } else {
            currentStatusColor.firstColor()
        }
    }
}