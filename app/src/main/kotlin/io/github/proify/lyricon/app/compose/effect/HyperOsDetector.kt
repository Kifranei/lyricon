/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.effect

import android.annotation.SuppressLint
import android.os.Build

/**
 * HyperOS 大版本探测，用于决定流光背景使用哪一代观感。
 *
 * - HyperOS 1：不绘制流光背景（[BG_EFFECT_NONE]）
 * - HyperOS 2：OS2 观感（[BG_EFFECT_OS2]）
 * - HyperOS 3 及以上 / 非小米设备：OS3 观感（[BG_EFFECT_OS3]）
 */
object HyperOsDetector {

    const val BG_EFFECT_OS2 = 0
    const val BG_EFFECT_OS3 = 1
    const val BG_EFFECT_NONE = 2

    /** 流光风格偏好键；取值见 [STYLE_AUTO] / [STYLE_OS2] / [STYLE_OS3]。 */
    const val KEY_BG_EFFECT_STYLE = "flowing_background_style"

    /** 跟随系统：按 HyperOS 大版本自动选择（OS1 不绘制流光）。 */
    const val STYLE_AUTO = 0

    /** 强制 OS2 观感。 */
    const val STYLE_OS2 = 1

    /** 强制 OS3 观感。 */
    const val STYLE_OS3 = 2

    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

    fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER.orEmpty().lowercase() in XIAOMI_BRANDS ||
                Build.BRAND.orEmpty().lowercase() in XIAOMI_BRANDS

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, key, "") as? String).orEmpty()
    }.getOrDefault("")

    /**
     * 从系统属性解析 HyperOS 主版本号，例如 "OS2.0.5.0" -> 2。
     *
     * 非小米设备或读不到版本属性时返回 null。
     */
    fun getHyperOsMajorVersion(): Int? {
        if (!isXiaomiDevice()) return null
        val raw = getSystemProperty("ro.mi.os.version.name")
            .ifBlank { getSystemProperty("ro.build.version.incremental") }
        if (raw.isBlank()) return null
        return Regex("""OS(\d+)""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /** 当前设备默认使用的流光背景版本。 */
    fun defaultBgEffectVersion(): Int = when (getHyperOsMajorVersion()) {
        null -> BG_EFFECT_OS3
        1 -> BG_EFFECT_NONE
        2 -> BG_EFFECT_OS2
        else -> BG_EFFECT_OS3
    }

    /**
     * 按用户选择解析实际使用的流光版本。
     *
     * 手动选择 OS2 / OS3 时无视系统版本（包括 OS1 也会显示流光）；
     * [STYLE_AUTO] 时回落到 [defaultBgEffectVersion]。
     */
    fun resolveBgEffectVersion(style: Int): Int = when (style) {
        STYLE_OS2 -> BG_EFFECT_OS2
        STYLE_OS3 -> BG_EFFECT_OS3
        else -> defaultBgEffectVersion()
    }
}
