/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.subscriber

import android.os.SharedMemory
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.subscriber.IActivePlayerListener
import io.github.proify.lyricon.subscriber.IRemoteService

/**
 * 远程订阅服务实现
 *
 * 负责管理远程监听器的生命周期，并通过共享内存高效同步播放进度。
 */
class RemoteSubscriberService(
    val subscriber: RemoteSubscriber
) : IRemoteService.Stub() {

    private val activePlayerListener = ActivePlayerListenerImpl(subscriber)
    private var isRelease = false

    override fun setActivePlayerListener(listener: IActivePlayerListener?) {
        if (isRelease) error("Service is released")

        activePlayerListener.remoteListener = listener
        syncLatestData()

        if (listener == null) {
            ActivePlayerDispatcher.removeActivePlayerListener(activePlayerListener)
        } else {
            ActivePlayerDispatcher.addActivePlayerListener(activePlayerListener)
        }
    }

    override fun getActivePlayerPositionMemory(): SharedMemory? =
        if (isRelease) error("Service is released") else activePlayerListener.positionSharedMemory

    private fun syncLatestData() {
        val activeRecorder = ActivePlayerDispatcher.activeRecorder ?: return
        ActivePlayerDispatcher.syncNewProviderState(activeRecorder, activePlayerListener)
    }

    fun release() {
        if (isRelease) return
        isRelease = true
        ActivePlayerDispatcher.removeActivePlayerListener(activePlayerListener)
        activePlayerListener.release()
    }

    override fun disconnect() {
        if (isRelease) error("Service is released")
        SubscriberManager.unregister(subscriber)
    }
}