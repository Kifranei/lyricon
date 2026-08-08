/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

/**
 * 当前活跃播放器的状态监听器。
 *
 * Central 会在多个已注册的 Provider 中选择一个“活跃播放器”，并只把该播放器的状态
 * 通过此接口分发给监听方。活跃播放器的选择策略由 central 内部维护，监听方不应假设
 * 某个 Provider 注册后一定会立即成为活跃播放器。
 *
 * 当监听器被添加时，如果 central 已经存在活跃播放器，通常会先收到一组当前状态快照：
 * [onActiveProviderChanged]、[onPlaybackStateChanged]、歌词内容回调、显示选项回调以及
 * [onPositionChanged]。后续则只接收状态变化事件。
 *
 * 回调可能来自 central 的内部线程，不保证在主线程执行。实现方如果需要更新 UI，必须自行
 * 切换到主线程。实现方也应避免在回调内执行耗时操作，以免阻塞 central 的事件分发。
 *
 * 回调实现抛出的异常会被 central 捕获并记录，不会中断其他监听器的分发；但监听方仍应
 * 尽量自行处理异常，保持回调逻辑轻量、幂等。
 */
interface ActivePlayerListener {

    /**
     * 活跃播放器发生变化。
     *
     * 当值不为 `null` 时，表示后续状态事件来自该 Provider 对应的播放器。当值为 `null` 时，
     * 表示当前没有可用的活跃播放器，通常发生在活跃 Provider 断开、进程死亡或被注销时。
     *
     * 收到 `null` 后，监听方应清理当前展示状态，例如隐藏歌词、停止进度刷新或显示空状态。
     *
     * @param providerInfo 新的活跃播放器信息；为 `null` 表示没有活跃播放器。
     */
    fun onActiveProviderChanged(providerInfo: ProviderInfo?)

    /**
     * 当前活跃播放器的结构化歌曲信息发生变化。
     *
     * 该回调用于传递解析后的 [Song] 数据，通常包含歌词、翻译、罗马音等结构化内容。
     * 如果 Provider 使用纯文本方式推送歌词，则可能不会触发该回调，而是触发 [onSendText]。
     *
     * @param song 当前歌曲信息；为 `null` 表示暂无结构化歌曲数据。
     */
    fun onSongChanged(song: Song?)

    /**
     * 当前活跃播放器的播放状态发生变化。
     *
     * 该回调只表示播放/暂停状态，不保证歌曲信息或播放进度已经同步完成。监听方应把它视为
     * 独立状态更新，并结合 [onPositionChanged]、[onSongChanged] 或 [onSendText] 更新 UI。
     *
     * @param isPlaying `true` 表示正在播放，`false` 表示暂停、停止或暂不可播放。
     */
    fun onPlaybackStateChanged(isPlaying: Boolean)

    /**
     * 当前活跃播放器的播放进度更新。
     *
     * 该回调用于连续同步播放位置，单位为毫秒。它可能以较高频率触发，监听方应避免在此回调
     * 中执行昂贵操作。进度值已在 central 内部归一化为非负数。
     *
     * 与 [onSeekTo] 的区别是：该回调表示常规进度刷新；[onSeekTo] 表示播放器发生了明确的
     * seek 行为，监听方可以据此做额外的跳转动画或重同步处理。
     *
     * @param position 当前播放进度，单位为毫秒，非负。
     */
    fun onPositionChanged(position: Long)

    /**
     * 当前活跃播放器执行了 seek 操作。
     *
     * 该回调用于表达一次明确的跳转行为。通常在收到该回调后，后续仍会继续收到
     * [onPositionChanged] 的常规进度刷新。
     *
     * @param position seek 后的目标播放进度，单位为毫秒，非负。
     */
    fun onSeekTo(position: Long)

    /**
     * 当前活跃播放器推送了纯文本内容。
     *
     * 该回调通常用于 Provider 无法或不需要提供结构化 [Song] 时，直接推送纯文本歌词、状态
     * 提示、错误描述或其他展示文本。监听方应根据业务决定该文本是否替换已有结构化歌词。
     *
     * @param text 文本内容；为 `null` 表示清空或暂无文本内容。
     */
    fun onSendText(text: String?)

    /**
     * 当前活跃播放器的翻译显示偏好发生变化。
     *
     * 该状态只表达 Provider 建议的显示偏好，不强制监听方必须显示或隐藏翻译。监听方可以结合
     * 自身设置决定最终展示效果。
     *
     * @param isDisplayTranslation `true` 表示建议显示翻译，`false` 表示建议隐藏翻译。
     */
    fun onDisplayTranslationChanged(isDisplayTranslation: Boolean)

    /**
     * 当前活跃播放器的罗马音显示偏好发生变化。
     *
     * 该状态只表达 Provider 建议的显示偏好，不强制监听方必须显示或隐藏罗马音。监听方可以
     * 结合自身设置决定最终展示效果。
     *
     * @param isDisplayRoma `true` 表示建议显示罗马音，`false` 表示建议隐藏罗马音。
     */
    fun onDisplayRomaChanged(isDisplayRoma: Boolean)
}
