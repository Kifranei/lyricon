/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.view.View
import androidx.core.view.isVisible

internal object ViewVisibilityPolicy {

    data class Input(
        val views: List<RichLyricLineView>,
        val style: LyricViewStyle,
    )

    fun apply(input: Input) {
        val views = input.views
        if (views.isEmpty()) return

        val v0 = views[0]
        val v1 = views.getOrNull(1)
        val s = input.style

        val pSize = s.primary.size
        val sSize = s.secondary.size
        val isTransition = v0.main.isFinished && v1 != null
        val v0HasSec = v0.secondary.model.let { it.text.isNotBlank() || it.words.isNotEmpty() }

        // Phase 1: size assignment
        v0.main.setTextSize(if (isTransition) sSize else pSize)
        v0.secondary.setTextSize(sSize)
        v1?.main?.setTextSize(if (isTransition) pSize else sSize)
        v1?.secondary?.setTextSize(sSize)

        // Phase 2+3: candidate flags + slot enforcement (max 2 visible LyricLineView)
        val wantV0sec = !isTransition && (v0.alwaysShowSecondary || (v0HasSec && v0.secondary.isStarted))
        var remaining = 2
        val v0mVis = remaining > 0; if (v0mVis) remaining--
        val v1mVis = v1 != null && remaining > 0; if (v1mVis) remaining--
        val v0sVis = wantV0sec && remaining > 0; if (v0sVis) remaining--
        val v1sVis = v1 != null && false && remaining > 0; if (v1sVis) remaining--

        v0.main.visibilityIfChanged = if (v0mVis) View.VISIBLE else View.GONE
        v0.secondary.visibilityIfChanged = if (v0sVis) View.VISIBLE else View.GONE
        v1?.main?.visibilityIfChanged = if (v1mVis) View.VISIBLE else View.GONE
        v1?.secondary?.visibilityIfChanged = if (v1sVis) View.VISIBLE else View.GONE

        // Phase 4: hide v2+
        for (i in 2 until views.size) {
            val v = views[i]
            v.main.visibilityIfChanged = View.GONE
            v.secondary.visibilityIfChanged = View.GONE
            v.visibilityIfChanged = View.GONE
            v.setRenderScale(1f)
            v.translationY = 0f
        }

        // Phase 5: container visibility
        for (i in 0 until minOf(2, views.size)) {
            val v = views[i]
            val anyVis = v.main.isVisible || v.secondary.isVisible
            v.visibilityIfChanged = if (anyVis) View.VISIBLE else View.GONE
        }

        // Phase 6: scale & translation
        var visibleCount = 0
        if (v0mVis) visibleCount++
        if (v1mVis) visibleCount++
        if (v0sVis) visibleCount++
        if (v1sVis) visibleCount++

        val scale = if (visibleCount > 1) s.scaleMultiLine.coerceIn(0.1f, 2f) else 1f
        val isMulti = visibleCount > 1 && v1 != null && v1.isVisible && scale != 1f

        for (i in 0 until minOf(2, views.size)) {
            val v = views[i]
            v.setRenderScale(scale)
            v.translationY = if (isMulti && v.measuredHeight > 0) {
                val offset = (v.measuredHeight * (1f - scale)) / 2f
                if (i == 0) offset else -offset
            } else 0f
        }
    }
}
