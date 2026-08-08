/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.view.ViewGroup
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine

internal class ViewSlotEngine(
    private val container: ViewGroup,
) {
    val toRemove = mutableListOf<RichLyricLineView>()
    val toAdd = mutableListOf<IRichLyricLine>()

    fun sync(matches: List<IRichLyricLine>, active: MutableList<IRichLyricLine>) {
        toRemove.clear()
        toAdd.clear()

        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i) as? RichLyricLineView ?: continue
            if (view.line !in matches) toRemove.add(view)
        }
        matches.forEach { if (it !in active) toAdd.add(it) }
        if (toRemove.isEmpty() && toAdd.isEmpty()) return
    }
}