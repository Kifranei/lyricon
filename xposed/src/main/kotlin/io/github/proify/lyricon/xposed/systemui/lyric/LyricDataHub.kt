/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.util.Log
import io.github.proify.lyricon.app.bridge.ProviderLyricPolicy
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.systemui.lyric.processor.LyricDataProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * 歌词数据调度中枢
 * 管理歌词生命周期：获取原始数据 -> 后台加工 -> 第一次分发 -> 后台增强 -> 最终分发。
 */
object LyricDataHub : ActivePlayerListener {
    private const val TAG = "LyricDataHub"

    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 状态版本号，用于在异步恢复后校验数据是否已过时 */
    private val versionCounter = AtomicInteger(0)

    /** 缓存当前的原始歌曲，用于配置变更时重走流程 */
    private var cachedRawSong: Song? = null

    /** 当前执行中的流水线任务 */
    private var activePipelineJob: Job? = null

    /** 当前活动的提供者信息 */
    private var providerInfo: ProviderInfo? = null
    @Volatile var providerPolicy = ProviderLyricPolicy()
        private set
    @Volatile private var lastPosition = 0L
    private var isTextMode = false
    private var cachedRawText: String? = null
    private var playing = false
    private val textScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var textGeneration = 0
    private var textSequence = 0L
    private var lastDeliveredTextSequence = 0L

    fun lyricPosition(position: Long): Long = providerPolicy.lyricPosition(position)

    @Synchronized fun refreshProviderPolicy() {
        val next = ProviderLyricsPreferences.read(providerInfo?.providerPackageName)
        if (next == providerPolicy) return
        providerPolicy = next
        reprocessCurrentSong()
        listeners.forEach { it.onSeekTo(lastPosition) }
    }

    private fun cancelPendingText() {
        textGeneration++
        textScope.coroutineContext.cancelChildren()
    }

    fun addListener(listener: ActivePlayerListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ActivePlayerListener) {
        listeners.remove(listener)
    }

