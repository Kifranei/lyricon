/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.bridge

import androidx.annotation.Keep
import io.github.proify.lyricon.common.Constants

object AppBridge {

    @Keep
    fun isActive(): Boolean = false

    object LyricStylePrefs {
        const val LYRIC_STYLE_PREF_NAME_PREIFY: String = "lyricon_style_"

        const val DEFAULT_PACKAGE_NAME: String = Constants.APP_PACKAGE_NAME

        const val PREF_NAME_BASE: String = LYRIC_STYLE_PREF_NAME_PREIFY + "base"
        const val PREF_NAME_PACKAGE_MANAGER: String =
            LYRIC_STYLE_PREF_NAME_PREIFY + "package_manager"

        const val KEY_ENABLED_PACKAGES: String = "enables"
        const val KEY_CONFIGURED_PACKAGES: String = "configured"

        fun getPackageStylePrefName(packageName: String): String {
            val prefix = LYRIC_STYLE_PREF_NAME_PREIFY + "app_"
            return prefix + (packageName.replace(".", "_"))
        }

    }

}