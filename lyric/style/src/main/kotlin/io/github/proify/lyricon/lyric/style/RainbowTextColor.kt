/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class RainbowTextColor(
    var normal: IntArray = intArrayOf(),
    var background: IntArray = intArrayOf(),
    var highlight: IntArray = intArrayOf()
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RainbowTextColor

        if (!normal.contentEquals(other.normal)) return false
        if (!background.contentEquals(other.background)) return false
        if (!highlight.contentEquals(other.highlight)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = normal.contentHashCode()
        result = 31 * result + background.contentHashCode()
        result = 31 * result + highlight.contentHashCode()
        return result
    }
}