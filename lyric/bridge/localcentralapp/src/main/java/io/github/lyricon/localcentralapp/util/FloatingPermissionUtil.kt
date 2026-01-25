/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.lyricon.localcentralapp.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

object FloatingPermissionUtil {
    fun hasPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun requestPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}