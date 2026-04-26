/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import io.github.proify.lyricon.lyric.model.Song

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