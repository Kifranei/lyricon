/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

data class PlayerRecorder(val providerInfo: ProviderInfo) {

    @Volatile
    var song: Song? = null
        set(value) {
            field = value
            lyricType = LyricType.SONG
        }

    @Volatile
    var isPlaying: Boolean = false

    @Volatile
    var position: Long = -1

    @Volatile
    var text: String? = null
        set(value) {
            field = value
            lyricType = LyricType.TEXT
        }

    @Volatile
    var isDisplayTranslation: Boolean = false

    @Volatile
    var isDisplayRoma = false

    @Volatile
    var lyricType: LyricType = LyricType.NONE
        private set

    enum class LyricType {
        NONE,
        SONG,
        TEXT
    }
}