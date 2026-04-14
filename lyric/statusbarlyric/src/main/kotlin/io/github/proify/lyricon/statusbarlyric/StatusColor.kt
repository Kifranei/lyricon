/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.graphics.Color
import io.github.proify.android.extensions.setColorAlpha

data class StatusColor(
    var color: IntArray = intArrayOf(Color.BLACK),
    var translucentColor: IntArray = intArrayOf(Color.BLACK.setColorAlpha(0.5f)),
    var darkIntensity: Float = 0f,
) {
    val isLightMode: Boolean get() = darkIntensity < 0.5f

    fun firstColor(fallback: Int = Color.BLACK): Int = color.firstOrNull() ?: fallback

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StatusColor

        if (darkIntensity != other.darkIntensity) return false
        if (!color.contentEquals(other.color)) return false
        if (!translucentColor.contentEquals(other.translucentColor)) return false
        if (isLightMode != other.isLightMode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = darkIntensity.hashCode()
        result = 31 * result + color.contentHashCode()
        result = 31 * result + translucentColor.contentHashCode()
        result = 31 * result + isLightMode.hashCode()
        return result
    }
}