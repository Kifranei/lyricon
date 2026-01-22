/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.hook.systemui

import android.annotation.SuppressLint
import android.os.Looper
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.xposed.lyricview.LyricView
import io.github.proify.lyricon.xposed.util.LyricPrefs
import io.github.proify.lyricon.xposed.util.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.util.StatusColor

/**
 * 歌词视图控制器。
 * 协调活跃播放器状态与 SystemUI 歌词视图之间的同步，
 * 自动处理跨线程调度并监听状态栏颜色变化。
 */
object LyricViewController : ActivePlayerListener,
    StatusBarColorMonitor.OnColorChangeListener {

    /** 获取当前是否处于播放状态 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 获取当前活跃播放器的包名 */
    @Volatile
    var activePackage: String = ""
        private set

    @SuppressLint("StaticFieldLeak")
    var statusBarViewManager: StatusBarViewManager? = null

   var providerInfo: ProviderInfo? = null
    private  set

    init {
        StatusBarColorMonitor.register(this)
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        this.providerInfo = providerInfo
        if (DEBUG) YLog.debug("activeProviderChanged: $providerInfo")

        val packageName = providerInfo?.playerPackageName
        activePackage = packageName.orEmpty()
        LyricPrefs.activePackageName = packageName

        runOnUi { view ->
            if (providerInfo == null) {
                view.logoView.providerLogo = null
                statusBarViewManager?.updateLyricStyle(LyricPrefs.getLyricStyle())
                view.updateSong(null)
                view.setPlaying(false)
            } else {
                view.logoView.providerLogo = providerInfo.logo
                val style = LyricPrefs.getLyricStyle()
                statusBarViewManager?.updateLyricStyle(style)
                view.updateVisibility()
            }
        }
    }

    override fun onSongChanged(song: Song?) {
        runOnUi { it.updateSong(song) }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        runOnUi { it.setPlaying(isPlaying) }
    }

    override fun onPositionChanged(position: Long) {
        runOnUi { it.updatePosition(position) }
    }

    override fun onSeekTo(position: Long) {
        runOnUi { it.seekTo(position) }
    }

    override fun onSendText(text: String?) {
        runOnUi { it.updateText(text) }
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        runOnUi { it.setDisplayTranslation(isDisplayTranslation) }
    }

    override fun onColorChange(color: StatusColor) {
        runOnUi { it.onColorChange(color) }
    }

    private inline fun runOnUi(crossinline action: (LyricView) -> Unit) {
        val manager = statusBarViewManager ?: return
        val view = manager.lyricView

        if (!view.isAttachedToWindow) return

        if (Looper.myLooper() == Looper.getMainLooper()) {
            performActionSafely(view, action)
        } else {
            view.post {
                performActionSafely(view, action)
            }
        }
    }

    /**
     * 在 UI 线程安全地执行动作并捕获异常。
     */
    private inline fun performActionSafely(view: LyricView, action: (LyricView) -> Unit) {
        try {
            if (view.isAttachedToWindow) action(view)
        } catch (t: Throwable) {
            YLog.error("LyricViewController: UI action execution failed", t)
        }
    }

    private val DEBUG get() = Constants.isDebug
}