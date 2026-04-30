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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 活跃播放器事件分发器。
 *
 * 作为 AIDL 回调接收中心服务推送的播放器事件，再分发给本地 [ActivePlayerListener]。
 * 播放进度通过 [SharedMemory] 轮询读取，避免高频 Binder 回调。
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class ActivePlayerListenerDispatcher : IActivePlayerListener.Stub() {
    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var positionMemory: SharedMemory? = null
    private var positionBuffer: ByteBuffer? = null
    private var positionJob = scope.launch { readPositions() }

    /** 注册本地活跃播放器监听器。 */
    fun registerActivePlayerListener(listener: ActivePlayerListener): Boolean =
        listeners.add(listener)

    /** 移除本地活跃播放器监听器。 */
    fun unregisterActivePlayerListener(listener: ActivePlayerListener): Boolean =
        listeners.remove(listener)

    /** 更新远端提供的播放进度共享内存。 */
    fun setPositionSharedMemory(memory: SharedMemory?) {
        positionBuffer = null
        positionMemory?.close()
        positionMemory = memory
        positionBuffer = runCatching { memory?.mapReadOnly() }
            .onFailure { Log.e(TAG, "Failed to map position memory", it) }
            .getOrNull()
    }

    override fun onActiveProviderChanged(providerInfo: ByteArray?) {
        val info = providerInfo?.takeIf { it.isNotEmpty() }?.decode<ProviderInfo>()
        listeners.forEach { it.onActiveProviderChanged(info) }
    }

    override fun onSongChanged(song: ByteArray?) {
        val value = song?.takeIf { it.isNotEmpty() }?.decode<Song>()
        listeners.forEach { it.onSongChanged(value) }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }

    override fun onSeekTo(position: Long) {
        listeners.forEach { it.onSeekTo(position) }
    }

    override fun onReceiveText(text: String?) {
        listeners.forEach { it.onReceiveText(text) }
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        listeners.forEach { it.onDisplayTranslationChanged(isDisplayTranslation) }
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        listeners.forEach { it.onDisplayRomaChanged(isDisplayRoma) }
    }

    /** 释放共享内存；[clearListeners] 为 `true` 时同时结束调度器生命周期。 */
    fun release(clearListeners: Boolean = false) {
        positionBuffer = null
        positionMemory?.close()
        positionMemory = null
        if (!clearListeners) return

        positionJob.cancel()
        scope.cancel()
        listeners.clear()
    }

    private suspend fun readPositions() {
        var lastPosition = Long.MIN_VALUE
        while (true) {
            val position = try {
                positionBuffer?.getLong(0)
            } catch (_: Exception) {
                null
            }
            if (position != null && position != lastPosition) {
                lastPosition = position
                listeners.forEach { it.onPositionChanged(position) }
            }
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    private inline fun <reified T> ByteArray.decode(): T? =
        runCatching { json.decodeFromString<T>(decodeToString()) }
            .onFailure { Log.e(TAG, "Failed to decode active-player payload", it) }
            .getOrNull()

    private companion object {
        private const val TAG = "ActivePlayerListenerDis"
        private const val POSITION_POLL_INTERVAL_MS = 16L
    }
}
