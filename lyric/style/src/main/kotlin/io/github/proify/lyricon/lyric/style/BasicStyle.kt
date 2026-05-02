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
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 歌词基础样式配置类
 * 负责歌词显示位置、边距、隐藏规则以及简繁体转换等基础逻辑
 */
@Serializable
@Parcelize
data class BasicStyle(
    var anchor: String = Defaults.ANCHOR,
    var insertionOrder: Int = Defaults.INSERTION_ORDER,
    var width: Float = Defaults.WIDTH,
    var widthInColorOSCapsuleMode: Float = Defaults.WIDTH_IN_COLOROS_CAPSULE_MODE,
    var dynamicWidthEnabled: Boolean = Defaults.DYNAMIC_WIDTH_ENABLED,
    var dynamicWidthAutoHideClock: Boolean = Defaults.DYNAMIC_WIDTH_AUTO_HIDE_CLOCK,
    var doubleTapSwitchClock: Boolean = Defaults.DOUBLE_TAP_SWITCH_CLOCK,
    var margins: RectF = Defaults.MARGINS,
    var paddings: RectF = Defaults.PADDINGS,
    var visibilityRules: List<VisibilityRule> = Defaults.VISIBILITY_RULES,
    var hideOnLockScreen: Boolean = Defaults.HIDE_ON_LOCK_SCREEN,
    var noLyricHideTimeout: Int = Defaults.NO_LYRIC_HIDE_TIMEOUT,
    var noUpdateHideTimeout: Int = Defaults.NO_UPDATE_HIDE_TIMEOUT,
    var keywordHideTimeout: Int = Defaults.KEYWORD_HIDE_TIMEOUT,
    var keywordHideMatches: List<String> = Defaults.KEYWORD_HIDE_MATCH,
    var blockedWordsRegexString: String = Defaults.BLOCKED_WORDS_REGEX,
    var chineseConversionMode: Int = Defaults.CHINESE_CONVERSION_MODE,
) : AbstractStyle(), Parcelable {

    /** 缓存的屏蔽词正则表达式对象 */
    @IgnoredOnParcel
    @Transient
    var blockedWordsRegex: Regex? = null
        get() = if (field == null) {
            field = try {
                if (blockedWordsRegexString.isNotBlank())
                    Regex(blockedWordsRegexString) else null
            } catch (_: Exception) {
                null
            }
            field
        } else {
            field
        }

    /** 缓存的关键语隐藏正则表达式列表 */
    @IgnoredOnParcel
    @Transient
    var keywordsHidePattern: List<Regex>? = null
        get() = if (field == null) {
            field = keywordHideMatches.mapNotNull {
                runCatching { Regex(it) }.getOrNull()
            }
            field
        } else {
            field
        }

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
        dynamicWidthEnabled = preferences.getBoolean(
            "lyric_style_base_dynamic_width_enabled",
            Defaults.DYNAMIC_WIDTH_ENABLED
        )
        dynamicWidthAutoHideClock = preferences.getBoolean(
            "lyric_style_base_dynamic_width_auto_hide_clock",
            Defaults.DYNAMIC_WIDTH_AUTO_HIDE_CLOCK
        )
        doubleTapSwitchClock = preferences.getBoolean(
            "lyric_style_base_double_tap_switch_clock",
            Defaults.DOUBLE_TAP_SWITCH_CLOCK
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
            preferences.getString(
                "lyric_style_base_visibility_rules",
                "[]"
            ), Defaults.VISIBILITY_RULES.toMutableList()
        )

        hideOnLockScreen = preferences.getBoolean(
            "lyric_style_base_hide_on_lock_screen",
            Defaults.HIDE_ON_LOCK_SCREEN
        )
        noLyricHideTimeout = preferences.getInt(
            "lyric_style_base_no_lyric_hide_timeout",
            Defaults.NO_LYRIC_HIDE_TIMEOUT
        )
        noUpdateHideTimeout = preferences.getInt(
            "lyric_style_base_no_update_hide_timeout",
            Defaults.NO_UPDATE_HIDE_TIMEOUT
        )
        keywordHideTimeout = preferences.getInt(
            "lyric_style_base_keyword_hide_timeout",
            Defaults.KEYWORD_HIDE_TIMEOUT
        )

        preferences.getString("lyric_style_base_timeout_hide_keywords", null)?.let {
            keywordHideMatches = json.safeDecode<List<String>>(it, emptyList())
            keywordsHidePattern = null
        }

        blockedWordsRegexString = preferences.getString(
            "lyric_style_base_blocked_words_regex",
            Defaults.BLOCKED_WORDS_REGEX
        ) ?: Defaults.BLOCKED_WORDS_REGEX
        blockedWordsRegex = null

        chineseConversionMode = preferences.getInt(
            "lyric_style_base_chinese_conversion_mode",
            Defaults.CHINESE_CONVERSION_MODE
        )
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putString("lyric_style_base_anchor", anchor)
        editor.putInt("lyric_style_base_insertion_order", insertionOrder)
        editor.putFloat("lyric_style_base_width", width)
        editor.putFloat("lyric_style_base_width_in_coloros_capsule_mode", widthInColorOSCapsuleMode)
        editor.putBoolean("lyric_style_base_dynamic_width_enabled", dynamicWidthEnabled)
        editor.putBoolean(
            "lyric_style_base_dynamic_width_auto_hide_clock",
            dynamicWidthAutoHideClock
        )
        editor.putBoolean(
            "lyric_style_base_double_tap_switch_clock",
            doubleTapSwitchClock
        )
        editor.putString("lyric_style_base_margins", margins.toJson())
        editor.putString("lyric_style_base_paddings", paddings.toJson())
        editor.putString("lyric_style_base_visibility_rules", visibilityRules.toJson())
        editor.putBoolean("lyric_style_base_hide_on_lock_screen", hideOnLockScreen)
        editor.putInt("lyric_style_base_no_lyric_hide_timeout", noLyricHideTimeout)
        editor.putInt("lyric_style_base_no_update_hide_timeout", noUpdateHideTimeout)
        editor.putInt("lyric_style_base_keyword_hide_timeout", keywordHideTimeout)
        editor.putString("lyric_style_base_timeout_hide_keywords", keywordHideMatches.toJson())
        editor.putString("lyric_style_base_blocked_words_regex", blockedWordsRegexString)

        // 写入中文转换配置
        editor.putInt("lyric_style_base_chinese_conversion_mode", chineseConversionMode)
    }

    object Defaults {
        const val ANCHOR: String = "clock"
        const val INSERTION_ORDER: Int = INSERTION_ORDER_BEFORE
        const val WIDTH: Float = 100f
        const val WIDTH_IN_COLOROS_CAPSULE_MODE: Float = 70f
        const val DYNAMIC_WIDTH_ENABLED: Boolean = false
        const val DYNAMIC_WIDTH_AUTO_HIDE_CLOCK: Boolean = false
        const val DOUBLE_TAP_SWITCH_CLOCK: Boolean = false
        val MARGINS: RectF = RectF()
        val PADDINGS: RectF = RectF()
        val VISIBILITY_RULES: List<VisibilityRule> = emptyList()
        const val HIDE_ON_LOCK_SCREEN: Boolean = true
        const val NO_LYRIC_HIDE_TIMEOUT: Int = 0
        const val NO_UPDATE_HIDE_TIMEOUT: Int = 0
        const val KEYWORD_HIDE_TIMEOUT: Int = 0
        val KEYWORD_HIDE_MATCH: List<String> = listOf()
        const val BLOCKED_WORDS_REGEX: String = ""
        const val CHINESE_CONVERSION_MODE: Int = CHINESE_CONVERSION_OFF
    }

    companion object {
        const val INSERTION_ORDER_BEFORE: Int = 0
        const val INSERTION_ORDER_AFTER: Int = 1

        /** 中文转换模式：关闭 */
        const val CHINESE_CONVERSION_OFF = 0

        /** 中文转换模式：简体中文 */
        const val CHINESE_CONVERSION_SIMPLIFIED = 1

        /** 中文转换模式：繁体中文 */
        const val CHINESE_CONVERSION_TRADITIONAL = 2
    }
}
