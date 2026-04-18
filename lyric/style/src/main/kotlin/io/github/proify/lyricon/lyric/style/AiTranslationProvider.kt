/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

enum class AiTranslationProvider(val provider: String, val model: String, val url: String) {
    OPENAI(
        "openai",
        "gpt-4o-mini",
        "https://api.openai.com/v1"
    ),
}