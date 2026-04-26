/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.util.Log
import androidx.annotation.Keep
import androidx.core.content.pm.PackageInfoCompat
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.common.util.safe

class LyriconApp : Application() {

    init {
        instance = this
    }

    @Keep
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "LyriconApp created")
    }

    override fun attachBaseContext(base: Context) {
        AppLangUtils.setDefaultLocale(base)
        super.attachBaseContext(AppLangUtils.wrapContext(base))
    }

    @Deprecated("This function is deprecated. Please use getSharedPreferences(String, Int, Class) instead.")
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        super.getSharedPreferences(name, mode).safe()

    companion object {
        const val TAG: String = "LyriconApp"

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: LyriconApp
            private set

        fun get(): LyriconApp = instance

        val packageInfo: PackageInfo by lazy {
            instance.packageManager.getPackageInfo(
                instance.packageName, 0
            )
        }
        val versionCode: Long by lazy { PackageInfoCompat.getLongVersionCode(packageInfo) }

        private var _safeMode: Boolean = false

        val safeMode: Boolean get() = _safeMode

        fun updateSafeMode(safeMode: Boolean) {
            _safeMode = safeMode
        }
    }
}