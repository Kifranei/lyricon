/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.media.session.MediaController
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.util.SystemUIMediaUtils

/**
 * 播放器控制入口 (Playback Control)
 *
 * 面向当前活跃播放器下发上一曲 / 下一曲 / 播放暂停指令,
 * 供状态栏歌词手势控制与歌词控制面板共同使用,保证行为一致。
 *
 * @author Tomakino
 * @since 2026
 */
object PlaybackControl {

    private const val TAG = "PlaybackControl"

    /** 上一曲 */
    fun previous() {
        transportControls()?.skipToPrevious()
    }

    /** 下一曲 */
    fun next() {
        transportControls()?.skipToNext()
    }

    /** 在播放与暂停之间切换 */
    fun togglePlay() {
        val controls = transportControls() ?: return
        if (LyricViewController.isPlaying) {
            controls.pause()
        } else {
            controls.play()
        }
    }

    private fun transportControls(): MediaController.TransportControls? {
        return try {
            SystemUIMediaUtils.getController(LyricViewController.activePackage)
                ?.transportControls
        } catch (e: Exception) {
            YLog.error(TAG, "Failed to obtain transport controls", e)
            null
        }
    }
}
