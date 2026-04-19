/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.systemui.lyric.processor.LyricDataProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * 歌词数据调度中枢
 * 管理歌词生命周期：获取原始数据 -> Pre 加工 -> 第一次分发 -> Post 加工(等待全部) -> 最终分发。
 */
object LyricDataHub : ActivePlayerListener {

    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 状态版本号，用于在异步恢复后校验数据是否已过时 */
    private val versionCounter = AtomicInteger(0)

    /** 缓存当前的原始歌曲，用于配置变更时重走流程 */
    private var cachedRawSong: Song? = null

    /** 当前执行中的流水线任务 */
    private var activePipelineJob: Job? = null

    /** 当前活动的提供者信息 */
    private var providerInfo: ProviderInfo? = null

    fun addListener(listener: ActivePlayerListener) {
        listeners.add(listener)
    }

    /**
     * 运行加工流水线
     * @param song 待加工的原始歌曲
     */
    private fun runProcessingPipeline(song: Song?) {
        val currentVersion = versionCounter.incrementAndGet()
        activePipelineJob?.cancel()

        if (song == null) {
            dispatchSong(null)
            return
        }

        // 1. 同步前置加工：处理繁简等基础显示
        val preProcessed = LyricDataProcessor.executePreProcessing(song)
        // 第一次分发：立即响应 UI
        dispatchSong(preProcessed)

        // 2. 异步后置流水线：处理 AI 翻译等耗时扩展
        activePipelineJob = scope.launch {
            val style = LyricPrefs.getLyricStyle()
            val finalSong = LyricDataProcessor.executePostProcessingPipeline(preProcessed, style)

            // 校验版本：确保在等待期间用户没有切歌
            if (currentVersion == versionCounter.get()) {
                // 第二次分发：所有异步增强处理完毕
                dispatchSong(finalSong)
            }
        }
    }

    /**
     * 重走加工流程
     * 当配置（繁简、AI 开关、翻译模式）变更时调用，无需切歌即可应用新设置。
     */
    fun reprocessCurrentSong() {
        runProcessingPipeline(cachedRawSong)
    }

    // --- ActivePlayerListener 触发点 ---

    override fun onSongChanged(song: Song?) {
        this.cachedRawSong = song
        runProcessingPipeline(song)
    }

    private fun dispatchSong(song: Song?) {
        listeners.forEach { it.onSongChanged(song) }
    }

    // --- 纯状态透传 (不涉及加工) ---

    override fun onReceiveText(text: String?) {
        listeners.forEach { it.onReceiveText(text) }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }

    override fun onPositionChanged(position: Long) {
        listeners.forEach { it.onPositionChanged(position) }
    }

    override fun onSeekTo(position: Long) {
        listeners.forEach { it.onSeekTo(position) }
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        listeners.forEach { it.onDisplayTranslationChanged(isDisplayTranslation) }
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        listeners.forEach { it.onDisplayRomaChanged(isDisplayRoma) }
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        this.providerInfo = providerInfo
        listeners.forEach { it.onActiveProviderChanged(providerInfo) }
    }
}