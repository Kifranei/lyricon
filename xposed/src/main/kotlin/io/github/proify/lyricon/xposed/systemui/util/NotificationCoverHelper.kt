/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import android.app.AndroidAppHelper
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.proify.android.extensions.saveTo
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.xposed.systemui.Directory
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

object NotificationCoverHelper {
    private var unhooks: MutableSet<XC_MethodHook.Unhook>? = null
    private val listeners = CopyOnWriteArrayList<OnCoverUpdateListener>()
    private const val COVER_FILE_NAME = "cover.png"

    private val NOTIFICATION_LISTENER_CLASS_CANDIDATES = arrayOf(
        "com.android.systemui.statusbar.notification.MiuiNotificationListener",
        "com.android.systemui.statusbar.NotificationListener",
    )

    fun registerListener(listener: OnCoverUpdateListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: OnCoverUpdateListener) {
        listeners.remove(listener)
    }

    private fun findNotificationListenerClass(classLoader: ClassLoader): Class<*>? {
        for (className in NOTIFICATION_LISTENER_CLASS_CANDIDATES) {
            try {
                return classLoader.loadClass(className)
            } catch (_: ClassNotFoundException) {
                // ignored
            }
        }
        return null
    }

    fun initialize(classLoader: ClassLoader) {
        unhooks?.forEach { it.unhook() }

        val listenerClass = findNotificationListenerClass(classLoader)
        if (listenerClass == null) {
            YLog.error("未找到通知监听器类,无法 Hook 通知")
            return
        }

        try {
            unhooks = XposedBridge.hookAllMethods(
                listenerClass,
                "onNotificationPosted",
                NotificationPostedHook()
            )
        } catch (e: Throwable) {
            YLog.error("Hook 通知监听器失败", e)
        }
    }

    fun getCoverFile(packageName: String): File =
        File(Directory.getPackageDataDir(packageName), COVER_FILE_NAME)

    fun interface OnCoverUpdateListener {
        fun onCoverUpdated(packageName: String, coverFile: File)
    }

    private class NotificationPostedHook : XC_MethodHook() {

        override fun afterHookedMethod(param: MethodHookParam) {
            extractAndSaveCover(param)
        }

        private fun extractAndSaveCover(param: MethodHookParam) {
            val args = param.args

            val statusBarNotification = args[0] as? StatusBarNotification ?: return

            val packageName = statusBarNotification.packageName
            val notification: Notification = statusBarNotification.notification

            if (!isMediaStyle(notification)) return

//            for (key in notification.extras.keySet()) {
//                val value = notification.extras.get(key)
//                Log.d("NotificationExtras", "$key = $value")
//            }

            val icon: Icon = notification.getLargeIcon() ?: return

            saveCoverIcon(icon, packageName)
        }

        fun isMediaStyle(n: Notification): Boolean {
            val e = n.extras
            return e.containsKey(Notification.EXTRA_MEDIA_SESSION)
                    || e.containsKey("android.media.metadata.ALBUM_ART")
                    || e.containsKey("android.media.metadata.ART")
        }

        private fun saveCoverIcon(icon: Icon, packageName: String) {
            val context: Context? = AndroidAppHelper.currentApplication()
            if (context == null) {
                YLog.warn("无法获取上下文")
                return
            }

            val drawable = icon.loadDrawable(context)
            if (drawable == null) {
                YLog.warn("无法加载封面图标")
                return
            }

            val bitmap: Bitmap = drawable.toBitmap()
            val coverFile = getCoverFile(packageName)

            bitmap.saveTo(coverFile)

            for (listener in listeners) {
                listener.onCoverUpdated(packageName, coverFile)
            }
        }
    }
}