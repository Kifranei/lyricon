/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel
import io.github.proify.lyricon.app.LyriconApp.Companion.systemUIChannel
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.common.util.safe

class LyriconApp : ModuleApplication() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: LyriconApp

        val packageInfo: PackageInfo by lazy {
            instance.packageManager.getPackageInfo(
                instance.packageName, 0
            )
        }
        val versionCode: Long by lazy { PackageInfoCompat.getLongVersionCode(packageInfo) }

        val systemUIChannel: YukiHookDataChannel.NameSpace by lazy {
            instance.dataChannel(packageName = PackageNames.SYSTEM_UI)
        }

        private var _safeMode: Boolean = false

        val safeMode: Boolean get() = _safeMode

        fun updateSafeMode(safeMode: Boolean) {
            _safeMode = safeMode
        }
    }

    init {
        instance = this
    }

    override fun attachBaseContext(base: Context) {
        AppLangUtils.setDefaultLocale(base)
        super.attachBaseContext(AppLangUtils.wrapContext(base))
    }

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        super.getSharedPreferences(name, mode).safe()
}

fun updateLyricStyle() {
    systemUIChannel.put(AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE)
}