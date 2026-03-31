/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.subscriber

import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.central.json
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.IActivePlayerBinder
import io.github.proify.lyricon.subscriber.IActivePlayerListener
import io.github.proify.lyricon.subscriber.IRemoteActivePlayerService
import io.github.proify.lyricon.provider.ProviderInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

internal object RemoteActivePlayerClientManager {

    private const val TAG = "RemoteActivePlayerClientMgr"
    private val DEBUG = Constants.isDebug()

    private val clients = CopyOnWriteArraySet<RemoteActivePlayerClient>()

    fun register(remoteBinder: IActivePlayerBinder): RemoteActivePlayerClient {
        val existed = getClient(remoteBinder)
        if (existed != null) return existed

        val client = RemoteActivePlayerClient(remoteBinder)
        if (clients.add(client)) {
            client.setDeathRecipient { unregister(client) }
        }
        return client
    }

    fun unregister(client: RemoteActivePlayerClient) {
        if (clients.remove(client)) {
            client.onDestroy()
        }
    }

    private fun getClient(remoteBinder: IActivePlayerBinder): RemoteActivePlayerClient? {
        val token = remoteBinder.asBinder()
        return clients.firstOrNull { it.isSameToken(token) }
    }

    internal class RemoteActivePlayerClient(
        private var binder: IActivePlayerBinder?
    ) {
        private val binderToken: IBinder? = binder?.asBinder()

        var service: RemoteActivePlayerService? = RemoteActivePlayerService(this)
            private set

        private var deathRecipient: IBinder.DeathRecipient? = null

        fun isSameToken(token: IBinder?): Boolean = binderToken == token

        fun setDeathRecipient(newDeathRecipient: IBinder.DeathRecipient?) {
            deathRecipient?.runCatching {
                binderToken?.unlinkToDeath(this, 0)
            }

            newDeathRecipient?.runCatching {
                binderToken?.linkToDeath(this, 0)
            }

            deathRecipient = newDeathRecipient
        }

        fun destroy() {
            unregister(this)
        }

        fun onDestroy() {
            service?.destroy()
            service = null

            setDeathRecipient(null)
            binder = null
        }
    }

    internal class RemoteActivePlayerService(
        private var client: RemoteActivePlayerClient?
    ) : IRemoteActivePlayerService.Stub() {

        private val listeners =
            ConcurrentHashMap<IBinder, ListenerHolder>()

        override fun addActivePlayerListener(listener: IActivePlayerListener?) {
            if (listener == null) return

            val token = listener.asBinder()
            if (listeners.containsKey(token)) return

            val deathRecipient = IBinder.DeathRecipient {
                removeActivePlayerListener(listener)
            }

            try {
                token.linkToDeath(deathRecipient, 0)
            } catch (_: RemoteException) {
                return
            }

            val bridge = ActivePlayerListenerBridge(listener) {
                removeActivePlayerListener(listener)
            }

            listeners[token] = ListenerHolder(bridge, deathRecipient)
            ActivePlayerDispatcher.addActivePlayerListener(bridge)
        }

        override fun removeActivePlayerListener(listener: IActivePlayerListener?) {
            if (listener == null) return

            val token = listener.asBinder()
            val holder = listeners.remove(token) ?: return

            runCatching {
                token.unlinkToDeath(holder.deathRecipient, 0)
            }

            ActivePlayerDispatcher.removeActivePlayerListener(holder.bridge)
        }

        override fun disconnect() {
            client?.destroy()
        }

        fun destroy() {
            listeners.keys.forEach { token ->
                listeners.remove(token)?.let { holder ->
                    runCatching {
                        token.unlinkToDeath(holder.deathRecipient, 0)
                    }
                    ActivePlayerDispatcher.removeActivePlayerListener(holder.bridge)
                }
            }

            client = null
        }

        private data class ListenerHolder(
            val bridge: ActivePlayerListener,
            val deathRecipient: IBinder.DeathRecipient
        )
    }

    private class ActivePlayerListenerBridge(
        private val remoteListener: IActivePlayerListener,
        private val onRemoteError: () -> Unit
    ) : ActivePlayerListener {

        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            send { it.onActiveProviderChanged(providerInfo) }
        }

        override fun onSongChanged(song: Song?) {
            val songBytes = song?.let {
                runCatching {
                    json.encodeToString(Song.serializer(), it).toByteArray(Charsets.UTF_8)
                }.getOrNull()
            }
            send { it.onSongChanged(songBytes) }
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            send { it.onPlaybackStateChanged(isPlaying) }
        }

        override fun onPositionChanged(position: Long) {
            send { it.onPositionChanged(position) }
        }

        override fun onSeekTo(position: Long) {
            send { it.onSeekTo(position) }
        }

        override fun onSendText(text: String?) {
            send { it.onSendText(text) }
        }

        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
            send { it.onDisplayTranslationChanged(isDisplayTranslation) }
        }

        override fun onDisplayRomaChanged(displayRoma: Boolean) {
            send { it.onDisplayRomaChanged(displayRoma) }
        }

        private inline fun send(block: (IActivePlayerListener) -> Unit) {
            runCatching { block(remoteListener) }
                .onFailure {
                    if (DEBUG) {
                        Log.e(TAG, "Failed to dispatch active player callback", it)
                    }
                    onRemoteError()
                }
        }
    }
}