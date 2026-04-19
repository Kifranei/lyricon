/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.bridge

import android.content.Context
import androidx.annotation.Keep
import io.github.proify.lyricon.common.Constants
import java.io.File

/**
 * 桥接模块
 * 用于给xposed环境hook
 */
object AppBridge {

    @Keep
    fun isModuleActive(): Boolean = false

    @Keep
    fun getPreferenceDirectory(context: Context): File = context.dataDir.resolve("shared_prefs")

    object LyricStylePrefs {
        const val DEFAULT_PACKAGE_NAME: String = Constants.APP_PACKAGE_NAME
        const val PREF_NAME_BASE_STYLE: String = "baseLyricStyle"
        const val PREF_PACKAGE_STYLE_MANAGER: String = "packageStyleManager"
        const val KEY_ENABLED_PACKAGES: String = "enables"

        fun getPackageStylePreferenceName(packageName: String): String =
            "package_style_${packageName.replace(".", "_")}"
    }
}