/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.subscriber

import android.os.IBinder
import android.util.Log
import io.github.proify.lyricon.central.connection.RemoteConnection
import io.github.proify.lyricon.subscriber.ISubscriberBinder
import io.github.proify.lyricon.subscriber.SubscriberInfo
import java.util.concurrent.atomic.AtomicBoolean

internal class SubscriberConnection(
    private var binder: ISubscriberBinder?,
    val subscriberInfo: SubscriberInfo
) : RemoteConnection<SubscriberInfo> {

    override val key: SubscriberInfo get() = subscriberInfo

    val service = SubscriberServiceBinder(this)

    private var deathRecipient: IBinder.DeathRecipient? = null
    private val closed = AtomicBoolean(false)

    override fun setDeathRecipient(onDeath: (() -> Unit)?) {
        if (closed.get() && onDeath != null) return

        val next = onDeath?.let { IBinder.DeathRecipient { it() } }
        deathRecipient?.runCatching {
            binder?.asBinder()?.unlinkToDeath(this, 0)
        }?.onFailure {
            Log.e(TAG, "unlink to death failed", it)
        }

        next?.runCatching {
            binder?.asBinder()?.linkToDeath(this, 0)
        }?.onFailure {
            Log.e(TAG, "link to death failed", it)
        }

        deathRecipient = next
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        setDeathRecipient(null)
        service.close()
        binder = null
    }

    override fun toString() = "SubscriberConnection{$subscriberInfo}"

    private companion object {
        private const val TAG = "SubscriberConnection"
    }
}
