/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.android.extensions.rom

import android.annotation.SuppressLint

object ColorOS {

    @SuppressLint("PrivateApi")
    fun isSupportCapsule(classLoader: ClassLoader): Boolean = try {
        classLoader.loadClass("com.android.systemui.plugins.statusbar.CapsulePlugin") != null
    } catch (_: Exception) {
        false
    }
}