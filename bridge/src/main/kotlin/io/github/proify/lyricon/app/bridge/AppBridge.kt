/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.bridge

import android.content.Context
import androidx.annotation.Keep
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import io.github.proify.android.extensions.getWorldReadableSharedPreferences
import io.github.proify.lyricon.common.Constants
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.common.StateSharedPreferences
import io.github.proify.lyricon.common.StateSharedPreferencesWrapper
import java.io.File

/**
 * 桥接模块
 * 用于给xposed环境hook
 */
object AppBridge {

    val isXposedEnv by lazy {
        try {
            XposedBridge.getXposedVersion() > 0
            true
        } catch (e: Throwable) {
            false
        }
    }

    @Keep
    fun isModuleActive(): Boolean = false

    fun getSharedPreferences(context: Context, name: String): StateSharedPreferences {
        if (isXposedEnv) {
            return XStateSharedPreferences(
                XSharedPreferences(PackageNames.APPLICATION, name)
            )
        }
        return StateSharedPreferencesWrapper(context.getWorldReadableSharedPreferences(name))
    }

//    fun getPreferenceFile(context: Context, name: String): File {
//        return File(getPreferenceDirectory(context), name)
//    }

    private val xpreferencesDirectory: File by lazy {
        XSharedPreferences(PackageNames.APPLICATION, "tmp").file.parentFile!!
    }

    fun getPreferenceDirectory(context: Context): File {
        if (isXposedEnv) return xpreferencesDirectory

        return context.dataDir.resolve("shared_prefs")
    }

    object LyricStylePrefs {
        const val DEFAULT_PACKAGE_NAME: String = Constants.APP_PACKAGE_NAME
        const val PREF_NAME_BASE_STYLE: String = "baseLyricStyle"
        const val PREF_PACKAGE_STYLE_MANAGER: String = "packageStyleManager"
        const val KEY_ENABLED_PACKAGES: String = "enables"

        fun getPackageStylePreferenceName(packageName: String): String =
            "package_style_${packageName.replace(".", "_")}"
    }
}