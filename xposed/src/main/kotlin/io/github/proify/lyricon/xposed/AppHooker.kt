/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.app.bridge.FrameworkInfo
import io.github.proify.lyricon.xposed.systemui.Directory

object AppHooker : YukiBaseHooker() {

    override fun onHook() {
        val preferenceDirectory = Directory.preferenceDirectory
        val frameworkInfo = resolveFrameworkInfo()

        AppBridge::class.java.name.toClass()
            .resolve().apply {
                firstMethod {
                    name = "getPreferenceDirectory"
                }.hook {
                    replaceTo(preferenceDirectory)
                }
                firstMethod {
                    name = "getFrameworkInfo"
                }.hook {
                    replaceTo(frameworkInfo)
                }
            }
    }

    private fun resolveFrameworkInfo(): FrameworkInfo? = null
}