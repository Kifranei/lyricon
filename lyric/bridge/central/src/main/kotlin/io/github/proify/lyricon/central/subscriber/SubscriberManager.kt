/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.subscriber

import android.util.Log
import io.github.proify.lyricon.subscriber.SubscriberInfo
import java.util.concurrent.CopyOnWriteArraySet

object SubscriberManager {
    private const val TAG = "SubscriberManager"

    private val subscribers = CopyOnWriteArraySet<RemoteSubscriber>()

    fun register(provider: RemoteSubscriber) {
        if (subscribers.add(provider)) {
            provider.setDeathRecipient { unregister(provider) }
        }
    }

    fun unregister(provider: RemoteSubscriber) {
        if (subscribers.remove(provider)) {
            provider.destroy()
        }
        Log.d(TAG, "unregister: $provider")
    }

    fun getSubscriber(subscriberInfo: SubscriberInfo): RemoteSubscriber? =
        subscribers.firstOrNull { it.subscriberInfo == subscriberInfo }
}