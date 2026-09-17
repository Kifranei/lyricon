/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build

/**
 * 彩虹渐变缓存：同一 (宽度, 配色, HDR 比率) 组合复用同一个 [LinearGradient]。
 *
 * 供正文着色（textPaint）与逐词着色（背景/高亮 [TextDrawer]）共用，
 * 避免两处各自维护一套彩虹 shader 缓存。
 *
 * [hdrRatio] 大于 1 且系统支持时走扩展色域（EXTENDED_SRGB）的长整型配色，
 * 让高亮在 HDR 屏上超出 SDR 白点；构建失败时回退为普通 SDR 渐变。
 */
internal class RainbowGradientCache {
    private var cached: LinearGradient? = null
    private var lastWidth = -1f
    private var lastColorsHash = 0
    private var lastHdrRatio = 1f

    /** 获取与 (width, colors, hdrRatio) 匹配的彩虹渐变；任一项变化时重建。 */
    fun getOrCreate(width: Float, colors: IntArray, hdrRatio: Float = 1f): LinearGradient {
        val colorsHash = colors.contentHashCode()
        if (cached == null ||
            lastWidth != width ||
            lastColorsHash != colorsHash ||
            lastHdrRatio != hdrRatio
        ) {
            cached = createHdr(width, colors, hdrRatio) ?: LinearGradient(
                0f, 0f, width, 0f,
                colors, null, Shader.TileMode.CLAMP
            )
            lastWidth = width
            lastColorsHash = colorsHash
            lastHdrRatio = hdrRatio
        }
        return cached!!
    }

    private fun createHdr(width: Float, colors: IntArray, hdrRatio: Float): LinearGradient? {
        if (!isHdrActive(hdrRatio)) return null
        return runCatching {
            LinearGradient(
                0f, 0f, width, 0f,
                LongArray(colors.size) { HdrColor.packHighlightColor(colors[it], hdrRatio) },
                null,
                Shader.TileMode.CLAMP
            )
        }.getOrNull()
    }

    fun clear() {
        cached = null
        lastWidth = -1f
        lastColorsHash = 0
        lastHdrRatio = 1f
    }

    companion object {
        /** HDR 仅在比率高于 SDR 白点且系统支持扩展色域时生效。 */
        fun isHdrActive(ratio: Float): Boolean =
            ratio.isFinite() && ratio > 1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
