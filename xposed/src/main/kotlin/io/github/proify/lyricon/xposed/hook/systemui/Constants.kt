/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.hook.systemui

import android.annotation.SuppressLint
import android.content.Context

object Constants {
    val isDebug: Boolean get() = false
    var statusBarLayoutId: Int = 0
    var clockId: Int = 0

    @SuppressLint("DiscouragedApi")
    fun initResourceIds(context: Context) {
        val resources = context.resources
        statusBarLayoutId =
            resources.getIdentifier("status_bar", "layout", context.packageName)
        clockId =
            resources.getIdentifier("clock", "id", context.packageName)
    }
}