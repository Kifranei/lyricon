/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.common.rom

import android.annotation.SuppressLint
import android.os.Build

/**
 * Flyme / Meizu 设备探测。
 *
 * 用于原生状态栏 Ticker 歌词入口展示与运行时能力判断。
 */
object Flyme {

    val isFlymeDevice: Boolean by lazy {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val display = Build.DISPLAY.orEmpty()
        val fingerprint = Build.FINGERPRINT.orEmpty()
        manufacturer.contains("meizu", ignoreCase = true) ||
            brand.contains("meizu", ignoreCase = true) ||
            display.contains("flyme", ignoreCase = true) ||
            fingerprint.contains("flyme", ignoreCase = true) ||
            hasFlymeSystemProperty()
    }

    @SuppressLint("PrivateApi")
    private fun hasFlymeSystemProperty(): Boolean {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java, String::class.java)
            val keys = listOf(
                "ro.build.flyme.version",
                "ro.flyme.version",
                "ro.meizu.product.model",
            )
            keys.any { key ->
                (get.invoke(null, key, "") as? String)?.trim().orEmpty().isNotBlank()
            }
        }.getOrDefault(false)
    }
}