    /**
     * 启动后台加工流水线。
     * 所有歌词加工都在后台协程执行，避免阻塞播放器回调线程。
     * @param rawSong 待加工的原始歌曲
     */
    @Synchronized private fun runProcessingPipeline(rawSong: Song?) {
        val song = rawSong?.deepCopy()
        val policy = providerPolicy
        val currentVersion = versionCounter.incrementAndGet()
        activePipelineJob?.cancel()

        activePipelineJob = scope.launch {
            try {
                if (song == null || policy.blocksEverything) {
                    dispatchSong(null, currentVersion, policy)
                    return@launch
                }

                val style = LyricPrefs.getLyricStyle()

                // 1. 后台前置加工：处理繁简、屏蔽词等基础显示数据
                val preProcessed = LyricDataProcessor.executePreProcessing(song)
                if (!isCurrentVersion(currentVersion)) return@launch

                // 第一次分发：基础处理完成后尽快刷新 UI
                dispatchSong(LyricDataProcessor.executeDisplayProcessing(preProcessed, style), currentVersion, policy)

                // 2. 后台后置流水线：处理 AI 翻译等耗时扩展
                val finalSong =
                    LyricDataProcessor.executePostProcessingPipeline(preProcessed, style)

                if (isCurrentVersion(currentVersion)) {
                    dispatchSong(LyricDataProcessor.executeDisplayProcessing(finalSong, style), currentVersion, policy)
                } else {
                    logOutdatedPipeline(currentVersion, finalSong)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Pipeline $currentVersion cancelled. $e")
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline $currentVersion error", e)
            }
        }
    }

    private fun isCurrentVersion(version: Int): Boolean = version == versionCounter.get()

    private fun logOutdatedPipeline(version: Int, song: Song) {
        Log.d(
            TAG,
            "Pipeline requestVersion:$version, nowVersion:${versionCounter.get()} skipped. ${song.name}"
        )
    }

    /**
     * 重走加工流程
     * 当配置（繁简、AI 开关、翻译模式）变更时调用，无需切歌即可应用新设置。
     */
    @Synchronized fun reprocessCurrentSong() {
        if (isTextMode) {
            cancelPendingText()
            listeners.forEach { it.onReceiveText(null) }
            scheduleText(cachedRawText)
        } else runProcessingPipeline(cachedRawSong)
    }

    // --- ActivePlayerListener 触发点 ---

    @Synchronized override fun onSongChanged(song: Song?) {
        cancelPendingText()
        isTextMode = false
        cachedRawText = null
        this.cachedRawSong = song?.deepCopy()
        runProcessingPipeline(song)
    }

    private var lastDispatchSongId: Int? = null
    @Synchronized private fun dispatchSong(song: Song?, version: Int, policy: ProviderLyricPolicy) {
        if (!isCurrentVersion(version)) return
        val normalize = filterProviderLyrics(song?.normalize(), policy)

        val hashCode = normalize?.hashCode() ?: 0
        if (hashCode == lastDispatchSongId) return
        lastDispatchSongId = hashCode

        listeners.forEach { it.onSongChanged(normalize) }
    }

    // --- 纯状态透传 (不涉及加工) ---

    @Synchronized override fun onReceiveText(text: String?) {
        if (!isTextMode) {
            versionCounter.incrementAndGet()
            activePipelineJob?.cancel()
            cancelPendingText()
            isTextMode = true
            cachedRawSong = null
            lastDispatchSongId = null
            listeners.forEach { it.onSongChanged(null) }
        }
        cachedRawText = text
        if (text.isNullOrBlank()) cancelPendingText()
        scheduleText(text)
    }

    private fun scheduleText(text: String?) {
        val policy = providerPolicy
        // 纯文本模式同样要遵守「隐藏翻译 / 仅显示翻译」：第二行即翻译行。
        val filtered = applyTextDisplay(policy.filterText(text))
        val wait = if (playing && filtered != null) policy.delayMs.coerceAtLeast(0).toLong() else 0L
        val sequence = ++textSequence
        if (wait == 0L) {
            lastDeliveredTextSequence = sequence
            listeners.forEach { it.onReceiveText(filtered) }
            return
        }
        val generation = textGeneration
        // Canceling on every update could starve a delayed fast stream. Never overwrite a newer line.
        textScope.launch {
            delay(wait)
            synchronized(this@LyricDataHub) {
                if (generation == textGeneration && isTextMode && sequence > lastDeliveredTextSequence) {
                    lastDeliveredTextSequence = sequence
                    listeners.forEach { it.onReceiveText(filtered) }
                }
            }
        }
    }

    private fun applyTextDisplay(text: String?): String? = runCatching {
        LyricDataProcessor.executeTextDisplayProcessing(text, LyricPrefs.getLyricStyle())
    }.getOrElse {
        Log.e(TAG, "Text display processing failed", it)
        text
    }

    @Synchronized override fun onPlaybackStateChanged(isPlaying: Boolean) {
        playing = isPlaying
        if (!isPlaying) cancelPendingText()
        listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }

    override fun onPositionChanged(position: Long) {
        lastPosition = position
        listeners.forEach { it.onPositionChanged(position) }
    }

    @Synchronized override fun onSeekTo(position: Long) {
        lastPosition = position
        cancelPendingText()
        listeners.forEach { it.onSeekTo(position) }
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        listeners.forEach { it.onDisplayTranslationChanged(isDisplayTranslation) }
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        listeners.forEach { it.onDisplayRomaChanged(isDisplayRoma) }
    }

    @Synchronized override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        if (this.providerInfo != providerInfo) {
            versionCounter.incrementAndGet()
            activePipelineJob?.cancel()
            cancelPendingText()
            cachedRawSong = null
            cachedRawText = null
            isTextMode = false
            lastDispatchSongId = null
            lastPosition = 0
        }
        this.providerInfo = providerInfo
        providerPolicy = ProviderLyricsPreferences.read(providerInfo?.providerPackageName)
        listeners.forEach { it.onActiveProviderChanged(providerInfo) }
    }
}
