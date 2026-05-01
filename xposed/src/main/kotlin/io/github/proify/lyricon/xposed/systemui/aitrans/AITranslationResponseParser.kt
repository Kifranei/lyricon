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
    private const val MAX_LOG_BODY_LENGTH = 1000

    fun parse(content: String, requestIndices: Set<Int>): List<TranslationItem> {
        val jsonPayload = content
        val items = decodeTranslationItems(jsonPayload)
        val validItems = normalizeTranslationItems(items, requestIndices)
        Log.d(TAG, "API call successful, parsed ${items.size} items, accepted ${validItems.size}.")
        return validItems
    }
//
//    private fun extractJsonFromLlmContent(raw: String): String? {
//        val regex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
//        val trimmed = regex.find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
//        if (trimmed.isEmpty()) return null
//
//        Log.e(TAG, "No JSON payload found in response: ${trimForLog(trimmed)}")
//        return trimmed
//    }

    private fun decodeTranslationItems(content: String): List<TranslationItem> {
        return json.decodeFromString<TranslationResponse>(content).translated
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

    private fun trimForLog(value: String): String =
        if (value.length <= MAX_LOG_BODY_LENGTH) value else value.take(MAX_LOG_BODY_LENGTH) + "..."
}