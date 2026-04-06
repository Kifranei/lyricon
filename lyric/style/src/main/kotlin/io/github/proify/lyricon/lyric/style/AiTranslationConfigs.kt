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
        private val BASE_PROMPT = """
# 角色
歌词翻译引擎。将 JSON 歌词数组翻译为 TARGET 语言，仅输出合规 JSON。

# 参数
TARGET={target}
TITLE={title}
ARTIST={artist}

# 风格
{user_prompt}

# 输入 / 输出
输入：[{"index":Int,"text":String},...]
输出：[{"index":Int,"trans":String},...]
- 仅输出 JSON，禁止任何额外内容
- index 来自输入，唯一，升序

# 规则
## 省略（满足任一条件则不输出）
- text 仅含数字/标点/空白/无语义拟声（如 la la、oh）
- text 已是 TARGET 语言

## 翻译（其余所有行必须输出）
- 译文语义等效、表达自然、不附注释

## 歧义
- 无法判断是否为 TARGET 语言 → 必须翻译并输出

# 示例
输入：[{"index":0,"text":"Hello"},{"index":1,"text":"你好"},{"index":2,"text":"La la"}]
TARGET=zh-CN
输出：[{"index":0,"trans":"你好"}]
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

        fun cleanLlmOutput(raw: String): String {
            val regex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
            return regex.find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
        }
    }
}