/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import android.os.Build
import android.os.SharedMemory
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.proify.lyricon.lyric.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 活跃播放器状态调度器。
 *
 * 负责跨进程同步播放信息及高频进度更新。该类通过 AIDL 接收远程服务的回调，
 * 并利用 [SharedMemory] 实现低延迟的播放进度同步。
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class ActivePlayerListenerDispatcher : IActivePlayerListener.Stub() {

    companion object {
        private const val TAG = "APListenerDispatcher"

        /** 进度轮询频率（毫秒），16ms 约等于 60fps 的刷新率 */
        private const val TICK_RATE_MS = 16L
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()

    private val memoryLock = Any()
    private var positionSharedMemory: SharedMemory? = null
    private var positionReadBuffer: ByteBuffer? = null

    private val isPlaying = AtomicBoolean(false)
    private var positionObserverJob: Job? = null

    /**
     * 更新共享内存句柄并重新映射缓冲区。
     *
     * 用于接收来自远程服务的内存文件描述符，从而实现高效的进度读取。
     *
     * @param sharedMemory 远程服务传入的共享内存实例。
     */
    fun setPositionSharedMemory(sharedMemory: SharedMemory?) {
        synchronized(memoryLock) {
            positionReadBuffer = null
            positionSharedMemory?.close()

            positionSharedMemory = sharedMemory
            // 将共享内存映射为只读缓冲区
            positionReadBuffer = sharedMemory?.mapReadOnly()
        }
        launchOrStopPositionObserver()
    }

    /** 注册状态监听器 */
    fun registerActivePlayerListener(listener: ActivePlayerListener) = listeners.add(listener)

    /** 注销状态监听器 */
    fun unregisterActivePlayerListener(listener: ActivePlayerListener) = listeners.remove(listener)

    @OptIn(ExperimentalSerializationApi::class)
    override fun onActiveProviderChanged(providerInfo: ByteArray) {
        val providerInfo = runCatching {
            json.decodeFromStream<ProviderInfo>(providerInfo.inputStream())
        }.onFailure {
            Log.e(TAG, "ProviderInfo decoding failed", it)
        }.getOrNull()

        forEachListener { it.onActiveProviderChanged(providerInfo) }
    }

    /**
     * 当歌曲信息变更时调用。
     * 接收字节流并反序列化为 [Song] 对象。
     */
    @OptIn(ExperimentalSerializationApi::class)
    override fun onSongChanged(song: ByteArray) {
        val decodedSong = runCatching {
            json.decodeFromStream<Song>(song.inputStream())
        }.onFailure {
            Log.e(TAG, "Song decoding failed", it)
        }.getOrNull()

        forEachListener { it.onSongChanged(decodedSong) }
    }

    override fun onPlaybackStateChanged(playing: Boolean) {
        this.isPlaying.set(playing)
        forEachListener { it.onPlaybackStateChanged(playing) }
        launchOrStopPositionObserver()
    }

    override fun onSeekTo(position: Long) {
        forEachListener { it.onSeekTo(position) }
    }

    override fun onReceiveText(text: String?) {
        forEachListener { it.onReceiveText(text) }
    }

    override fun onDisplayTranslationChanged(isDisplay: Boolean) {
        forEachListener { it.onDisplayTranslationChanged(isDisplay) }
    }

    override fun onDisplayRomaChanged(isDisplay: Boolean) {
        forEachListener { it.onDisplayRomaChanged(isDisplay) }
    }

    /**
     * 分发进度更新事件。
     */
    fun onPositionChanged(position: Long) {
        forEachListener { it.onPositionChanged(position) }
    }

    /**
     * 根据播放状态决定是否启动进度监测协程。
     */
    private fun launchOrStopPositionObserver() {
        if (isPlaying.get()) launchPositionObserver() else stopPositionObserver()
    }

    /**
     * 启动进度观察者。
     */
    private fun launchPositionObserver() {
        synchronized(this) {
            if (positionObserverJob?.isActive == true) return

            positionObserverJob = coroutineScope.launch {
                // 结合协程生命周期与播放状态进行高频轮询
                while (isActive && isPlaying.get()) {
                    onPositionChanged(readSharedMemoryPosition())
                    delay(TICK_RATE_MS)
                }
            }
        }
    }

    /**
     * 停止并取消进度观察协程。
     */
    private fun stopPositionObserver() {
        synchronized(this) {
            positionObserverJob?.cancel()
            positionObserverJob = null
        }
    }

    /**
     * 从共享内存中读取当前的播放进度。
     * @return 毫秒级进度值，读取失败或内存越界时返回 0。
     */
    private fun readSharedMemoryPosition(): Long {
        return synchronized(memoryLock) {
            try {
                positionReadBuffer?.getLong(0)?.coerceAtLeast(0L) ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    /**
     * 安全地遍历所有监听器并执行操作，防止单个监听器的异常影响全局分发。
     */
    private inline fun forEachListener(crossinline action: (ActivePlayerListener) -> Unit) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Dispatch failed", e)
            }
        }
    }

    /**
     * 释放调度器占用的所有资源。
     *
     * @param clearAllListeners 是否同时清空监听器列表，默认为 false。
     */
    fun release(clearAllListeners: Boolean = false) {
        isPlaying.set(false)
        stopPositionObserver()
        synchronized(memoryLock) {
            positionReadBuffer = null
            positionSharedMemory?.close()
            positionSharedMemory = null
        }
        coroutineScope.cancel()
        if (clearAllListeners) listeners.clear()
    }
}