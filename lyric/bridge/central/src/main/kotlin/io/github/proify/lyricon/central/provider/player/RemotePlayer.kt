/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import android.annotation.SuppressLint
import android.os.SharedMemory
import android.system.Os
import android.system.OsConstants
import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.central.inflate
import io.github.proify.lyricon.central.json
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.IRemotePlayer
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderInfo
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
import java.util.concurrent.atomic.AtomicBoolean

internal class RemotePlayer(
    private val info: ProviderInfo,
    private val playerListener: PlayerListener = ActivePlayerDispatcher
) : IRemotePlayer.Stub() {

    companion object {
        private val DEBUG = Constants.isDebug()
        private const val TAG = "RemotePlayer"
        private const val MIN_INTERVAL_MS = 16L
    }

    private val recorder = PlayerRecorder(info)

    private var positionSharedMemory: SharedMemory? = null

    @Volatile
    private var positionReadBuffer: ByteBuffer? = null

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var positionProducerJob: Job? = null

    @Volatile
    private var positionUpdateInterval: Long =
        ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL

    private val released = AtomicBoolean(false)

    init {
        initSharedMemory()
    }

    fun destroy() {
        if (!released.compareAndSet(false, true)) return

        stopPositionUpdate()

        positionReadBuffer?.let { SharedMemory.unmap(it) }
        positionReadBuffer = null

        positionSharedMemory?.close()
        positionSharedMemory = null

        scope.cancel()
    }

    private fun initSharedMemory() {
        try {
            val hash = ("${info.providerPackageName}/${info.playerPackageName}").hashCode()
            val hashHex = Integer.toHexString(hash)

            positionSharedMemory = SharedMemory.create(
                "lyricon_music_position_${hashHex}_${Os.getpid()}",
                Long.SIZE_BYTES
            ).apply {
                setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
                positionReadBuffer = mapReadOnly()
            }

            if (DEBUG) Log.i(TAG, "SharedMemory initialized")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init SharedMemory", t)
        }
    }

    /**
     * 直接读取 shared memory 的 long 值（热路径，尽量保持轻量）
     */
    private fun readPosition(): Long {
        val buffer = positionReadBuffer ?: return 0L
        return try {
            buffer.getLong(0).coerceAtLeast(0L)
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * 启动位置刷新的协程。使用基于 nanoTime 的节拍减少调度抖动。
     * start/stop 的并发控制以简单的 null 检查为主（已在外面用 volatile 标记）。
     */
    private fun startPositionUpdate() {
        if (positionProducerJob != null || released.get()) return

        val interval = positionUpdateInterval.coerceAtLeast(MIN_INTERVAL_MS)

        if (DEBUG) Log.d(TAG, "Start position updater, interval=$interval ms")

        positionProducerJob = scope.launch {
            var nextTick = System.nanoTime()
            while (isActive) {
                val position = readPosition()
                recorder.lastPosition = position

                playerListener.safeNotify {
                    onPositionChanged(recorder, position)
                }

                // 以期望时间点为基准，减少累积漂移
                nextTick += interval * 1_000_000L
                val sleepNs = nextTick - System.nanoTime()
                if (sleepNs > 0) {
                    delay(sleepNs / 1_000_000L)
                } else {
                    // 若已超时，则立即执行下一轮（避免负 delay）
                    // 但为避免忙等仍交出线程一下
                    delay(0)
                    nextTick = System.nanoTime()
                }
            }
        }
    }

    @SuppressLint("MemberExtensionConflict")
    private fun stopPositionUpdate() {
        positionProducerJob?.cancel()
        positionProducerJob = null
        if (DEBUG) Log.d(TAG, "Stop position updater")
    }

    override fun setPositionUpdateInterval(interval: Int) {
        if (released.get()) return

        positionUpdateInterval = interval.toLong().coerceAtLeast(MIN_INTERVAL_MS)

        if (DEBUG) Log.d(TAG, "Update interval = $positionUpdateInterval ms")

        if (positionProducerJob != null) {
            stopPositionUpdate()
            startPositionUpdate()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun setSong(bytes: ByteArray?) {
        if (released.get()) return

        val song = bytes?.let {
            try {
                val start = System.currentTimeMillis()
                val decompressed = it.inflate()
                val parsed = json.decodeFromStream(Song.serializer(), decompressed.inputStream())
                if (DEBUG) Log.d(TAG, "Song parsed in ${System.currentTimeMillis() - start} ms")
                parsed
            } catch (t: Throwable) {
                Log.e(TAG, "Song parse failed", t)
                null
            }
        }

        val normalized = song?.normalize()
        recorder.lastSong = normalized

        if (DEBUG) Log.i(TAG, "Song changed")
        playerListener.safeNotify {
            onSongChanged(recorder, normalized)
        }
    }

    override fun setPlaybackState(isPlaying: Boolean) {
        if (released.get()) return

        recorder.lastIsPlaying = isPlaying

        playerListener.safeNotify {
            onPlaybackStateChanged(recorder, isPlaying)
        }

        if (DEBUG) Log.i(TAG, "Playback state = $isPlaying")

        if (isPlaying) startPositionUpdate() else stopPositionUpdate()
    }

    override fun seekTo(position: Long) {
        if (released.get()) return
        if (DEBUG) Log.i(TAG, "Seek to: $position")

        val safe = position.coerceAtLeast(0L)
        recorder.lastPosition = safe

        playerListener.safeNotify {
            onSeekTo(recorder, safe)
        }
    }

    override fun sendText(text: String?) {
        if (released.get()) return
        if (DEBUG) Log.i(TAG, "Send text: $text")

        recorder.lastText = text
        playerListener.safeNotify {
            onSendText(recorder, text)
        }
    }

    override fun setDisplayTranslation(isDisplayTranslation: Boolean) {
        if (released.get()) return
        if (DEBUG) Log.i(TAG, "Display translation: $isDisplayTranslation")

        recorder.lastIsDisplayTranslation = isDisplayTranslation
        playerListener.safeNotify {
            onDisplayTranslationChanged(recorder, isDisplayTranslation)
        }
    }

    override fun getPositionMemory(): SharedMemory? = positionSharedMemory

    /**
     * 统一的 listener 回调保护：捕获异常，避免单个 listener 破坏整个分发流程。
     */
    private inline fun PlayerListener.safeNotify(crossinline block: PlayerListener.() -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "Error notifying listener", t)
        }
    }
}