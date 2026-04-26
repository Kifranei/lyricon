/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.lyricon.LyriconHooker
import io.github.proify.lyricon.xposed.miui.MiuiSystemPlugin
import io.github.proify.lyricon.xposed.systemui.Directory
import io.github.proify.lyricon.xposed.systemui.SystemUIHooker

class HookEntry : IXposedHookLoadPackage {
    companion object {
        const val TAG = "HookEntry"
    }

    private val scopes = listOf(
        PackageNames.APPLICATION,
        PackageNames.SYSTEM_UI,
        PackageNames.MIUI_SYSTEM_UI_PLUGIN
    )

    private lateinit var helper: PackageHelper

    override fun handleLoadPackage(packageParam: XC_LoadPackage.LoadPackageParam?) {
        if (packageParam == null) return
        if (packageParam.packageName !in scopes) return

        helper = PackageHelper(packageParam)
        helper.doOnAppCreated { Directory.initialize(it) }

        YLog.info(TAG, "handleLoadPackage: ${packageParam.packageName}/${packageParam.processName}")
        when (packageParam.packageName) {
            PackageNames.APPLICATION -> loadApp(LyriconHooker)
            PackageNames.SYSTEM_UI -> loadApp(SystemUIHooker)
            PackageNames.MIUI_SYSTEM_UI_PLUGIN -> loadApp(MiuiSystemPlugin)
        }
    }

    private fun loadApp(hooker: PackageHooker) {
        hooker.onAttach(helper)
        hooker.onHook()
    }
}