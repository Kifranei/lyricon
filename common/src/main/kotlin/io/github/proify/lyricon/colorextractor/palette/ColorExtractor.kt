/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import android.graphics.Color
import io.github.proify.lyricon.colorextractor.palette.ColorExtractorImpl.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ColorExtractor {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun extractAsync(bitmap: Bitmap, callback: (ColorPaletteResult?) -> Unit) {
        scope.launch {

            val r = ColorExtractorImpl.extractThemePalette(bitmap)
            withContext(Dispatchers.Main) {

                fun buildColor(r: ThemePalette, isDark: Boolean): ThemeColors {
                    val color = if (isDark) r.onBlackBackground else r.onWhiteBackground
                    return ThemeColors(
                        primary = color.firstOrNull() ?: Color.BLACK,
                        swatches = color.toIntArray()
                    )
                }

                callback(
                    ColorPaletteResult(
                        buildColor(r, false),
                        buildColor(r, true)
                    )
                )
            }
            return@launch
        }
    }
}