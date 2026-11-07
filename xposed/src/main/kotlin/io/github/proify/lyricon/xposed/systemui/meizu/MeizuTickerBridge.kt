/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.meizu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import io.github.proify.lyricon.common.rom.Flyme
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.BuildConfig
import io.github.proify.lyricon.xposed.systemui.lyric.LyricDataHub
import io.github.proify.lyricon.xposed.systemui.lyric.LyricPrefs
import io.github.proify.lyricon.xposed.systemui.lyric.control.ControlUi

/**
 * Flyme / Meizu 原生状态栏 Ticker 歌词桥接（第一切片）。
 *
 * 通过广播 `com.meizu.flyme.ticker.ACTION_SEND/CLEAR` 推送到 SystemUI，
 * 并可选投递带 `FLAG_ALWAYS_SHOW_TICKER` / `FLAG_ONLY_UPDATE_TICKER` 的通知。
 *
 * 延后：TTML 逐行同步、翻译 extras、PlaybackTickerState、heads-up、无根 Central。
 */
object MeizuTickerBridge : ActivePlayerListener {

    private const val TAG = "MeizuTickerBridge"

    private const val CHANNEL_ID = "lyricon_flyme_ticker_lyrics_v1"
    private const val NOTIFICATION_ID = 0x4C59434F // "LYCO"
    private const val FLAG_ALWAYS_SHOW_TICKER_FALLBACK = 0x1000000
    private const val FLAG_ONLY_UPDATE_TICKER_FALLBACK = 0x2000000
    private const val ACTION_SEND_LYRIC = "com.meizu.flyme.ticker.ACTION_SEND"
    private const val ACTION_CLEAR_LYRIC = "com.meizu.flyme.ticker.ACTION_CLEAR"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var enabled = false

    private var lastPayload: String? = null

    private val notificationManager: NotificationManager?
        get() = appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val flagAlwaysShowTicker: Int by lazy {
        getNotificationFlag("FLAG_ALWAYS_SHOW_TICKER")
    }

    private val flagOnlyUpdateTicker: Int by lazy {
        getNotificationFlag("FLAG_ONLY_UPDATE_TICKER")
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        refreshEnabledFromPrefs()
        LyricDataHub.addListener(this)
        Log.i(TAG, "initialized flyme=${Flyme.isFlymeDevice} enabled=$enabled")
    }

    fun refreshEnabledFromPrefs() {
        val shouldEnable = LyricPrefs.baseStyle.meizuTickerEnabled
        setEnabled(shouldEnable)
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) {
            if (!enabled) clearLyric()
            return
        }
        this.enabled = enabled
        if (!enabled) {
            clearLyric()
        } else {
            lastPayload = null
        }
        Log.i(TAG, "setEnabled=$enabled")
    }

    fun sendLyric(text: String?) {
        if (!enabled) return
        val context = appContext ?: return
        if (text.isNullOrEmpty()) {
            clearLyric()
            return
        }
        if (text == lastPayload) return
        lastPayload = text

        try {
            val intent = Intent(ACTION_SEND_LYRIC).apply {
                putExtra("ticker_text", text)
                putExtra("lyric", text)
                putExtra("text", text)
                putExtra("content", text)
                putExtra("ticker_package", BuildConfig.APP_PACKAGE_NAME)
                putExtra("package", BuildConfig.APP_PACKAGE_NAME)
                putExtra("ticker_app_name", "Lyricon")
                putExtra("app_name", "Lyricon")
            }
            sendFlymeBroadcast(context, intent)
            postTickerNotification(context, text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ticker lyric", e)
        }
    }

    fun clearLyric() {
        lastPayload = null
        val context = appContext ?: return
        try {
            val intent = Intent(ACTION_CLEAR_LYRIC).apply {
                putExtra("ticker_package", BuildConfig.APP_PACKAGE_NAME)
                putExtra("package", BuildConfig.APP_PACKAGE_NAME)
            }
            sendFlymeBroadcast(context, intent)
            notificationManager?.cancel(NOTIFICATION_ID)
            notificationManager?.cancel(null, NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun sendFlymeBroadcast(context: Context, intent: Intent) {
        context.sendBroadcast(intent)
        context.sendBroadcast(Intent(intent).setPackage(SYSTEM_UI_PACKAGE))
    }

    @Suppress("DEPRECATION")
    private fun postTickerNotification(context: Context, text: String) {
        val manager = notificationManager ?: return
        ensureNotificationChannel(context, manager)

        val flymeTickerSupported = isFlymeTickerSupported()
        val tickerText: CharSequence? = if (flymeTickerSupported) text else null
        val smallIcon = resolveSmallIcon(context)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(smallIcon)
            .setContentTitle(text)
            .setContentText("")
            .setTicker(tickerText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setDefaults(0)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR

        if (flymeTickerSupported) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                notification.extras.putBoolean("ticker_icon_switch", false)
            }
            notification.flags = notification.flags or flagAlwaysShowTicker
            notification.flags = notification.flags or flagOnlyUpdateTicker
        } else {
            Log.w(TAG, "Flyme ticker flags using fallback values or non-Flyme device")
        }

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun resolveSmallIcon(context: Context): Icon {
        return runCatching {
            val moduleRes = ControlUi.moduleResources(context)
            val resId = moduleRes.getIdentifier(
                "ic_flyme_ticker",
                "drawable",
                BuildConfig.APP_PACKAGE_NAME
            )
            if (resId != 0) {
                Icon.createWithResource(BuildConfig.APP_PACKAGE_NAME, resId)
            } else {
                Icon.createWithResource(context, android.R.drawable.ic_media_play)
            }
        }.getOrElse {
            Icon.createWithResource(context, android.R.drawable.ic_media_play)
        }
    }

    private fun ensureNotificationChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Flyme 状态栏歌词",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于向 Flyme 状态栏推送歌词"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun isFlymeTickerSupported(): Boolean {
        return Flyme.isFlymeDevice && flagAlwaysShowTicker > 0 && flagOnlyUpdateTicker > 0
    }

    private fun getNotificationFlag(name: String): Int {
        return try {
            val field = Notification::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.getInt(null)
        } catch (_: Throwable) {
            when (name) {
                "FLAG_ALWAYS_SHOW_TICKER" -> FLAG_ALWAYS_SHOW_TICKER_FALLBACK
                "FLAG_ONLY_UPDATE_TICKER" -> FLAG_ONLY_UPDATE_TICKER_FALLBACK
                else -> 0
            }.also { fallback ->
                Log.w(TAG, "Flyme ticker flag not found: $name, fallback=$fallback")
            }
        }
    }

    // --- ActivePlayerListener: first slice wires text path + pause/clear ---

    override fun onReceiveText(text: String?) {
        if (text.isNullOrBlank()) clearLyric() else sendLyric(text)
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (!isPlaying) clearLyric()
    }

    override fun onSongChanged(song: Song?) {
        // Timed line sync is deferred; leave/enter song mode clears ticker text.
        clearLyric()
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        clearLyric()
    }

    override fun onPositionChanged(position: Long) = Unit

    override fun onSeekTo(position: Long) = Unit

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
}
