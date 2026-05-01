/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.miui

import io.github.proify.lyricon.xposed.PackageHooker
import io.github.proify.lyricon.xposed.logger.YLog

object MiuiSystemPlugin : PackageHooker() {
    private const val TAG = "MiuiSystemPlugin"

    override fun onHook() {
        YLog.info(TAG, "onHook")
    }
}