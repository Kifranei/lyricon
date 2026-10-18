/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber.internal

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.IRemoteService
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.SubscriberInfo
import io.github.proify.lyricon.subscriber.SubscriberStatus
import io.github.proify.lyricon.subscriber.internal.SubscriberConstants
import io.github.proify.lyricon.subscriber.internal.binding.RegistrationBinder
import io.github.proify.lyricon.subscriber.internal.connection.CentralConnection
import io.github.proify.lyricon.subscriber.internal.registration.CentralBootReceiver
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
 * [CentralConnection] 管理。
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
        CentralConnection { disconnect(remote = true, notifyRemote = false) }
    private val registration = RegistrationCoordinator()

    /** 当前连接状态（由 [RegistrationCoordinator] 维护）。 */
    val status: SubscriberStatus
        get() = registration.status

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
        if (destroyed.get()) return
        registration.cancel()
        disconnect(remote = false)
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        registration.close()
        disconnect(remote = false, destroy = true)
        scope.cancel()
        listeners.clear()
    }

    /** 注册成功：标记连接、绑定远端服务并通知监听器（区分首次与重连）。 */
    private fun onRegistered(service: IRemoteService?, reconnect: Boolean) {
        registration.markConnected()
        remote.bind(service)
        listeners.forEach {
            if (reconnect) it.onReconnected(this) else it.onConnected(this)
        }
    }

    /**
     * 断开连接并通知监听器。
     *
     * 远端 Binder 死亡时保留 [RegistrationCoordinator.wantsConnection]，并安排一次
     * 延迟注册；主动 unregister/destroy 则不会安排重连。
     *
     * @param remote 是否为远端导致的断开。
     * @param notifyRemote 是否调用远端 disconnect 接口。
     * @param destroy 是否彻底销毁连接端点。
     */
    private fun disconnect(
        remote: Boolean,
        notifyRemote: Boolean = true,
        destroy: Boolean = false
    ) {
        registration.abortAttempt()
        this.remote.disconnect(notifyRemote, destroy)
        registration.markDisconnected(remote)
        listeners.forEach { it.onDisconnected(this) }
        if (remote && !destroy) {
            registration.scheduleReconnect()
        }
    }

    /**
     * 注册协调器：发送注册广播、管理连接超时重试与中心重启后的恢复注册。
     */
    private inner class RegistrationCoordinator : CentralBootReceiver.BootListener {
        private val binder = RegistrationBinder(subscriberInfo)
        private var timeoutJob: Job? = null
        private var reconnectJob: Job? = null
        private var retryCount = 0
        private var reconnect = false

        /** 期望保持连接（register 时置位，注销/销毁时复位）。 */
        @Volatile
        private var wantsConnection: Boolean = false

        @Volatile
        var status: SubscriberStatus = SubscriberStatus.DISCONNECTED
            private set

        /** 标记为已连接。 */
        fun markConnected() {
            cancelReconnect()
            status = SubscriberStatus.CONNECTED
        }

        /** 标记为断开。 */
        fun markDisconnected(byRemote: Boolean) {
            status =
                if (byRemote) SubscriberStatus.DISCONNECTED_BY_REMOTE
                else SubscriberStatus.DISCONNECTED
        }

        private val attempt = object : RegistrationBinder.RegistrationAttempt {
            override fun onCompleted(service: IRemoteService?) {
                cancelTimeout()
                retryCount = 0
                if (!wantsConnection || destroyed.get()) return

                if (service == null) {
                    // 中心返回空服务：保持期望连接并稍后恢复。
                    Log.w(TAG, "Central returned null service, registration rejected")
                    status = SubscriberStatus.DISCONNECTED
                    scheduleReconnect()
                    return
                }
                this@LyriconSubscriberImpl.onRegistered(service, reconnect)
            }
        }

        init {
            CentralBootReceiver.addBootListener(this)
        }

        /** 发起注册（用户主动或中心启动恢复）。 */
        fun start(manual: Boolean) {
            if (destroyed.get()) return
            cancelReconnect()
            if (status == SubscriberStatus.CONNECTED || status == SubscriberStatus.CONNECTING) return

            wantsConnection = true
            reconnect = !manual || status.isDisconnectedByRemote()
            retryCount = 0
            send()
        }

        /** Binder 断开后延迟重连，避免在 Binder death 回调栈内立即发送广播。 */
        fun scheduleReconnect() {
            if (destroyed.get() || !wantsConnection) return
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(RECONNECT_DELAY_MS)
                reconnectJob = null
                if (destroyed.get() || !wantsConnection ||
                    status == SubscriberStatus.CONNECTED ||
                    status == SubscriberStatus.CONNECTING
                ) return@launch
                Log.i(TAG, "Remote connection lost, attempting to reconnect")
                start(manual = false)
            }
        }

        override fun onBootCompleted() {
            // 中心服务重启：仍期望连接且当前不在连接中时补一次注册。
            if (wantsConnection &&
                status != SubscriberStatus.CONNECTED &&
                status != SubscriberStatus.CONNECTING
            ) {
                start(manual = false)
            }
        }

        /** 结束当前尝试但保留期望连接（远端断开/超时等非用户路径）。 */
        fun abortAttempt() {
            cancelTimeout()
            binder.endAttempt(attempt)
        }

        /** 注销/销毁：结束尝试并清除期望连接。 */
        fun cancel() {
            wantsConnection = false
            cancelReconnect()
            abortAttempt()
        }

        /** 销毁：结束尝试并停止监听中心启动广播。 */
        fun close() {
            cancel()
            CentralBootReceiver.removeBootListener(this)
        }

        /** 取消注册超时等待。 */
        private fun cancelTimeout() {
            timeoutJob?.cancel()
            timeoutJob = null
        }

        private fun cancelReconnect() {
            reconnectJob?.cancel()
            reconnectJob = null
        }

        /** 发送注册广播并启动超时等待。 */
        private fun send() {
            if (destroyed.get()) return
            status = SubscriberStatus.CONNECTING
            binder.beginAttempt(attempt)
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

        /** 启动注册超时等待；超时后按次数重发广播，耗尽则报告超时。 */
        private fun scheduleTimeout() {
            cancelTimeout()
            timeoutJob = scope.launch {
                delay(CONNECT_TIMEOUT_MS)
                if (destroyed.get() || !status.isConnecting() || !wantsConnection) return@launch
                if (retryCount++ < MAX_RETRY_COUNT) {
                    send()
                } else {
                    retryCount = 0
                    status = SubscriberStatus.DISCONNECTED
                    listeners.forEach { it.onConnectTimeout(this@LyriconSubscriberImpl) }
                    scheduleReconnect()
                }
            }
        }
    }

    private companion object {
        private const val TAG = "LyriconSubscriberImpl"
        private const val MAX_RETRY_COUNT = 3
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val RECONNECT_DELAY_MS = 1_000L
    }
}
