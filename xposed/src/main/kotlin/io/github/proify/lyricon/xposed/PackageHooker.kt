/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import android.app.Application

abstract class PackageHooker {
    private lateinit var packageHelper: PackageHelper

    val packageName: String get() = packageHelper.packageName
    val processName: String get() = packageHelper.processName
    val classLoader get() = packageHelper.classLoader
    val appContext get() = packageHelper.appInstance

    fun isMainProcess() = processName == packageName

    /**
     * 确保在Application创建时执行
     */
    fun doOnAppCreated(callback: (Application) -> Unit) {
        packageHelper.doOnAppCreated(callback)
    }

    fun onAttach(packageHelper: PackageHelper) {
        this.packageHelper = packageHelper
    }

    abstract fun onHook()
}