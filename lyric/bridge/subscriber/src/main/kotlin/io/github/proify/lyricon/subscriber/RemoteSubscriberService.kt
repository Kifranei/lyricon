/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class RemoteSubscriberService(
    val subscriber: LyriconSubscriberImpl,
) {
    private var remoteService: IRemoteService? = null
    private val listenerDispatcher = ActivePlayerListenerDispatcher()

    private val deathRecipient = IBinder.DeathRecipient {
        subscriber.unregisterInternal(true)
    }

    fun bindService(service: IRemoteService?) {
        val oldService = remoteService
        remoteService = service
        runCatching {
            remoteService?.setActivePlayerListener(listenerDispatcher)
            listenerDispatcher.setPositionSharedMemory(remoteService?.activePlayerPositionMemory)
        }.onFailure {
            Log.e(TAG, "Failed to bind to remote service", it)
        }

        runCatching {
            oldService?.asBinder()?.unlinkToDeath(deathRecipient, 0)
        }.onFailure {
            Log.e(TAG, "Failed to unbind from old service", it)
        }
        runCatching {
            service?.asBinder()?.linkToDeath(deathRecipient, 0)
        }.onFailure {
            Log.e(TAG, "Failed to bind to new service", it)
        }
    }

    fun registerActivePlayerListener(listener: ActivePlayerListener) =
        listenerDispatcher.registerActivePlayerListener(listener)

    fun unregisterActivePlayerListener(listener: ActivePlayerListener) =
        listenerDispatcher.unregisterActivePlayerListener(listener)

    fun disconnect() {
        remoteService?.disconnect()
        listenerDispatcher.release()
        bindService(null)
    }

    companion object {
        const val TAG: String = "RemoteSubscriberService"
    }
}