/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.content.SharedPreferences
import io.github.proify.lyricon.app.activity.BaseActivity
import io.github.proify.lyricon.app.updateRemoteLyricStyle

abstract class AbstractLyricActivity : BaseActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        if (key?.startsWith("lyric_style_") == true) {
            updateRemoteLyricStyle()
        }
    }
}