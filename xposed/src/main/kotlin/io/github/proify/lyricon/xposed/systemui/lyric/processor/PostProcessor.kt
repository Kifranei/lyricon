/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.processor

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle

/**
 * 后置异步加工器接口
 * 用于承载所有耗时的异步逻辑（如 AI 翻译、拼音注音等）。
 */
interface PostProcessor {
    /** 加工优先级，数值越小越先执行 */
    val priority: Int get() = 100

    /** 检查当前配置下是否需要执行此加工器 */
    fun isEnabled(style: LyricStyle): Boolean = false

    /**
     * 执行具体的加工逻辑
     * @param song 已由前序步骤加工过的歌曲数据
     * @param style 当前激活的样式配置
     * @return 加工完成后的新歌曲对象
     */
    suspend fun process(song: Song, style: LyricStyle): Song
}