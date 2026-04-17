/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine

data class RichLyricLineModel(val source: IRichLyricLine) : IRichLyricLine by source {
    var previous: RichLyricLineModel? = null
    var next: RichLyricLineModel? = null

    override fun hashCode(): Int {
        return source.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RichLyricLineModel) return false

        if (isTitleLine() && other.isTitleLine()) {
            return text == other.text
                    && words == other.words
                    && secondaryWords == other.secondaryWords
                    && translationWords == other.translationWords
                    && isAlignedRight == other.isAlignedRight
                    && metadata == other.metadata
        }
        return source == other.source
    }
}