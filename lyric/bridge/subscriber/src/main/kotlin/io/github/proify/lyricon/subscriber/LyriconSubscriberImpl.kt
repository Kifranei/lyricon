/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 默认订阅端实现。
 *
 * 负责发送注册广播、处理连接超时重试、维护连接状态，并把远端服务交给
 * [SubscriberRemoteEndpoint] 管理。
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class LyriconSubscriberImpl(
    private val context: Context,
    override val subscriberInfo: SubscriberInfo,
) : LyriconSubscriber {
    private val destroyed = AtomicBoolean(false)
    private val listeners = CopyOnWriteArraySet<ConnectionListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val remote =
        SubscriberRemoteEndpoint { disconnect(remote = true, notifyRemote = false) }
    private val registration = Registration()

    @Volatile
    var status = SubscriberStatus.DISCONNECTED
        private set

    override fun addConnectionListener(listener: ConnectionListener) {
        listeners.add(listener)
    }

    override fun removeConnectionListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }

    override fun subscribeActivePlayer(listener: ActivePlayerListener): Boolean =
        remote.addActivePlayerListener(listener)

    override fun unsubscribeActivePlayer(listener: ActivePlayerListener): Boolean =
        remote.removeActivePlayerListener(listener)

    override fun register() {
        registration.start(manual = true)
    }

    override fun unregister() {
        if (!destroyed.get()) disconnect(remote = false)
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        registration.close()
        disconnect(remote = false, destroy = true)
        scope.cancel()
        listeners.clear()
    }

    private fun onRegistered(service: IRemoteService?, reconnect: Boolean) {
        status = SubscriberStatus.CONNECTED
        remote.bind(service)
        listeners.forEach {
            if (reconnect) it.onReconnected(this) else it.onConnected(this)
        }
    }

    private fun disconnect(
        remote: Boolean,
        notifyRemote: Boolean = true,
        destroy: Boolean = false
    ) {
        registration.cancelTimeout()
        this.remote.disconnect(notifyRemote, destroy)
        status =
            if (remote) SubscriberStatus.DISCONNECTED_BY_REMOTE else SubscriberStatus.DISCONNECTED
        listeners.forEach { it.onDisconnected(this) }
    }

    /** 管理注册广播、超时重试和中心服务重启后的恢复注册。 */
    private inner class Registration : CentralServiceReceiver.ServiceListener {
        private val binder = SubscriberBinder(subscriberInfo)
        private var timeoutJob: Job? = null
        private var retryCount = 0
        private var reconnect = false
        private val callback = object : SubscriberBinder.RegistrationCallback {
            override fun onRegistered(service: IRemoteService?) {
                cancelTimeout()
                retryCount = 0
                this@LyriconSubscriberImpl.onRegistered(service, reconnect)
            }
        }

        init {
            binder.addRegistrationCallback(callback)
            CentralServiceReceiver.addServiceListener(this)
        }

        fun start(manual: Boolean) {
            if (destroyed.get() || status == SubscriberStatus.CONNECTED) return
            reconnect = !manual || status.isDisconnectedByRemote()
            retryCount = 0
            send()
        }

        override fun onServiceBootCompleted() {
            if (status.isDisconnectedByRemote()) start(manual = false)
        }

        fun cancelTimeout() {
            timeoutJob?.cancel()
            timeoutJob = null
        }

        fun close() {
            cancelTimeout()
            binder.removeRegistrationCallback(callback)
            CentralServiceReceiver.removeServiceListener(this)
        }

        private fun send() {
            if (destroyed.get()) return
            status = SubscriberStatus.CONNECTING
            context.sendBroadcast(Intent(SubscriberConstants.ACTION_REGISTER_SUBSCRIBER).apply {
                setPackage(SubscriberConstants.SYSTEM_UI_PACKAGE_NAME)
                putExtra(
                    SubscriberConstants.EXTRA_BUNDLE,
                    Bundle().apply {
                        putBinder(SubscriberConstants.EXTRA_BINDER, binder)
                    }
                )
            })
            scheduleTimeout()
        }

        private fun scheduleTimeout() {
            cancelTimeout()
            timeoutJob = scope.launch {
                delay(CONNECT_TIMEOUT_MS)
                if (destroyed.get() || !status.isConnecting()) return@launch
                if (retryCount++ < MAX_RETRY_COUNT) send() else {
                    retryCount = 0
                    status = SubscriberStatus.DISCONNECTED
                    listeners.forEach { it.onConnectTimeout(this@LyriconSubscriberImpl) }
                }
            }
        }
    }

    private companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val CONNECT_TIMEOUT_MS = 3_000L
    }
}
