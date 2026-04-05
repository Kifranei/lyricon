/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

data class ThemeColors(
    val primary: Int,
    val swatches: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ThemeColors

        if (primary != other.primary) return false
        if (!swatches.contentEquals(other.swatches)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primary
        result = 31 * result + swatches.contentHashCode()
        return result
    }
}