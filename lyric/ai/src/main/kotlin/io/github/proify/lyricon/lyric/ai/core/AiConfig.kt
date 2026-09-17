/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.ai.core

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 统一 AI 连接配置：所有 AI 功能（歌词翻译、音乐解读）共用的 OpenAI 兼容服务连接信息。
 *
 * 只包含"如何连上服务"的参数（provider / baseUrl / apiKey / model / 采样参数），
 * 不含任何功能语义——目标语言、风格要求等业务参数由各功能自行管理。
 */
@Serializable
@Parcelize
data class AiConfig(
    val apiKey: String? = null,
    val model: String? = null,
    val baseUrl: String? = null,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topP: Float = DEFAULT_TOP_P,
    val presencePenalty: Float = DEFAULT_PRESENCE_PENALTY,
    val frequencyPenalty: Float = DEFAULT_FREQUENCY_PENALTY,
) : Parcelable {

    /** 连接配置是否可用（能否发起请求）。 */
    @IgnoredOnParcel
    val isUsable by lazy {
        !apiKey.isNullOrBlank()
                && !model.isNullOrBlank()
                && !baseUrl.isNullOrBlank()
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_P = 1.0f
        const val DEFAULT_PRESENCE_PENALTY = 0.3f
        const val DEFAULT_FREQUENCY_PENALTY = 0.3f

    }

    override fun toString(): String {
        return "AiConfig(isUsable=$isUsable," +
                " frequencyPenalty=$frequencyPenalty, " +
                "presencePenalty=$presencePenalty," +
                "topP=$topP," +
                " temperature=$temperature, " +
                "baseUrl=$baseUrl, model=$model," +
                " apiKey=${apiKey?.take(5)})"
    }
}