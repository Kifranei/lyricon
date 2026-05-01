/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.util.Log
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.common.PackageNames

fun updateRemoteLyricStyle() {
    Log.d(LyriconApp.TAG, "updateRemoteLyricStyle called")

    LyriconBridge.with(LyriconApp.get())
        .to(PackageNames.SYSTEM_UI)
        .key(AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE)
        .send()
}