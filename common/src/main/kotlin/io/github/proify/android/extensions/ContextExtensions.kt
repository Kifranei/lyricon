/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.android.extensions

import android.content.Context
import android.content.SharedPreferences
import io.github.proify.lyricon.common.util.safe

fun Context.getPrivateSharedPreferences(name: String): SharedPreferences {
    return getSharedPreferences(name, Context.MODE_PRIVATE).safe()
}
//
///**
// * 尝试获取 world-readable 的 SharedPreferences
// */
//@SuppressLint("WorldReadableFiles")
//fun Context.getWorldReadableSharedPreferences(name: String): SharedPreferences = try {
//    @Suppress("DEPRECATION")
//    getSharedPreferences(name, Context.MODE_WORLD_READABLE).safe()
//} catch (_: Exception) {
//    getPrivateSharedPreferences(name)
//}
//
//@Suppress("unused")
//fun Context.getSharedPreferences(name: String, worldReadable: Boolean): SharedPreferences =
//    if (worldReadable) getWorldReadableSharedPreferences(name)
//    else getPrivateSharedPreferences(name)

val Context.defaultSharedPreferences: SharedPreferences
    get() = getPrivateSharedPreferences(defaultSharedPreferencesName)

val Context.defaultSharedPreferencesName: String get() = packageName + "_preferences"