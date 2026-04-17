/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.processor

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle

class TranslationOnlyProcessor : PostProcessor {
    override fun isEnabled(style: LyricStyle): Boolean = style.packageStyle.text.isTranslationOnly

    override suspend fun process(
        song: Song,
        style: LyricStyle
    ): Song = applyTranslationOnly(song)

    private fun applyTranslationOnly(song: Song): Song {
        return song.copy(lyrics = song.lyrics?.map { line ->
            if (!line.translation.isNullOrBlank()) {
                line.copy(text = line.translation, translation = null)
            } else line
        })
    }
}