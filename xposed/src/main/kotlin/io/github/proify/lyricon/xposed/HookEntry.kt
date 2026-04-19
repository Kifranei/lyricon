/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.xposed.lyricon.LyriconHooker
import io.github.proify.lyricon.xposed.systemui.Directory
import io.github.proify.lyricon.xposed.systemui.SystemUIHooker

class HookEntry : IXposedHookLoadPackage {

    private lateinit var helper: PackageHelper

    override fun handleLoadPackage(packageParam: XC_LoadPackage.LoadPackageParam?) {
        if (packageParam == null) return
        helper = PackageHelper(packageParam)
        helper.doOnAppCreated { Directory.initialize(it) }

        when (packageParam.packageName) {
            PackageNames.APPLICATION -> loadApp(LyriconHooker)
            PackageNames.SYSTEM_UI -> loadApp(SystemUIHooker)
        }
    }

    private fun loadApp(hooker: PackageHooker) {
        hooker.onAttach(helper)
        hooker.onHook()
    }
}