/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import android.media.session.PlaybackState
import android.os.SharedMemory
import android.os.SystemClock
import android.util.Log
import io.github.proify.lyricon.central.inflate
import io.github.proify.lyricon.central.json
import io.github.proify.lyricon.central.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.IRemotePlayer
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.util.concurrent.atomic.AtomicBoolean

internal class PlayerBinder(
    info: ProviderInfo,
    private val playerEvents: PlayerListener
) : IRemotePlayer.Stub(), ScreenStateMonitor.ScreenStateListener {

    private val recorder = PlayerRecorder(info)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val closed = AtomicBoolean(false)
    private val isState2Enabled = AtomicBoolean(false)
    private val closeMutex = Mutex()
    private val positionMemory = PositionMemoryBridge(info)
    private val positionTicker = PositionTicker(
        scope = scope,
        readPosition = ::computeCurrentPosition,
        onPosition = ::publishPosition
    )

    @Volatile
    private var positionUpdateInterval: Long = ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL

    @Volatile
    private var lastPlaybackState: PlaybackState? = null

    init {
        ScreenStateMonitor.addListener(this)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        ScreenStateMonitor.removeListener(this)
        stopPositionUpdate()

        scope.launch {
            closeMutex.withLock {
                positionMemory.close()
                scope.cancel()
            }
        }
    }

    override fun onScreenOn() {
        if (recorder.isPlaying) startPositionUpdate()
    }

    override fun onScreenOff() {
        stopPositionUpdate()
    }

    override fun onScreenUnlocked() = Unit

    override fun setPositionUpdateInterval(interval: Int) {
        if (closed.get()) return

        val next = interval.toLong().coerceAtLeast(MIN_INTERVAL_MS)
        if (positionUpdateInterval == next) return

        positionUpdateInterval = next
        if (recorder.isPlaying) {
            stopPositionUpdate()
            startPositionUpdate()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun setSong(bytes: ByteArray?) {
        if (closed.get()) return

        scope.launch {
            val song = bytes?.let {
                runCatching {
                    it.inflate()
                        .inputStream()
                        .buffered()
                        .use {
                            json.decodeFromStream(Song.serializer(), it)
                        }
                }.getOrNull()
            }

            val normalized = song?.normalize()
            recorder.song = normalized
            playerEvents.safeNotify { onSongChanged(recorder, normalized) }
        }
    }

    override fun setPlaybackState(isPlaying: Boolean) {
        if (closed.get()) return

        isState2Enabled.set(false)
        lastPlaybackState = null

        if (recorder.isPlaying != isPlaying) {
            recorder.isPlaying = isPlaying
            playerEvents.safeNotify { onPlaybackStateChanged(recorder, isPlaying) }
        }

        if (isPlaying) startPositionUpdate() else stopPositionUpdate()
    }

    override fun setPlaybackState2(state: PlaybackState?) {
        if (closed.get()) return

        if (state == null) {
            if (isState2Enabled.compareAndSet(true, false)) {
                lastPlaybackState = null
                stopPositionUpdate()
            }
            return
        }

        Log.d(TAG, "setPlaybackState2: $state")

        if (state.state == PlaybackState.STATE_BUFFERING) return

        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        isState2Enabled.set(true)
        lastPlaybackState = state

        if (recorder.isPlaying != isPlaying) {
            recorder.isPlaying = isPlaying
            playerEvents.safeNotify { onPlaybackStateChanged(recorder, isPlaying) }
        }

        if (isPlaying) startPositionUpdate() else stopPositionUpdate()
    }

    override fun seekTo(position: Long) {
        if (closed.get()) return

        val safe = position.coerceAtLeast(0L)
        recorder.position = safe
        playerEvents.safeNotify { onSeekTo(recorder, safe) }
    }

    override fun sendText(text: String?) {
        if (closed.get()) return

        recorder.text = text
        playerEvents.safeNotify { onSendText(recorder, text) }
    }

    override fun setDisplayTranslation(isDisplayTranslation: Boolean) {
        if (closed.get()) return

        recorder.isDisplayTranslation = isDisplayTranslation
        playerEvents.safeNotify { onDisplayTranslationChanged(recorder, isDisplayTranslation) }
    }

    override fun setDisplayRoma(isDisplayRoma: Boolean) {
        if (closed.get()) return

        recorder.isDisplayRoma = isDisplayRoma
        playerEvents.safeNotify { onDisplayRomaChanged(recorder, isDisplayRoma) }
    }

    override fun getPositionMemory(): SharedMemory? = positionMemory.sharedMemory

    private fun computeCurrentPosition(): Long {
        if (!isState2Enabled.get()) return positionMemory.readPosition()

        val state = lastPlaybackState ?: return 0L
        val basePosition = state.position.coerceAtLeast(0L)
        if (state.state != PlaybackState.STATE_PLAYING) return basePosition

        val lastUpdate = state.lastPositionUpdateTime
        if (lastUpdate <= 0L) return basePosition

        val delta = (SystemClock.elapsedRealtime() - lastUpdate).coerceAtLeast(0L)
        val advanced = if (state.playbackSpeed == 1.0f) {
            basePosition + delta
        } else {
            basePosition + (delta * state.playbackSpeed).toLong()
        }

        return advanced.coerceAtLeast(0L)
    }

    private fun startPositionUpdate() {
        if (closed.get()) return
        if (ScreenStateMonitor.state == ScreenStateMonitor.ScreenState.OFF) return
        positionTicker.start(positionUpdateInterval)
    }

    private fun stopPositionUpdate() {
        positionTicker.stop()
    }

    private fun publishPosition(position: Long) {
        recorder.position = position
        playerEvents.safeNotify { onPositionChanged(recorder, position) }
    }

    private inline fun PlayerListener.safeNotify(crossinline block: PlayerListener.() -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "player event dispatch failed", e)
        }
    }

    private companion object {
        private const val TAG = "PlayerBinder"
        private const val MIN_INTERVAL_MS = 16L
    }
}
