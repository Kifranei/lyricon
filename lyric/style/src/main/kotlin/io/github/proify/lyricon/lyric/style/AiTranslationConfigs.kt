/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class AiTranslationConfigs(
    val provider: String? = null,
    val targetLanguage: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val baseUrl: String? = null,
    val prompt: String = BASE_PROMPT
) : Parcelable {

    @IgnoredOnParcel
    val isUsable by lazy {
        !provider.isNullOrBlank()
                && !targetLanguage.isNullOrBlank()
                && !apiKey.isNullOrBlank()
                && !model.isNullOrBlank()
                && !baseUrl.isNullOrBlank()
    }

    override fun toString(): String {
        return "AiTranslationConfigs(baseUrl=$baseUrl, provider=$provider, targetLanguage=$targetLanguage, apiKey=${
            apiKey.orEmpty().take(6)
        }..., model=$model prompt=${
            prompt.take(30)
        }..., isUsable=$isUsable)"
    }

    companion object {
        val USER_PROMPT = """
        歌词翻译准则：
        1. 语义：保留核心意象与情感，文化专有名词用功能对等表达。
        2. 韵律：匹配原歌词音节数，根据曲风押韵。
        3. 风格：根据歌曲背景（流行/说唱/民谣）调整语气。
        4. 本地化：规避目标文化禁忌。
    """.trimIndent()

        private val BASE_PROMPT = """
        你是一个专业的音乐翻译家。正在为歌曲《{title}》- {artist} 翻译歌词。
        目标语言：{target}。
        
        输入：一个包含索引和原文的 JSON 列表：[{"index": Int, "text": String}]。
        
        严格指令：
        1. 必须保持返回的 "index" 与输入完全一致。
        2. 严禁合并多行，严禁拆分单行。每一行输入必须对应一行输出（除非无需翻译）。
        3. 若原文无需翻译（如拟声词“Oh~”、专有名词、或已是{target}），请从结果列表中忽略该 index，不要返回它。
        4. 翻译风格建议：{user_prompt}
        
        输出格式：
        仅返回 JSON 格式，严禁包含任何 Markdown 代码块标签、解释或其它文字。
        格式：[{"index": Int, "trans": "String"}]
    """.trimIndent()

        fun getPrompt(
            target: String,
            title: String,
            artist: String,
            userPrompt: String = USER_PROMPT
        ): String {
            return BASE_PROMPT
                .replace("{user_prompt}", userPrompt)
                .replace("{target}", target)
                .replace("{title}", title)
                .replace("{artist}", artist)
                .trimIndent()
        }
    }
}