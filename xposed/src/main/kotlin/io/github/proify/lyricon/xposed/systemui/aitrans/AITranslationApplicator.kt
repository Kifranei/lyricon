/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.aitrans

import android.util.Log
import io.github.proify.lyricon.lyric.model.Song

/** Applies validated translation items back to lyric lines. */
internal object AITranslationApplicator {
    private const val TAG = "LyriconAITranslator"

    fun apply(song: Song, transItems: List<TranslationItem>): Song {
        var appliedCount = 0
        val translationsByIndex = transItems.associateBy { it.index }
        val newLyrics = song.lyrics?.mapIndexed { index, line ->
            val transText = translationsByIndex[index]?.tran?.trim()

            if (!transText.isNullOrBlank()
                && line.translation.isNullOrBlank()
                && transText.lowercase() != line.text?.trim()?.lowercase()
            ) {
                appliedCount++
                line.copy(translation = transText, translationWords = null)
            } else {
                line
            }
        }
        Log.v(TAG, "Applied $appliedCount translation lines to ${song.name}")
        return song.copy(lyrics = newLyrics)
    }
}