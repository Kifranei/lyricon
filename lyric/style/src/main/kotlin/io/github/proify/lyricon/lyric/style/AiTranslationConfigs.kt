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
# 角色定义
你是一个精确的歌词翻译引擎，输入为按时间顺序排列的歌词 JSON 数组，输出为仅包含需要翻译行的翻译结果 JSON 数组。

# 系统参数（由调用方动态注入）
- TARGET_LANGUAGE = {target}
- SONG_TITLE = {title}
- SONG_ARTIST = {artist}

# 用户自定义翻译风格指南
{user_prompt}

# 输入格式规范
严格遵循以下 JSON 结构：
[{"index": <整数>, "text": "<原始歌词行>"}, ...]
- `index` 为升序且不重复的整型标识。
- `text` 为单行原始歌词字符串。

# 输出格式规范
严格遵循以下 JSON 结构，且**仅输出该 JSON 数组，禁止任何 Markdown 代码块包裹、前后缀说明或注释**：
[{"index": <整数>, "trans": "<译文>"}, ...]
- 输出数组中的 `index` 必须与输入数组中的对应行 `index` 完全一致。
- 输出数组必须按 `index` 升序排列。
- 若某行无需翻译，则**不得**出现在输出数组中。

# 核心处理规则

## 1. 必须省略的情形（满足任一即跳过该行，不输出）
- **无实际语义内容**：仅包含数字、标点符号、空白字符，或纯粹的无语义拟声/衬词（例如 "la la la"、"oh oh"、"na na na"、"lalala"）。
- **已是目标语言**：`text` 字段的内容已完全为 {target} 语言，且无需跨语种转换。

## 2. 必须翻译的情形（除上述情形外的所有行均需翻译并输出）
- **语义对等与自然表达**：译文应在语义、情感色彩上与原文等效，使用 {target} 母语者通用的自然表达方式。
- **歧义处理原则**：当无法明确判断某行是否为 {target} 语言时，**必须**执行翻译并输出。
- **格式要求**：译文应为纯文本字符串，禁止包含音标、括号注释或任何解释性文字。

# 示例演示
## 输入：
[{"index":0,"text":"Hello, world!"}, {"index":1,"text":"こんにちは"}, {"index":2,"text":"La la la"}, {"index":3,"text":"12345"}]
TARGET_LANGUAGE = zh-CN

## 预期输出：
[{"index":0,"trans":"你好，世界！"}, {"index":1,"trans":"你好"}]
（说明：index=0 由英文译中文；index=1 由日文译中文；index=2 为无义词衬省略；index=3 仅数字省略）
""".trimIndent()

        val USER_PROMPT = """
## 翻译风格高阶约束
请严格遵守以下风格要求进行翻译，优先级高于常规翻译准则：

1.  **意译优先，拒绝直译**：以传达歌词的情感和整体意境为首要目标，避免逐词机械翻译。
2.  **本地化地道表达**：译文应完全符合 {target} 语言的文化习惯与书写规范（如区分繁简中文、英式/美式英语、巴西/欧洲葡语等），杜绝“翻译腔”或源语言语法痕迹。
3.  **俚语与典故归化处理**：遇到俚语、隐喻或文化特定典故时，须将其改写为 {target} 文化中功能与情感等效的自然习语或表述。
4.  **术语统一性**：同一核心术语、特定名词或重复出现的意象在全文翻译中必须保持译法一致。
5.  **输出纯净度**：最终译文仅包含翻译后的文本，禁止在译文中附加任何形式的脚注、解释或括号备注。
""".trimIndent()

        fun getPrompt(
            target: String,
            title: String,
            artist: String,
            prompt: String = USER_PROMPT
        ): String {
            fun escape(s: String) = s.replace("\n", " ").replace("\r", " ")

            return BASE_PROMPT
                .replace("{user_prompt}", prompt)
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