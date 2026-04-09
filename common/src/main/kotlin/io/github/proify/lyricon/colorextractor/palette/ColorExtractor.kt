/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ColorExtractor {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun extractAsync(bitmap: Bitmap, callback: (ColorPaletteResult?) -> Unit) {
        scope.launch {

            val r = ColorExtractorImpl.extract(bitmap)
            withContext(Dispatchers.Main) {

                fun buildColor(r: List<Int>, isDark: Boolean): ThemeColors {
                    return ThemeColors(
                        primary = ColorExtractorImpl.adaptiveLuminance(r[0], isDark),
                        swatches = ColorExtractorImpl.adaptiveLuminance(r.toIntArray(), isDark)
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