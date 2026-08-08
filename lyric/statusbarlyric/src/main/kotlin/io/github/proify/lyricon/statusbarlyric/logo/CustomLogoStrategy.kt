/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.github.proify.lyricon.common.util.SVGHelper

/**
 * 策略：显示用户自定义图标。
 * 支持 Base64 编码的图片和原始 SVG 文本。
 */
class CustomLogoStrategy(
    private val view: SuperLogo
) : ILogoStrategy {
    override var isEffective: Boolean = false
        private set

    private var cachedBitmap: Bitmap? = null
    private var lastSignature: String? = null

    override fun updateContent() {
        if (view.outlineProvider != null) view.outlineProvider = null

        val bitmap = loadCustomBitmap()
        view.setImageBitmap(bitmap)
        isEffective = bitmap != null

        onColorUpdate()
        view.updateVisibility()
    }

    override fun onColorUpdate() {
        val logoStyle = view.lyricStyle?.packageStyle?.logo

        view.imageTintList =
            if (logoStyle?.colorfulCustomLogo == true) null else calculateCustomTint()
    }

    override fun onAttach() {
        if (view.drawable == null && isEffective) {
            updateContent()
        }
    }

    override fun onDetach() {
        cachedBitmap = null
        lastSignature = null
        view.setImageDrawable(null)
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible && view.drawable == null) {
            updateContent()
        }
    }

    private fun loadCustomBitmap(): Bitmap? {
        val customLogoStr = view.lyricStyle?.packageStyle?.logo?.customLogo
        if (customLogoStr.isNullOrBlank()) return null

        val lp = view.layoutParams ?: return null
        val w = lp.width
        val h = lp.height

        val signature = "${customLogoStr.hashCode()}_${w}_${h}"
        if (signature == lastSignature && cachedBitmap != null) {
            return cachedBitmap
        }

        val bmp = runCatching {
            when {
                isSvgString(customLogoStr) -> {
                    SVGHelper.create(customLogoStr).createBitmap(w, h)
                }

                customLogoStr.startsWith("data:") -> {
                    parseDataUri(customLogoStr, w, h)
                }

                else -> {
                    decodeBase64Bitmap(customLogoStr)
                }
            }
        }.getOrNull()

        cachedBitmap = bmp
        lastSignature = signature
        return bmp
    }

    private fun isSvgString(str: String): Boolean {
        val trimmed = str.trimStart()
        return trimmed.startsWith("<svg", ignoreCase = true) ||
                trimmed.startsWith("<?xml", ignoreCase = true)
    }

    private fun parseDataUri(dataUri: String, w: Int, h: Int): Bitmap? {
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex == -1) return null

        val header = dataUri.substring(0, commaIndex)
        val data = dataUri.substring(commaIndex + 1)
        val isBase64 = header.contains(";base64")

        val mimeType = header.substringAfter("data:").substringBefore(";")

        return when {
            mimeType.contains("svg") -> {
                val svgStr = if (isBase64) {
                    String(Base64.decode(data, Base64.DEFAULT))
                } else {
                    data
                }
                SVGHelper.create(svgStr).createBitmap(w, h)
            }

            isBase64 -> {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            else -> decodeBase64Bitmap(data)
        }
    }

    private fun decodeBase64Bitmap(base64Str: String): Bitmap? {
        val bytes = runCatching {
            Base64.decode(base64Str, Base64.DEFAULT)
        }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun calculateCustomTint(): ColorStateList? {
        val logoStyle = view.lyricStyle?.packageStyle?.logo
            ?: return null

        if (!logoStyle.enableCustomColor) {
            return null
        }

        val logoColorConfig = logoStyle.color(view.currentStatusColor.isLightMode)
        val finalColor = when {
            logoColorConfig.followTextColor -> resolveFollowTextColor()
            logoColorConfig.color != 0 -> logoColorConfig.color
            else -> view.currentStatusColor.firstColor()
        }

        return ColorStateList.valueOf(finalColor)
    }

    private fun resolveFollowTextColor(): Int {
        val textStyle = view.lyricStyle?.packageStyle?.text
        if (textStyle?.enableCustomTextColor != true) {
            return view.currentStatusColor.firstColor()
        }
        val textColorConfig = textStyle.color(view.currentStatusColor.isLightMode)
        return if (textColorConfig != null && textColorConfig.normal.isNotEmpty()) {
            textColorConfig.normal.firstOrNull() ?: view.currentStatusColor.firstColor()
        } else {
            view.currentStatusColor.firstColor()
        }
    }
}
