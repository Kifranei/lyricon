/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.subscriber

import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import io.github.proify.lyricon.central.json
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.subscriber.IActivePlayerListener
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal class ActivePlayerListenerImpl(
    private val subscriber: RemoteSubscriber
) : ActivePlayerListener {

    companion object {
        private const val TAG = "ActivePlayerListenerImpl"
    }

    @Volatile
    var remoteListener: IActivePlayerListener? = null

    var positionSharedMemory: SharedMemory? = null
    private var positionWriteBuffer: ByteBuffer? = null

    init {
        initSharedMemory()
    }

    /**
     * 初始化共享内存，用于存放 Long 类型的播放进度
     */
    private fun initSharedMemory() {
        try {
            val info = subscriber.subscriberInfo
            val hashHex = Integer.toHexString(
                "${info.packageName}/${info.processName}".hashCode()
            )
            positionSharedMemory =
                SharedMemory.create("lyricon_subscriber_pos_$hashHex", java.lang.Long.BYTES)
                    .apply {
                        setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
                        positionWriteBuffer = mapReadWrite()
                    }
        } catch (t: Throwable) {
            Log.e(TAG, "SharedMemory mapping failed", t)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        if (providerInfo == null) {
            remoteListener?.onActiveProviderChanged(null)
            return
        }

        val out = ByteArrayOutputStream()
        json.encodeToStream<ProviderInfo>(providerInfo, out)

        remoteListener?.onActiveProviderChanged(out.toByteArray())
    }

    override fun onSongChanged(song: Song?) {
        val bytes = song?.let { json.encodeToString(it).toByteArray() } ?: byteArrayOf()
        remoteListener?.onSongChanged(bytes)
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        remoteListener?.onPlaybackStateChanged(isPlaying)
    }

    override fun onPositionChanged(position: Long) {
        try {
            positionWriteBuffer?.putLong(0, position)
        } catch (e: Exception) {
            Log.e(TAG, "Position write failed", e)
        }
    }

    override fun onSeekTo(position: Long) {
        remoteListener?.onSeekTo(position)
    }

    override fun onSendText(text: String?) {
        remoteListener?.onReceiveText(text)
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        remoteListener?.onDisplayTranslationChanged(isDisplayTranslation)
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        remoteListener?.onDisplayRomaChanged(isDisplayRoma)
    }

    /**
     * 释放资源
     */
    fun release() {
        positionWriteBuffer = null
        positionSharedMemory?.close()
        positionSharedMemory = null
        remoteListener = null
    }
}