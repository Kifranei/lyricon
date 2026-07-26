/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import androidx.annotation.Keep
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.xposed.hook.GeneralHooker
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.SystemUIHooker

/**
 * ooooo        oooooo   oooo ooooooooo.   ooooo   .oooooo.     .oooooo.   ooooo      ooo
 * `888'         `888.   .8'  `888   `Y88. `888'  d8P'  `Y8b   d8P'  `Y8b  `888b.     `8'
 *  888           `888. .8'    888   .d88'  888  888          888      888  8 `88b.    8
 *  888            `888.8'     888ooo88P'   888  888          888      888  8   `88b.  8
 *  888             `888'      888`88b.     888  888          888      888  8     `88b.8
 *  888       o      888       888  `88b.   888  `88b    ooo  `88b    d88'  8       `888
 * o888ooooood8     o888o     o888o  o888o o888o  `Y8bood8P'   `Y8bood8P'  o8o        `8
 *
 *  @author Tomakino
 *  @date 2026/05/03
 */
@Keep
class ModuleEntry : XposedModule() {

    companion object {
        private const val TAG = "ModuleEntry"

        private val scopes = listOf(
            PackageNames.APPLICATION,
            PackageNames.SYSTEM_UI,
        )

        lateinit var instance: ModuleEntry
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)
        YLog.info(TAG, "onPackageReady: packageName=${param.packageName}")
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        YLog.info(TAG, "onSystemServerStarting: classLoader=${param.classLoader}")
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        instance = this
        YLog.init(this)
        YLog.info(
            TAG,
            "onModuleLoaded: isSystemServer=${param.isSystemServer}, processName=${param.processName}"
        )
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val packageName = param.packageName
        if (packageName !in scopes) {
            YLog.debug(TAG, "onPackageLoaded: $packageName is not in scopes")
            return
        }

        YLog.info(TAG, "onPackageLoaded: $packageName")

        GeneralHooker.hook(this, param)
        when (packageName) {
            PackageNames.SYSTEM_UI -> SystemUIHooker.hook(this, param)
        }
    }
}