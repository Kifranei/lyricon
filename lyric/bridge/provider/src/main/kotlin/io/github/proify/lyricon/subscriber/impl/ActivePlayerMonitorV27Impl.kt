/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber.impl

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ActivePlayerMonitor
import io.github.proify.lyricon.provider.CentralServiceReceiver
import io.github.proify.lyricon.subscriber.IActivePlayerBinder
import io.github.proify.lyricon.subscriber.IActivePlayerListener
import io.github.proify.lyricon.subscriber.IRemoteActivePlayerService
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.provider.json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class ActivePlayerMonitorV27Impl(
    private val context: Context,
    private val centralPackageName: String,
    activePlayerListener: ActivePlayerListener? = null
) : ActivePlayerMonitor, CentralServiceReceiver.ServiceListener {

    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()
    private val listenerBridges = ConcurrentHashMap<ActivePlayerListener, IActivePlayerListener>()

    private val destroyed = AtomicBoolean(false)

    @Volatile
    private var shouldStayRegistered = false

    @Volatile
    private var remoteService: IRemoteActivePlayerService? = null

    private var remoteDeathRecipient: IBinder.DeathRecipient? = null

    override var activePlayerListener: ActivePlayerListener? = null
        set(value) {
            val old = field
            if (old === value) return

            if (old != null) {
                removeActivePlayerListener(old)
            }

            field = value

            if (value != null) {
                addActivePlayerListener(value)
            }
        }

    private val registrationBinder = object : IActivePlayerBinder.Stub() {
        override fun onRegistrationCallback(service: IRemoteActivePlayerService?) {
            if (destroyed.get() || !shouldStayRegistered) {
                runCatching { service?.disconnect() }
                return
            }
            bindRemoteService(service)
        }
    }

    init {
        CentralServiceReceiver.addServiceListener(this)
        this@ActivePlayerMonitorV27Impl.activePlayerListener = activePlayerListener
    }

    override fun addActivePlayerListener(listener: ActivePlayerListener): Boolean {
        if (destroyed.get()) return false
        if (!listeners.add(listener)) return false

        remoteService?.let { service ->
            runCatching {
                service.addActivePlayerListener(getOrCreateBridge(listener))
            }.onFailure {
                Log.e(TAG, "Failed to add active player listener", it)
            }
        }

        return true
    }

    override fun removeActivePlayerListener(listener: ActivePlayerListener): Boolean {
        if (!listeners.remove(listener)) return false

        val bridge = listenerBridges.remove(listener) ?: return true
        remoteService?.let { service ->
            runCatching {
                service.removeActivePlayerListener(bridge)
            }
        }

        return true
    }

    override fun register(): Boolean {
        if (destroyed.get()) return false
        if (centralPackageName.isBlank()) return false

        shouldStayRegistered = true

        context.sendBroadcast(
            Intent(ProviderConstants.ACTION_REGISTER_ACTIVE_PLAYER_LISTENER).apply {
                setPackage(centralPackageName)
                putExtra(
                    ProviderConstants.EXTRA_BUNDLE,
                    bundleOf(ProviderConstants.EXTRA_BINDER to registrationBinder)
                )
            }
        )

        return true
    }

    override fun unregister(): Boolean {
        if (destroyed.get()) return false
        shouldStayRegistered = false
        disconnectRemoteService()
        return true
    }

    override fun destroy(): Boolean {
        if (!destroyed.compareAndSet(false, true)) return false

        shouldStayRegistered = false
        activePlayerListener = null
        listenerBridges.clear()
        listeners.clear()
        disconnectRemoteService()
        CentralServiceReceiver.removeServiceListener(this)

        return true
    }

    override fun onServiceBootCompleted() {
        if (destroyed.get()) return
        if (shouldStayRegistered) {
            register()
        }
    }

    private fun bindRemoteService(service: IRemoteActivePlayerService?) {
        disconnectRemoteService(notifyRemote = false)

        if (service == null) {
            return
        }

        val token = service.asBinder()
        if (!token.isBinderAlive) {
            return
        }

        val deathRecipient = IBinder.DeathRecipient {
            disconnectRemoteService(notifyRemote = false)
        }

        runCatching {
            token.linkToDeath(deathRecipient, 0)
        }.onFailure {
            Log.e(TAG, "Failed to link active player service death recipient", it)
            return
        }

        remoteDeathRecipient = deathRecipient
        remoteService = service

        listeners.forEach { listener ->
            runCatching {
                service.addActivePlayerListener(getOrCreateBridge(listener))
            }.onFailure {
                Log.e(TAG, "Failed to sync active player listener", it)
            }
        }
    }

    private fun disconnectRemoteService(notifyRemote: Boolean = true) {
        val service = remoteService
        val recipient = remoteDeathRecipient

        remoteService = null
        remoteDeathRecipient = null

        service?.let {
            runCatching {
                if (recipient != null) {
                    it.asBinder().unlinkToDeath(recipient, 0)
                }
            }

            if (notifyRemote) {
                runCatching { it.disconnect() }
            }
        }
    }

    private fun getOrCreateBridge(listener: ActivePlayerListener): IActivePlayerListener {
        return listenerBridges.computeIfAbsent(listener) {
            ActivePlayerListenerBridge(it)
        }
    }

    private class ActivePlayerListenerBridge(
        private val delegate: ActivePlayerListener
    ) : IActivePlayerListener.Stub() {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            delegate.onActiveProviderChanged(providerInfo)
        }

        override fun onSongChanged(song: ByteArray?) {
            val parsedSong = song?.let {
                runCatching {
                    json.decodeFromString(Song.serializer(), it.toString(Charsets.UTF_8))
                }.getOrNull()
            }
            delegate.onSongChanged(parsedSong)
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            delegate.onPlaybackStateChanged(isPlaying)
        }

        override fun onPositionChanged(position: Long) {
            delegate.onPositionChanged(position)
        }

        override fun onSeekTo(position: Long) {
            delegate.onSeekTo(position)
        }

        override fun onSendText(text: String?) {
            delegate.onSendText(text)
        }

        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
            delegate.onDisplayTranslationChanged(isDisplayTranslation)
        }

        override fun onDisplayRomaChanged(displayRoma: Boolean) {
            delegate.onDisplayRomaChanged(displayRoma)
        }
    }

    private companion object {
        private const val TAG = "ActivePlayerMonitor"
    }
}
