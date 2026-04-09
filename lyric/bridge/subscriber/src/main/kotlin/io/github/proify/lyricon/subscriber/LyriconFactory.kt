/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.subscriber

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build

object LyriconFactory {

    /**
     * 创建 LyriconSubscriber 实例。
     *
     * @param context 上下文
     */
    fun createSubscriber(
        context: Context
    ): LyriconSubscriber {
        CentralServiceReceiver.initialize(context)

        val subscriberInfo = SubscriberInfo(
            context.packageName,
            getCurrentProcessName(context).orEmpty().ifBlank { context.packageName }
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            LyriconSubscriberImpl(context, subscriberInfo)
        } else {
            EmptyLyriconSubscriber(subscriberInfo)
        }
    }

    private fun getCurrentProcessName(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
    }
}