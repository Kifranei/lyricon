/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import io.github.proify.lyricon.lyric.model.Song

/**
 * 活跃播放器状态监听器。
 * 用于接收当前正在运行的媒体播放器的元数据、播放状态以及歌词配置变更。
 */
interface ActivePlayerListener {

    /**
     * 当活跃的媒播放器应用发生切换时触发。
     *
     * @param providerInfo 新的提供者信息，若为 null 则表示当前没有活跃的播放器。
     */
    fun onActiveProviderChanged(providerInfo: ProviderInfo?)

    /**
     * 当当前播放的歌曲信息发生变更时触发。
     *
     * @param song 歌曲元数据对象，包含标题、艺术家等信息。
     */
    fun onSongChanged(song: Song?)

    /**
     * 当接收到新的文本（如普通无时间戳的文本时）时触发。
     *
     * 一些播放器可能会发送携带歌词+翻译的文本。比如
     * ```
     * 你好 世界\nHello world
     * ```
     *
     * @param text 接收到的文本字符串。
     */
    fun onReceiveText(text: String?)

    /**
     * 当播放状态（播放/暂停）发生变更时触发。
     *
     * @param isPlaying 当前是否正在播放。
     */
    fun onPlaybackStateChanged(isPlaying: Boolean)

    /**
     * 当播放进度更新时触发。
     *
     * @param position 当前播放进度的时间戳（毫秒）。
     */
    fun onPositionChanged(position: Long)

    /**
     * 当手动跳跃进度操作时触发。
     *
     * @param position 目标进度时间戳（毫秒）。
     */
    fun onSeekTo(position: Long)

    /**
     * 当是否显示歌词翻译的配置发生变更时触发。
     *
     * @param isDisplayTranslation 是否显示翻译。
     */
    fun onDisplayTranslationChanged(isDisplayTranslation: Boolean)

    /**
     * 当是否显示罗马音（Roma）的配置发生变更时触发。
     *
     * @param isDisplayRoma 是否显示罗马音。
     */
    fun onDisplayRomaChanged(isDisplayRoma: Boolean)
}