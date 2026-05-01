/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.subscriber

import android.os.SharedMemory
import io.github.proify.lyricon.central.CentralRuntime
import io.github.proify.lyricon.subscriber.IActivePlayerListener
import io.github.proify.lyricon.subscriber.IRemoteService
import java.util.concurrent.atomic.AtomicBoolean

internal class SubscriberServiceBinder(
    private var connection: SubscriberConnection?
) : IRemoteService.Stub() {

    private val closed = AtomicBoolean(false)
    private val subscription = ActivePlayerSubscription(connection!!.subscriberInfo)

    override fun setActivePlayerListener(listener: IActivePlayerListener?) {
        if (closed.get()) return

        subscription.remoteListener = listener

        if (listener == null) {
            CentralRuntime.activePlayers.removeListener(subscription)
        } else {
            CentralRuntime.activePlayers.addListener(subscription)
        }
    }

    override fun getActivePlayerPositionMemory(): SharedMemory? =
        if (closed.get()) null else subscription.positionMemory

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        CentralRuntime.activePlayers.removeListener(subscription)
        subscription.close()
        connection = null
    }

    override fun disconnect() {
        if (closed.get()) return
        connection?.let { CentralRuntime.subscribers.unregister(it) }
    }
}
