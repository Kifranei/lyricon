/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

interface ActivePlayerListener {
    fun onActiveProviderChanged(providerInfo: ProviderInfo?)
    fun onSongChanged(song: Song?)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onPositionChanged(position: Long)
    fun onSeekTo(position: Long)
    fun onSendText(text: String?)
    fun onDisplayTranslationChanged(isDisplayTranslation: Boolean)
    fun onDisplayRomaChanged(displayRoma: Boolean)
}