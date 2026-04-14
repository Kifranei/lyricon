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
import java.util.Locale

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
        private val BASE_PROMPT = """
你是一个专业的音乐翻译家 Api，正在为歌曲《{title}》 - {artist} 翻译歌词。
目标语言：{target}。

## 输入
一个包含索引和原文的 JSON 列表：[{"index": Int, "text": String}]

## 严格指令
1. 必须保持返回的 "index" 与输入完全一致。
2. 严禁合并多行，严禁拆分单行。每一行输入最多对应一行输出。
3. 若条目原文无需翻译，例如拟声词、纯符号、专有名词或已是 {target}，请直接忽略该条目，不要输出。
4. 仅返回 JSON，严禁包含 Markdown 代码块、解释或其它文字。

## 翻译风格建议
{user_prompt}

## 输出格式
[{"index": Int, "trans": "String"}]
""".trimIndent()

        val USER_PROMPT = """
1. 语义与情感优先，禁止逐词直译。
2. 译文符合 TARGET 地区规范（繁简/英式美式/葡语标准等），不留源语痕迹。
3. 俚语/隐喻/文化典故须改写为 TARGET 中功能等效的自然表达。
4. 同一术语全文统一译法。
5. 仅输出译文，不附任何解释或括号说明。
""".trimIndent()

        fun getPrompt(
            target: String,
            title: String,
            artist: String,
            userPrompt: String = USER_PROMPT
        ): String {
            fun escape(s: String) = s.replace("\n", " ").replace("\r", " ")

            return BASE_PROMPT
                .replace("{user_prompt}", userPrompt)
                .replace("{title}", escape(title))
                .replace("{artist}", escape(artist))
                .replace("{target}", escape(target))
        }

        fun defaultTargetLanguage(locale: Locale = Locale.getDefault()): String {
            val language = locale.language.lowercase(Locale.ROOT)
            val script = locale.script.lowercase(Locale.ROOT)
            val country = locale.country.lowercase(Locale.ROOT)
            return when {
                language == "zh" && (script == "hant" || country in setOf("tw", "hk", "mo")) -> "繁體中文"
                language == "zh" -> "简体中文"
                language == "ja" -> "日本語"
                language == "ko" -> "한국어"
                else -> "English"
            }
        }

        fun cleanLlmOutput(raw: String): String {
            val regex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
            return regex.find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
        }
    }
}
