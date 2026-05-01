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
    val prompt: String = USER_PROMPT,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topP: Float = DEFAULT_TOP_P,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val presencePenalty: Float = DEFAULT_PRESENCE_PENALTY,
    val frequencyPenalty: Float = DEFAULT_FREQUENCY_PENALTY
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
        }..., model=$model temperature=$temperature topP=$topP maxTokens=$maxTokens prompt=${
            prompt.take(30)
        }..., isUsable=$isUsable)"
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_P = 1.0f
        const val DEFAULT_MAX_TOKENS = 0
        const val DEFAULT_PRESENCE_PENALTY = 0.3f
        const val DEFAULT_FREQUENCY_PENALTY = 0.3f

        private val CORE_PROMPT = """
你是歌词翻译引擎api。

# 元数据
目标语言："{target}"
歌曲："{title}"
歌手名："{artist}"

# 规则
1. 跳过(不输出)：纯目标语言行、纯数字/标点/空白、无意义衬词(如 la la la)。
2. 仅翻译非目标语言或语言归属不明的行。
3. 使用原 index，升序，不重复，不新增或遗漏。
4. 译文自然流畅，不加括号注释，严格保持 index 对应。

# 示例（目标语言为简体中文时）
输入JSON："{"lyrics":[{"index":0,"src":"Hello"},{"index":1,"src":"你好"}]}"
输出JSON："{"translated":[{"index":0,"tran":"你好"}]}"

# 风格要求（仅用于译文措辞，不得破坏上述协议）
```
{style_prompt}
```
""".trimIndent()

        val USER_PROMPT = """
语境迁移：贴合背景、身份和情感。
隐喻转化：替换为本地惯用比喻，舍字面保意境。
曲风适配：民谣克制留白，摇滚直接锋利，说唱押韵顺 flow。
习惯优先：完全用目标语言习惯用语和自然语序，杜绝翻译腔。
""".trimIndent()

        fun getPrompt(
            target: String,
            title: String,
            artist: String,
            prompt: String = USER_PROMPT
        ): String {
            fun escape(s: String) = s.replace("\n", " ")
                .replace("\r", " ")

            return CORE_PROMPT
                .replace("{style_prompt}", prompt)
                .replace("{title}", escape(title))
                .replace("{artist}", escape(artist))
                .replace("{target}", escape(target))
        }
    }
}
