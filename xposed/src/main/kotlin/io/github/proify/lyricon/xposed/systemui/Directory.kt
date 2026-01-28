/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui

import android.content.Context
import de.robv.android.xposed.XSharedPreferences
import io.github.proify.lyricon.common.PackageNames
import java.io.File

object Directory {
    private lateinit var moduleDataDir: File
    private lateinit var tempDir: File
    private lateinit var packageDir: File

    val preferenceDirectory: File? by lazy {
        XSharedPreferences(PackageNames.APPLICATION, "137666").file.parentFile
    }

    fun initialize(context: Context) {
        val filesDir = context.filesDir
        moduleDataDir = File(filesDir, "lyricon")
        tempDir = File(moduleDataDir, ".temp")
        packageDir = File(moduleDataDir, "packages")
    }

    fun getPackageDataDir(packageName: String): File = File(packageDir, packageName)
}