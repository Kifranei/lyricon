/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.aitrans

import android.util.Log
import io.github.proify.android.extensions.json

internal object AITranslationResponseParser {
    private const val TAG = "LyriconAITranslator"

    fun parse(content: String, requestIndices: Set<Int>): List<TranslationItem> {
        val items = decodeTranslationItems(content)
        val validItems = normalizeTranslationItems(items, requestIndices)
        Log.d(TAG, "API call successful, parsed ${items.size} items, accepted ${validItems.size}.")
        return validItems
    }

    private fun decodeTranslationItems(content: String): List<TranslationItem> {
        return try {
            return json.decodeFromString<TranslationResponse>(content).translated
        } catch (_: Exception) {
            json.decodeFromString<TranslationResponse>(cleanOpenAIResponse(content)).translated
        }
    }

    private fun cleanOpenAIResponse(rawResponse: String): String {
        return rawResponse
            .replace(Regex("^```json\\s*\\n?"), "")  // 移除开头的 ```json
            .replace(Regex("^```\\s*\\n?"), "")       // 移除开头的 ```
            .replace(Regex("\\n?```\\s*$"), "")       // 移除结尾的 ```
            .trim()
    }

    private fun normalizeTranslationItems(
        items: List<TranslationItem>,
        requestIndices: Set<Int>
    ): List<TranslationItem> {
        val accepted = LinkedHashMap<Int, TranslationItem>()
        items.forEach { item ->
            val trans = item.tran.trim()
            if (item.index in requestIndices && trans.isNotBlank() && item.index !in accepted) {
                accepted[item.index] = item.copy(tran = trans)
            }
        }
        return accepted.values.toList()
    }
}