/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import io.github.proify.lyricon.colorextractor.palette.ColorExtractorImpl.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ColorExtractor {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val caches = ConcurrentHashMap<String, ColorPaletteResult>()
    private const val TAG = "ColorExtractor"

    fun extractAsync(
        bitmap: Bitmap,
        cacheKey: (suspend () -> String)? = null,
        callback: (ColorPaletteResult?) -> Unit
    ) {
        scope.launch {
            val key = cacheKey?.invoke()

            if (key != null && caches.containsKey(key)) {
                Log.d(TAG, "Cache hit: $key")
                withContext(Dispatchers.Main) {
                    callback(caches[key])
                }
                return@launch
            }
            Log.d(TAG, "Cache miss: $key")

            try {
                val start = System.currentTimeMillis()
                val palette = ColorExtractorImpl.extractThemePalette(bitmap)

                //约50ms
                Log.d(TAG, "Extracted in ${System.currentTimeMillis() - start}ms")

                fun buildColor(r: ThemePalette, isDark: Boolean): ThemeColors {
                    val color = if (isDark) r.onBlackBackground else r.onWhiteBackground
                    return ThemeColors(
                        primary = color.firstOrNull() ?: Color.BLACK,
                        swatches = color.toIntArray()
                    )
                }

                val result = ColorPaletteResult(
                    buildColor(palette, false),
                    buildColor(palette, true)
                )

                key?.let { caches[it] = result }

                withContext(Dispatchers.Main) {
                    callback(result)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

}