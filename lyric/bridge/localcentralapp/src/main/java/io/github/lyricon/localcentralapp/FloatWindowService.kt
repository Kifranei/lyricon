/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.lyricon.localcentralapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

class FloatWindowService : Service() {

    private var helper: FloatWindowHelper? = null
    private var isStarted = false
    private val activePlayerListener = MyActivePlayerListener()

    companion object {
        const val ACTION_TOGGLE: String = "io.github.lyricon.ACTION_TOGGLE"
        const val TAG = "FloatWindowService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        helper = FloatWindowHelper(this)
        initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                helper?.toggle()
                if (helper?.isShowing == true) {
                    moveToForeground()
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
        }
        return START_STICKY
    }

    private fun initialize() {
        if (isStarted) return
        isStarted = true
        BridgeCentral.initialize(applicationContext)
        ActivePlayerDispatcher.addActivePlayerListener(activePlayerListener)
        BridgeCentral.sendBootCompleted()
        Log.d(TAG, "Initialized BridgeCentral")
    }

    @SuppressLint("ForegroundServiceType")
    private fun moveToForeground() {
        val channelId = "float_window_channel"
        val channel =
            NotificationChannel(channelId, "歌词悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("歌词同步中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1001,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败: ${e.message}")
        }
    }

    inner class MyActivePlayerListener : ActivePlayerListener {

        private inline fun runInUiThread(crossinline block: () -> Unit) {
            helper?.floatingView?.post { block() }
        }

        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            Log.d(TAG, "onActiveProviderChanged: $providerInfo")

        }

        override fun onSongChanged(song: Song?) {
            Log.d(TAG, "onSongChanged: $song")
            runInUiThread {
                helper?.floatingView?.song = song?.normalize()
            }
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            Log.d(TAG, "onPlaybackStateChanged: $isPlaying")
        }

        override fun onPositionChanged(position: Long) {
            //Log.d(TAG, "onPositionChanged: $position")
            runInUiThread {
                helper?.floatingView?.setPosition(position)
            }
        }

        override fun onSeekTo(position: Long) {
            Log.d(TAG, "onSeekTo: $position")
            runInUiThread {
                helper?.floatingView?.seekTo(position)
            }
        }

        override fun onSendText(text: String?) {
            Log.d(TAG, "onSendText: $text")
            runInUiThread {
                helper?.floatingView?.text = text
            }
        }

        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
            Log.d(TAG, "onDisplayTranslationChanged: $isDisplayTranslation")
            runInUiThread {
                helper?.floatingView?.setDisplayTranslation(isDisplayTranslation)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ActivePlayerDispatcher.removeActivePlayerListener(activePlayerListener)
        helper?.dismiss()
        isStarted = false
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }
}