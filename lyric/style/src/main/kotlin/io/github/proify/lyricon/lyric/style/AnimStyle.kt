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

@Serializable
@Parcelize
data class AnimStyle(
    var enable: Boolean = Defaults.ENABLE,
    var id: String = Defaults.ID
) : AbstractStyle(), Parcelable {

    object Defaults {
        const val ENABLE: Boolean = true
        const val ID: String = "default"
    }

    override fun onLoad(preferences: SharedPreferences) {
        enable = preferences.getBoolean("lyric_style_anim_enable", Defaults.ENABLE)
        id = preferences.getString("lyric_style_anim_id", Defaults.ID) ?: Defaults.ID
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putBoolean("lyric_style_anim_enable", enable)
        editor.putString("lyric_style_anim_id", id)
    }
}