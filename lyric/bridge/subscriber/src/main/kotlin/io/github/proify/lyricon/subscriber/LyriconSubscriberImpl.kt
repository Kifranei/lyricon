/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lyricon 订阅者实现类。
 *
 * 负责管理与系统 Lyricon 服务的远程连接、超时自动重试以及状态监听分发。
 * 能够区分用户主动触发的连接序列与系统自动触发的恢复逻辑。
 *
 * @property context Android 上下文，用于发送广播。
 * @property subscriberInfo 订阅者元数据信息。
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class LyriconSubscriberImpl(
    val context: Context,
    override val subscriberInfo: SubscriberInfo
) : LyriconSubscriber {
    private val service = RemoteSubscriberService(this)

    /** 状态监听器集合，确保回调的顺序与唯一性 */
    private val connectionListeners = LinkedHashSet<ConnectionListener>()

    /** 当前连接任务的重试计数 */
    private var currentRetryCount = 0

    /**
     * 标记当前连接尝试的性质。
     * - `true`: 由系统服务重启或重试机制发起的被动行为。
     * - `false`: 由用户手动调用 [register] 发起的主动行为。
     */
    private var isPassiveAttempt = false

    private companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val CONNECT_TIMEOUT_MS = 3000L
    }

    private val binder: SubscriberBinder = SubscriberBinder(subscriberInfo).apply {
        addRegistrationCallback(object : SubscriberBinder.RegistrationCallback {
            override fun onRegistered(service: IRemoteService?) {
                val isPassive = isPassiveAttempt

                status = SubscriberStatus.CONNECTED
                currentRetryCount = 0
                this@LyriconSubscriberImpl.service.bindService(service)

                // 根据触发源分发对应的成功回调
                connectionListeners.forEach {
                    if (isPassive) {
                        it.onReconnected(this@LyriconSubscriberImpl)
                    } else {
                        it.onConnected(this@LyriconSubscriberImpl)
                    }
                }
            }
        })
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 当前订阅者的连接状态 */
    var status = SubscriberStatus.DISCONNECTED
        private set

    /**
     * 注册连接状态监听器。
     */
    override fun addConnectionListener(listener: ConnectionListener) {
        connectionListeners.add(listener)
    }

    /**
     * 注销连接状态监听器。
     */
    override fun removeConnectionListener(listener: ConnectionListener) {
        connectionListeners.remove(listener)
    }

    override fun subscribeActivePlayer(listener: ActivePlayerListener) =
        service.registerActivePlayerListener(listener)

    override fun unsubscribeActivePlayer(listener: ActivePlayerListener) =
        service.unregisterActivePlayerListener(listener)

    private var isDestroy = false

    /**
     * 用户主动发起注册。
     * 该操作会重置重试计数并标记为主动连接任务。
     */
    override fun register() {
        if (status == SubscriberStatus.CONNECTED) return

        isPassiveAttempt = false
        currentRetryCount = 0
        performRegistration()
    }

    /**
     * 执行底层 Binder 注册广播的发送逻辑。
     */
    private fun performRegistration() {
        if (isDestroy) return

        status = SubscriberStatus.CONNECTING
        val intent = Intent(SubscriberConstants.ACTION_REGISTER_SUBSCRIBER)

        intent.putExtra(SubscriberConstants.EXTRA_BUNDLE, bundleOf(
            SubscriberConstants.EXTRA_BINDER to binder
        ))
        intent.setPackage(SubscriberConstants.SYSTEM_UI_PACKAGE_NAME)
        context.sendBroadcast(intent)

        launchConnectTimeoutTask()
    }

    private var connectTimeoutTaskJob: Job? = null

    private val serviceListener = object : CentralServiceReceiver.ServiceListener {
        override fun onServiceBootCompleted() {
            if (status.isDisconnectedByRemote()) {
                // 系统服务重启引发的恢复属于被动行为
                isPassiveAttempt = true
                currentRetryCount = 0
                performRegistration()
            }
        }
    }

    init {
        CentralServiceReceiver.addServiceListener(serviceListener)
    }

    /**
     * 启动超时监控任务。
     * 若在指定时间内未连接成功，将根据重试次数决定再次尝试或宣告超时。
     */
    private fun launchConnectTimeoutTask() {
        connectTimeoutTaskJob?.cancel()
        connectTimeoutTaskJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (isDestroy) return@launch

            if (status.isConnecting()) {
                if (currentRetryCount < MAX_RETRY_COUNT) {
                    currentRetryCount++
                    performRegistration()
                } else {
                    status = SubscriberStatus.DISCONNECTED
                    currentRetryCount = 0
                    connectionListeners.forEach { it.onConnectTimeout(this@LyriconSubscriberImpl) }
                }
            }
        }
    }

    override fun unregister() {
        unregisterInternal()
    }

    /**
     * 内部断开逻辑。
     * @param isFromRemote 是否由服务端主动断开。
     */
    internal fun unregisterInternal(isFromRemote: Boolean = false) {
        if (isDestroy) return
        service.disconnect()

        status = if (isFromRemote) {
            SubscriberStatus.DISCONNECTED_BY_REMOTE
        } else {
            SubscriberStatus.DISCONNECTED
        }

        connectionListeners.forEach { it.onDisconnected(this@LyriconSubscriberImpl) }
    }

    /**
     * 销毁订阅者，释放协程作用域并移除系统监听。
     */
    override fun destroy() {
        if (isDestroy) return
        isDestroy = true
        connectTimeoutTaskJob?.cancel()
        connectTimeoutTaskJob = null
        unregister()
        CentralServiceReceiver.removeServiceListener(serviceListener)
        connectionListeners.clear()
    }
}