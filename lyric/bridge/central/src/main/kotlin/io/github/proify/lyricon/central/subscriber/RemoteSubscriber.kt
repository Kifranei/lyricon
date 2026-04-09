/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.subscriber

import android.os.IBinder
import android.util.Log
import io.github.proify.lyricon.subscriber.ISubscriberBinder
import io.github.proify.lyricon.subscriber.SubscriberInfo

class RemoteSubscriber(
    var binder: ISubscriberBinder?,
    val subscriberInfo: SubscriberInfo
) {
    companion object {
        private const val TAG = "RemoteSubscriber"
    }

    var service: RemoteSubscriberService? = RemoteSubscriberService(this)

    private var deathRecipient: IBinder.DeathRecipient? = null

    private var isDestroyed = false

    fun setDeathRecipient(newDeathRecipient: IBinder.DeathRecipient?) {
        if (isDestroyed) return
        deathRecipient?.runCatching {
            binder?.asBinder()?.unlinkToDeath(this, 0)
        }?.onFailure {
            Log.e(TAG, "unlink to Death failed", it)
        }

        newDeathRecipient?.runCatching {
            binder?.asBinder()?.linkToDeath(this, 0)
        }?.onFailure {
            Log.e(TAG, "link to Death failed", it)
        }

        deathRecipient = newDeathRecipient
    }

    fun destroy() {
        isDestroyed = true
        setDeathRecipient(null)
        service?.release()
        service = null
        binder = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return if (other is RemoteSubscriber) {
            subscriberInfo == other.subscriberInfo
        } else false
    }

    override fun hashCode(): Int = subscriberInfo.hashCode()
}