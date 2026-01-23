/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.xposed.Dirs

object AppHooker : YukiBaseHooker() {

    override fun onHook() {
        replaceGetPreferenceDirectory()
    }

    private fun replaceGetPreferenceDirectory() {
        val preferenceDirectory = Dirs.preferenceDirectory ?: return
        AppBridge::class.java.name.toClass(appClassLoader)
            .resolve().apply {
                firstMethod {
                    name = "getPreferenceDirectory"
                }.hook {
                    replaceTo(preferenceDirectory)
                }
                firstMethod {
                    name = "isModuleActive"
                }.hook {
                    replaceTo(true)
                }
            }
    }
}