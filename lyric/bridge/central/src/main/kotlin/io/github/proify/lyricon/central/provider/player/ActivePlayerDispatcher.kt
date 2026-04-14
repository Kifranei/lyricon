/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */


package io.github.proify.lyricon.central.provider.player

import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.central.provider.player.PlayerRecorder.LyricType.NONE
import io.github.proify.lyricon.central.provider.player.PlayerRecorder.LyricType.SONG
import io.github.proify.lyricon.central.provider.player.PlayerRecorder.LyricType.TEXT
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

object ActivePlayerDispatcher : PlayerListener {

    private const val TAG = "ActivePlayerDispatcher"
    private val DEBUG = Constants.isDebug()

    private val lock = ReentrantReadWriteLock()

    @Volatile
    var activeRecorder: PlayerRecorder? = null
        private set

    private val activeInfo: ProviderInfo? get() = activeRecorder?.providerInfo

    @Volatile
    private var activeIsPlaying: Boolean = false

    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()

    /**
     * 添加监听器，如果监听器已存在，则不添加
     */
    fun addActivePlayerListener(listener: ActivePlayerListener) = listeners.add(listener)

    /**
     * 移除监听器
     */
    fun removeActivePlayerListener(listener: ActivePlayerListener) = listeners.remove(listener)

    fun notifyProviderInvalid(provider: ProviderInfo) {
        val shouldNotify = lock.write {
            if (activeInfo == provider) {
                activeRecorder = null
                activeIsPlaying = false
                true
            } else {
                false
            }
        }

        if (shouldNotify) {
            broadcast {
                it.onActiveProviderChanged(null)
                it.onPlaybackStateChanged(false)
            }
        }
    }

    // ---------------- PlayerListener ----------------

    override fun onSongChanged(recorder: PlayerRecorder, song: Song?) {
        if (DEBUG) Log.d(TAG, "onSongChanged: $song")
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onSongChanged(song)
        }
    }

    override fun onPlaybackStateChanged(recorder: PlayerRecorder, isPlaying: Boolean) {
        if (DEBUG) Log.d(TAG, "onPlaybackStateChanged: $isPlaying")
        dispatchIfActive(recorder) {
            it.onPlaybackStateChanged(isPlaying)
        }
    }

    override fun onPositionChanged(recorder: PlayerRecorder, position: Long) {
        dispatchIfActive(recorder) {
            it.onPositionChanged(position)
        }
    }

    override fun onSeekTo(recorder: PlayerRecorder, position: Long) {
        dispatchIfActive(recorder) {
            it.onSeekTo(position)
        }
    }

    override fun onSendText(recorder: PlayerRecorder, text: String?) {
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onSendText(text)
        }
    }

    override fun onDisplayTranslationChanged(
        recorder: PlayerRecorder,
        isDisplayTranslation: Boolean
    ) {
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onDisplayTranslationChanged(isDisplayTranslation)
        }
    }

    override fun onDisplayRomaChanged(
        recorder: PlayerRecorder,
        displayRoma: Boolean
    ) {
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onDisplayRomaChanged(displayRoma)
        }
    }

    private inline fun dispatchIfActive(
        recorder: PlayerRecorder,
        allowDuplicateIfSwitching: Boolean = true,
        crossinline notifier: (ActivePlayerListener) -> Unit
    ) {
        val recorderInfo = recorder.providerInfo
        val recorderPlaying = recorder.isPlaying

        var isSwitched = false
        var shouldBroadcastOriginal = false

        lock.write {
            val currentInfo = activeInfo
            if (currentInfo === recorderInfo) {
                activeIsPlaying = recorderPlaying
                shouldBroadcastOriginal = true
            } else {
                val canSwitch =
                    currentInfo == null || (!activeIsPlaying && recorderPlaying)

                if (canSwitch) {
                    activeRecorder = recorder
                    activeIsPlaying = recorderPlaying
                    isSwitched = true
                    shouldBroadcastOriginal = allowDuplicateIfSwitching
                }
            }
        }

        if (isSwitched) {
            syncNewProviderState(recorder)
        }

        if (shouldBroadcastOriginal) {
            broadcast(notifier)
        }
    }

    private fun syncNewProviderState(recorder: PlayerRecorder) {
        broadcast { listener ->
            syncNewProviderState(recorder, listener)
        }
    }

    fun syncNewProviderState(
        recorder: PlayerRecorder,
        listener: ActivePlayerListener,
    ) {
        listener.onActiveProviderChanged(recorder.providerInfo)

        listener.onPlaybackStateChanged(activeIsPlaying)

        when (recorder.lyricType) {
            SONG -> listener.onSongChanged(recorder.song)
            TEXT -> listener.onSendText(recorder.text)
            NONE -> Unit
        }

        listener.onDisplayTranslationChanged(recorder.isDisplayTranslation)
        listener.onDisplayRomaChanged(recorder.isDisplayRoma)
        listener.onPositionChanged(recorder.position)
    }

    private inline fun broadcast(
        crossinline notifier: (ActivePlayerListener) -> Unit
    ) {
        for (listener in listeners) {
            try {
                notifier(listener)
            } catch (e: Exception) {
                if (DEBUG) Log.e(
                    TAG,
                    "Dispatch failed for listener: ${listener.javaClass.name}",
                    e
                )
            }
        }
    }
}