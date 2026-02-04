/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.android.extensions
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.github.proify.lyricon.common.util.safe

fun Context.getPrivateSharedPreferences(name: String): SharedPreferences =
    getSharedPreferences(name, Context.MODE_PRIVATE).safe()

/**
 * 尝试获取 world-readable 的 SharedPreferences，失败则返回私有的
 */
@SuppressLint("WorldReadableFiles")
fun Context.getWorldReadableSharedPreferences(name: String): SharedPreferences = try {
    @Suppress("DEPRECATION") getSharedPreferences(name, Context.MODE_WORLD_READABLE).safe()
} catch (_: Exception) {
    getPrivateSharedPreferences(name)
}

fun Context.getSharedPreferences(name: String, worldReadable: Boolean): SharedPreferences =
    if (worldReadable) getWorldReadableSharedPreferences(name)
    else getPrivateSharedPreferences(name)

/**
 * 默认的 SharedPreferences
 *
 * 注意：`BackupManager`不会备份此SharedPreferences的设置
 */
val Context.defaultSharedPreferences: SharedPreferences
    get() = getWorldReadableSharedPreferences(defaultSharedPreferencesName)

val Context.defaultSharedPreferencesName: String get() = packageName + "_preferences"