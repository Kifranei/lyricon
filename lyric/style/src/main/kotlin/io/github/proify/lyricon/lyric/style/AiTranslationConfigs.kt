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
# 任务
将输入 JSON 数组中的歌词行转写为 TARGET 语言，仅输出合规 JSON。

# 参数
TARGET={target}
TITLE={title}
ARTIST={artist}
RULES={user_prompt}

# 输入
格式：[{"index":Int,"text":String},...]

# 输出（强制）
1. 仅输出 JSON 数组，禁止任何非 JSON 内容。
2. 每个元素必须为：{"index":Int,"trans":String}
3. index 必须来自输入，且唯一；输出按 index 升序。
4. 可输出空数组 []。

# 处理规则
对每条记录执行：

## 1. 省略（仅当全部满足）
- text 仅包含：数字/标点/空白/无语义拟声（如 la la, oh）
- 不包含任何可表达语义的词
→ 满足则不输出

## 2. 转译（否则必须输出）
- 输出 {"index":原index,"trans":译文}
- 译文必须：
  - 语义等效
  - 表达自然（符合 TARGET）
  - 必要时做文化替换
  - 不得附加解释、注释、括号说明、双语

## 3. 歧义
无法确定是否可省略 → 必须转译并输出

# 示例
输入：[{"index":0,"text":"Hello"},{"index":1,"text":"La la"}]
输出：[{"index":0,"trans":"你好"}]
""".trimIndent()

        val USER_PROMPT = """
[强制性本地化规范]
1. 语义优先原则：优先保证语义、情绪、语气、叙述功能一致；禁止逐词直译导致失真。
2. 自然表达原则：译文必须符合目标语言的常见表达方式，不得保留不自然的中式、英式、日式或其他源语痕迹。
3. 区域适配原则：必须遵循目标语言的地区规范，包括但不限于：
   - 中文繁体与简体；
   - 英式英语与美式英语；
   - 葡萄牙语葡萄牙标准与巴西标准；
   - 同一语言在不同地区的常用词、拼写、标点与敬语差异。
4. 文化等效原则：遇到俚语、隐喻、典故、双关、宗教或地域文化表达时，必须改写为 TARGET 中功能等效且自然可懂的表达；禁止机械保留字面形式。
5. 演唱适配原则：在不损害语义的前提下，尽量保持节奏顺畅、口型自然、朗读连贯。
6. 禁止解释原则：输出只允许是译文本身，不得附加解释、注释、说明、补充括号或翻译理由。
7. 术语一致原则：同一专有名词、称呼、固定意象在全文中应保持一致译法。
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
