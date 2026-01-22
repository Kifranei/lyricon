/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.content.SharedPreferences
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Deprecated(
    "未实现"
)
@Serializable
@Parcelize
data class AnimStyle(var enable: Boolean = false) : AbstractStyle(), Parcelable {

    override fun onLoad(preferences: SharedPreferences) {
        enable = preferences.getBoolean("lyric_style_anim_enable", true)
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putBoolean("lyric_style_anim_enable", enable)
    }
}