/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.bridge

import android.content.SharedPreferences
import de.robv.android.xposed.XSharedPreferences
import io.github.proify.lyricon.common.StateSharedPreferencesWrapper

class XStateSharedPreferences(private val preferences: XSharedPreferences) :
    StateSharedPreferencesWrapper(preferences) {

    override fun hasChanged(): Boolean = preferences.hasFileChanged()

    override fun reload(): Boolean {
        if (hasChanged()) {
            preferences.reload()
            return true
        }
        return false
    }

    @Deprecated("Deprecated in XSharedPreferences", level = DeprecationLevel.ERROR)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        throw NotImplementedError()
    }

    @Deprecated("Deprecated in XSharedPreferences", level = DeprecationLevel.ERROR)
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        throw NotImplementedError()
    }
}