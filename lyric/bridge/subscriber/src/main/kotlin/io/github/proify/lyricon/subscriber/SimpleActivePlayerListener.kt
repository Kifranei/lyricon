/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import io.github.proify.lyricon.lyric.model.Song

/**
 * [ActivePlayerListener] 的空实现适配器。
 *
 * 只关心部分事件时可实现该接口并覆写需要的方法，避免写出所有回调。
 */
interface SimpleActivePlayerListener : ActivePlayerListener {
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {}
    override fun onSongChanged(song: Song?) {}
    override fun onReceiveText(text: String?) {}
    override fun onPlaybackStateChanged(isPlaying: Boolean) {}
    override fun onPositionChanged(position: Long) {}
    override fun onSeekTo(position: Long) {}
    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {}
    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {}
}
