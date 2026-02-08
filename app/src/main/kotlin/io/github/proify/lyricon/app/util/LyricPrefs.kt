/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.content.SharedPreferences
import io.github.proify.android.extensions.fromJson
import io.github.proify.android.extensions.getWorldReadableSharedPreferences
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.bridge.AppBridge.LyricStylePrefs
import io.github.proify.lyricon.app.bridge.AppBridge.LyricStylePrefs.KEY_ENABLED_PACKAGES
import io.github.proify.lyricon.lyric.style.VisibilityRule

/**
 * 歌词样式偏好管理工具类。
 *
 * 提供全局基础样式、包级样式及可视化规则的持久化读写接口。
 * 使用 [SharedPreferences] 存储数据，并支持 JSON 序列化。
 */
object LyricPrefs {

    /** 默认应用包名 */
    const val DEFAULT_PACKAGE_NAME: String = LyricStylePrefs.DEFAULT_PACKAGE_NAME

    /** 已配置的包名存储键 */
    private const val KEY_CONFIGURED_PACKAGES: String = "configured"

    /** 管理包级样式配置的 SharedPreferences */
    private val packageStyleManager: SharedPreferences =
        getSharedPreferences(LyricStylePrefs.PREF_PACKAGE_STYLE_MANAGER)

    /** 基础样式偏好 SharedPreferences */
    val basicStylePrefs: SharedPreferences
        get() = getSharedPreferences(LyricStylePrefs.PREF_NAME_BASE_STYLE)

    /** 获取指定名称的 SharedPreferences*/
    fun getSharedPreferences(name: String): SharedPreferences {
        return LyriconApp.instance.getWorldReadableSharedPreferences(name)
    }

    /** 获取指定包名对应的样式配置偏好名称 */
    fun getPackagePrefName(packageName: String): String =
        LyricStylePrefs.getPackageStylePreferenceName(packageName)

    /** 设置启用的包名集合 */
    fun setEnabledPackageNames(names: Set<String>) {
        packageStyleManager.editCommit {
            putStringSet(KEY_ENABLED_PACKAGES, names)
        }
    }

    /** 获取启用的包名集合 */
    fun getEnabledPackageNames(): Set<String> {
        return packageStyleManager
            .getStringSet(KEY_ENABLED_PACKAGES, null)?.toSet() ?: emptySet()
    }

    /** 设置已配置的包名集合 */
    fun setConfiguredPackageNames(names: Set<String>) {
        packageStyleManager.editCommit {
            putString(KEY_CONFIGURED_PACKAGES, names.toJson())
        }
    }

    /** 获取已配置的包名集合 */
    fun getConfiguredPackageNames(): Set<String> {
        val jsonData = packageStyleManager.getString(KEY_CONFIGURED_PACKAGES, null)
        return json.safeDecode<List<String>>(jsonData).toSet()
    }

    /** 设置歌词显示可视化规则 */
    fun setViewVisibilityRule(rules: List<VisibilityRule>?) {
        basicStylePrefs.editCommit {
            if (rules.isNullOrEmpty()) {
                remove("lyric_style_base_visibility_rules")
            } else {
                putString("lyric_style_base_visibility_rules", rules.toJson())
            }
        }
    }

    /** 获取歌词显示可视化规则 */
    fun getViewVisibilityRule(): List<VisibilityRule> {
        val json = basicStylePrefs.getString("lyric_style_base_visibility_rules", null)
        return json?.fromJson<List<VisibilityRule>>() ?: emptyList()
    }

    fun setLyricTextBlacklist(texts: List<String>?) {
        val normalized =
            texts.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        basicStylePrefs.editCommit {
            if (normalized.isEmpty()) {
                remove("lyric_style_base_lyric_text_blacklist")
            } else {
                putString("lyric_style_base_lyric_text_blacklist", normalized.toJson())
            }
        }
    }

    fun getLyricTextBlacklist(): List<String> {
        val jsonString = basicStylePrefs.getString("lyric_style_base_lyric_text_blacklist", null)
        return json.safeDecode<List<String>>(jsonString).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
