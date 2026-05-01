/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.app.AndroidAppHelper
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.common.StateSharedPreferences
import io.github.proify.lyricon.common.ensureLatest
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.PackageStyle

object LyricPrefs {

    private val prefsCache = mutableMapOf<String, StateSharedPreferences>()
    private val packageStyleCache = mutableMapOf<String, PackageStyleCache>()

    @Volatile
    var activePackageName: String? = null

    /* ---------------- base style ---------------- */

    private val baseStylePrefs: StateSharedPreferences =
        createPrefs(AppBridge.LyricStylePrefs.PREF_NAME_BASE_STYLE)

    val baseStyle: BasicStyle = BasicStyle().apply {
        load(baseStylePrefs)
    }
        get() {
            if (baseStylePrefs.hasChanged()) {
                baseStylePrefs.reload()
                field.load(baseStylePrefs)
            }
            return field
        }

    /* ---------------- default package style ---------------- */

    private val defaultPackageStylePrefs: StateSharedPreferences by lazy {
        getPackagePrefs(
            AppBridge.LyricStylePrefs.DEFAULT_PACKAGE_NAME
        )
    }

    val defaultPackageStyle: PackageStyle = PackageStyle().apply {
        load(defaultPackageStylePrefs)
    }
        get() {
            if (defaultPackageStylePrefs.hasChanged()) {
                defaultPackageStylePrefs.reload()
                field.load(defaultPackageStylePrefs)
            }
            return field
        }

    /* ---------------- package manager ---------------- */

    private val packageStyleManagerPrefs: StateSharedPreferences =
        createPrefs(AppBridge.LyricStylePrefs.PREF_PACKAGE_STYLE_MANAGER)
        get() {
            return field.ensureLatest()
        }

    val activePackageStyle
        get() = run {
            val pkg = activePackageName
            if (pkg != null && isPackageEnabled(pkg)) {
                getPackageStyle(pkg)
            } else {
                defaultPackageStyle
            }
        }

    private fun isPackageEnabled(packageName: String): Boolean {
        packageStyleManagerPrefs.ensureLatest()
        return runCatching {
            packageStyleManagerPrefs
                .getStringSet(
                    AppBridge.LyricStylePrefs.KEY_ENABLED_PACKAGES,
                    emptySet()
                )
                ?.contains(packageName) ?: false
        }.getOrDefault(false)
    }

    /* ---------------- prefs cache ---------------- */

    private fun getPackagePrefName(packageName: String): String =
        AppBridge.LyricStylePrefs.getPackageStylePreferenceName(packageName)

    private fun getPackagePrefs(packageName: String): StateSharedPreferences {
        val prefName = getPackagePrefName(packageName)
        return prefsCache.getOrPut(prefName) {
            createPrefs(prefName)
        }
    }

    private fun createPrefs(name: String): StateSharedPreferences {
        return AppBridge.getSharedPreferences(AndroidAppHelper.currentApplication(), name)
    }

    /* ---------------- package style cache ---------------- */

    private class PackageStyleCache(
        private val prefs: StateSharedPreferences,
        private val style: PackageStyle
    ) {
        fun getStyle(): PackageStyle {
            if (prefs.hasChanged()) {
                prefs.reload()
                style.load(prefs)
            }
            return style
        }
    }

    fun getPackageStyle(packageName: String): PackageStyle {
        return packageStyleCache.getOrPut(packageName) {
            val prefs = getPackagePrefs(packageName)
            val style = PackageStyle().apply {
                load(prefs)
            }
            PackageStyleCache(prefs, style)
        }.getStyle()
    }

    /* ---------------- lyric style ---------------- */

    fun getLyricStyle(packageName: String? = null): LyricStyle {
        if (packageName.isNullOrBlank()) {
            return LyricStyle(baseStyle, activePackageStyle)
        }
        return LyricStyle(
            baseStyle,
            getPackageStyle(packageName)
        )
    }
}