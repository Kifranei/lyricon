/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.util.Log
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.LyriconApp.Companion.systemUIChannel
import io.github.proify.lyricon.app.bridge.AppBridgeConstants

fun updateRemoteLyricStyle() {
    fun getCallSourceMethod(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return if (stackTrace.size > 3) "${stackTrace[3].className}.${stackTrace[3].methodName}" else "Unknown"
    }
    Log.d(LyriconApp.TAG, "updateRemoteLyricStyle called from ${getCallSourceMethod()}")
    systemUIChannel.put(AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE)
}