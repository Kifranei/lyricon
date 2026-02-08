/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.content.SharedPreferences
import android.os.Parcelable
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class BasicStyle(
    var anchor: String = Defaults.ANCHOR,
    var insertionOrder: Int = Defaults.INSERTION_ORDER,
    var width: Float = Defaults.WIDTH,
    var widthInColorOSCapsuleMode: Float = Defaults.WIDTH_IN_COLOROS_CAPSULE_MODE,
    var margins: RectF = Defaults.MARGINS,
    var paddings: RectF = Defaults.PADDINGS,
    var visibilityRules: List<VisibilityRule> = Defaults.VISIBILITY_RULES,
    var hideOnLockScreen: Boolean = Defaults.HIDE_ON_LOCK_SCREEN,
    var hideWhenNoLyricAfterSeconds: Int = Defaults.HIDE_WHEN_NO_LYRIC_AFTER_SECONDS,
    var lyricTextBlacklist: List<String> = Defaults.LYRIC_TEXT_BLACKLIST,
) : AbstractStyle(), Parcelable {

    override fun onLoad(preferences: SharedPreferences) {
        anchor =
            preferences.getString("lyric_style_base_anchor", Defaults.ANCHOR) ?: Defaults.ANCHOR
        insertionOrder =
            preferences.getInt("lyric_style_base_insertion_order", Defaults.INSERTION_ORDER)
        width = preferences.getFloat("lyric_style_base_width", Defaults.WIDTH)
        widthInColorOSCapsuleMode = preferences.getFloat(
            "lyric_style_base_width_in_coloros_capsule_mode",
            Defaults.WIDTH_IN_COLOROS_CAPSULE_MODE
        )

        margins = json.safeDecode<RectF>(
            preferences.getString("lyric_style_base_margins", null),
            Defaults.MARGINS
        )
        paddings = json.safeDecode<RectF>(
            preferences.getString("lyric_style_base_paddings", null),
            Defaults.PADDINGS
        )
        visibilityRules = json.safeDecode<MutableList<VisibilityRule>>(
            preferences.getString("lyric_style_base_visibility_rules", null),
            Defaults.VISIBILITY_RULES.toMutableList()
        )
        hideOnLockScreen = preferences.getBoolean(
            "lyric_style_base_hide_on_lock_screen",
            Defaults.HIDE_ON_LOCK_SCREEN
        )
        hideWhenNoLyricAfterSeconds = preferences.getInt(
            "lyric_style_base_hide_when_no_lyric_after_seconds",
            Defaults.HIDE_WHEN_NO_LYRIC_AFTER_SECONDS
        )
        lyricTextBlacklist = json.safeDecode<MutableList<String>>(
            preferences.getString("lyric_style_base_lyric_text_blacklist", null),
            Defaults.LYRIC_TEXT_BLACKLIST.toMutableList()
        )
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putString("lyric_style_base_anchor", anchor)
        editor.putInt("lyric_style_base_insertion_order", insertionOrder)
        editor.putFloat("lyric_style_base_width", width)
        editor.putFloat("lyric_style_base_width_in_coloros_capsule_mode", widthInColorOSCapsuleMode)
        editor.putString("lyric_style_base_margins", margins.toJson())
        editor.putString("lyric_style_base_paddings", paddings.toJson())
        editor.putString("lyric_style_base_visibility_rules", visibilityRules.toJson())
        editor.putBoolean("lyric_style_base_hide_on_lock_screen", hideOnLockScreen)
        editor.putInt("lyric_style_base_hide_when_no_lyric_after_seconds", hideWhenNoLyricAfterSeconds)
        editor.putString("lyric_style_base_lyric_text_blacklist", lyricTextBlacklist.toJson())
    }

    object Defaults {
        const val HIDE_ON_LOCK_SCREEN: Boolean = true
        const val ANCHOR: String = "clock"
        const val INSERTION_ORDER: Int = INSERTION_ORDER_BEFORE
        const val WIDTH: Float = 100f
        const val WIDTH_IN_COLOROS_CAPSULE_MODE: Float = 70f
        val MARGINS: RectF = RectF()
        val PADDINGS: RectF = RectF()
        val VISIBILITY_RULES: List<VisibilityRule> = emptyList()
        const val HIDE_WHEN_NO_LYRIC_AFTER_SECONDS: Int = 0
        val LYRIC_TEXT_BLACKLIST: List<String> = emptyList()
    }

    companion object {
        const val INSERTION_ORDER_BEFORE: Int = 0
        const val INSERTION_ORDER_AFTER: Int = 1
    }
}
