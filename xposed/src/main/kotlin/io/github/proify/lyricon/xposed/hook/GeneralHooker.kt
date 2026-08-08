/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.hook

import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.Directory

object GeneralHooker : PackageHooker() {
    const val TAG = "GeneralHooker"

    override fun onHook() {
        doOnAppCreated {
            YLog.info(TAG, "App created: ${it.packageName}")
            Directory.initialize(it)
        }
    }
}