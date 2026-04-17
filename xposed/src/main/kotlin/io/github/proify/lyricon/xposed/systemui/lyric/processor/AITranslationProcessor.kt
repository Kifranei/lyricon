/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.processor

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.xposed.systemui.util.AITranslator

/**
 * AI 翻译加工器的具体实现
 */
class AITranslationProcessor : PostProcessor {

    override fun isEnabled(style: LyricStyle): Boolean {
        val packageStyle = style.packageStyle
        return packageStyle.text.isAiTranslationEnable
                && packageStyle.text.aiTranslationConfigs?.isUsable == true
    }

    override suspend fun process(song: Song, style: LyricStyle): Song {
        val packageStyle = style.packageStyle
        val configs = packageStyle.text.aiTranslationConfigs ?: return song

        return AITranslator.translateSongSync(song, configs)
    }
}