/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.processor

import io.github.proify.lyricon.lyric.model.Song

/**
 * 「隐藏翻译 / 仅显示翻译」在 SystemUI 侧的纯数据规则。
 *
 * 翻译可能从三条路径进入状态栏：
 * 1. 结构化歌词的 [io.github.proify.lyricon.lyric.model.RichLyricLine.translation]；
 * 2. 同时间戳双语 LRC（`[00:01.00]原文` + `[00:01.00]译文`）被 lrckit 合并进
 *    `secondary`（无逐字时间），形式上是"和声"，实际是整首歌的译文；
 * 3. 纯文本模式（sendText）：`LyricPlayerView.text` 把第二行当作翻译。
 *
 * 三条路径都必须遵守开关；两者同时开启时以「隐藏翻译」为准。
 */
object TranslationDisplayRules {

    /**
     * 判定 secondary 是否其实是整首歌的双语译文（而非和声）。
     *
     * 条件：整首没有结构化翻译、没有任何逐字时间的 secondary，
     * 且至少一半有正文的行都带纯文本 secondary。和声 / 背景人声通常只出现在少数行，
     * 并且 TTML / Apple Music 来源会带逐字时间，不会被误判。
     */
    fun isBilingualSecondary(song: Song): Boolean {
        val lines = song.lyrics ?: return false
        if (lines.any { !it.translation.isNullOrBlank() || !it.translationWords.isNullOrEmpty() }) {
            return false
        }
        if (lines.any { !it.secondaryWords.isNullOrEmpty() }) return false
        val textLines = lines.count { !it.text.isNullOrBlank() || !it.words.isNullOrEmpty() }
        if (textLines == 0) return false
        val plainSecondary = lines.count { !it.secondary.isNullOrBlank() }
        return plainSecondary * 2 >= textLines
    }

    /** 隐藏翻译：去掉结构化翻译；双语 LRC 时一并去掉作为译文的 secondary。 */
    fun hideTranslation(song: Song): Song {
        val stripSecondary = isBilingualSecondary(song)
        return song.copy(lyrics = song.lyrics?.map { line ->
            val hasTranslation =
                !line.translation.isNullOrBlank() || !line.translationWords.isNullOrEmpty()
            val hasSecondary = stripSecondary &&
                    (!line.secondary.isNullOrBlank() || !line.secondaryWords.isNullOrEmpty())
            if (!hasTranslation && !hasSecondary) {
                line
            } else {
                line.copy(
                    translation = null,
                    translationWords = null,
                    secondary = if (stripSecondary) null else line.secondary,
                    secondaryWords = if (stripSecondary) null else line.secondaryWords,
                )
            }
        })
    }

    /** 仅显示翻译：用译文替换正文；双语 LRC 时以 secondary 作为译文。 */
    fun translationOnly(song: Song): Song {
        val useSecondary = isBilingualSecondary(song)
        return song.copy(lyrics = song.lyrics?.map { line ->
            when {
                !line.translation.isNullOrBlank() -> line.copy(
                    text = line.translation,
                    words = line.translationWords,
                    translation = null,
                    translationWords = null
                )

                useSecondary && !line.secondary.isNullOrBlank() -> line.copy(
                    text = line.secondary,
                    words = line.secondaryWords,
                    secondary = null,
                    secondaryWords = null
                )

                else -> line
            }
        })
    }

    /**
     * 纯文本模式：`LyricPlayerView.text` 以第一行为正文、第二行为翻译。
     */
    fun applyToText(text: String?, hideTranslation: Boolean, translationOnly: Boolean): String? {
        if (text.isNullOrBlank()) return text
        val lines = text.lines()
        if (lines.size < 2) return text
        return when {
            hideTranslation -> lines.first()
            translationOnly -> lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: lines.first()
            else -> text
        }
    }
}
