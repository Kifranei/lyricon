/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.lyricon

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.xposed.PackageHooker

object LyriconHooker : PackageHooker() {

    override fun onHook() {
        XposedHelpers.findAndHookMethod(
            AppBridge::class.java.name,
            classLoader,
            "isModuleActive",
            XC_MethodReplacement.returnConstant(true)
        )
    }
}