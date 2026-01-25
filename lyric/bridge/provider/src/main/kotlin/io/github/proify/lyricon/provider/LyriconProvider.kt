/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.provider

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.github.proify.lyricon.provider.ProviderBinder.OnRegistrationCallback
import io.github.proify.lyricon.provider.player.CachedRemotePlayer
import io.github.proify.lyricon.provider.service.RemoteService
import io.github.proify.lyricon.provider.service.RemoteServiceProxy
import io.github.proify.lyricon.provider.service.addConnectionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 歌词提供者类，用于向中心服务注册并提供歌词服务。
 *
 * 该类负责：
 * - 管理与中心服务的连接和注册状态；
 * - 提供远程调用接口 [service]；
 * - 管理提供者信息 [providerInfo]；
 * - 支持资源释放及生命周期管理。
 *
 * @property context 上下文对象
 * @property providerPackageName 提供者包名
 * @property playerPackageName 播放器包名（默认为 providerPackageName）
 * @property logo 播放器 Logo，可为空
 * @property metadata 提供者元数据，可为空
 * @property providerService 本地服务实例，可为空
 * @property centralPackageNames 中心服务包名列表
 */
class LyriconProvider(
    context: Context,
    providerPackageName: String = context.packageName,
    playerPackageName: String = providerPackageName,
    logo: ProviderLogo? = null,
    metadata: ProviderMetadata? = null,
    providerService: ProviderService? = null,
    var centralPackageNames: List<String> = listOf(ProviderConstants.SYSTEM_UI_PACKAGE_NAME)
) {

    private companion object {
        private const val TAG = "LyriconProvider"
        private const val CONNECTION_TIMEOUT_MS = 4000L
    }

    /** 本地服务 */
    var providerService: ProviderService? = providerService
        set(value) {
            field = value
            localService.listener = value
        }

    val appContext: Context = context.applicationContext

    /** 本地提供者服务实现 */
    private val localService = LocalProviderService(providerService)

    /** 远程服务代理，用于与中心服务交互 */
    private val serviceProxy = RemoteServiceProxy(this)

    /** 提供者绑定器，负责注册回调和广播交互 */
    private val binder = ProviderBinder(this, localService, serviceProxy)

    /** 协程作用域，用于超时处理等异步操作 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 注册连接超时任务 */
    @Volatile
    private var connectionTimeoutJob: Job? = null

    /** 实例销毁标记 */
    private val destroyed = AtomicBoolean(false)

    /** 监听中心服务启动/重启事件 */
    private val centralServiceListener = object : CentralServiceReceiver.ServiceListener {
        override fun onServiceBootCompleted() {
            if (serviceProxy.connectionStatus == ConnectionStatus.DISCONNECTED_REMOTE) {
                Log.d(TAG, "Central service restarted, attempting re-registration")
                register()
            }
        }
    }

    /** 提供者信息对象 */
    val providerInfo: ProviderInfo = ProviderInfo(
        providerPackageName = providerPackageName,
        playerPackageName = playerPackageName,
        logo = logo,
        metadata = metadata
    )

    /** 远程服务接口 */
    val service: RemoteService = serviceProxy
    val player: RemotePlayer get() = service.player

    /**
     * 是否自动同步数据，在连接和恢复连接时调用同步
     */
    var autoSync: Boolean = true

    init {
        CentralServiceReceiver.initialize(appContext)
        CentralServiceReceiver.addServiceListener(centralServiceListener)

        service.addConnectionListener {
            fun handleConnected() {
                val player = this@LyriconProvider.player
                if (autoSync && player is CachedRemotePlayer) {
                    player.syncs()
                }
            }
            onConnected { handleConnected() }
            onReconnected { handleConnected() }
        }
    }

    /**
     * 向中心服务发起注册请求。
     *
     * 注册流程：
     * - 若当前未连接或未注册，启动注册流程；
     * - 若已连接或正在连接，直接返回 false。
     *
     * @return true 表示已发起注册，false 表示已连接或正在连接
     * @throws IllegalStateException 实例已被销毁
     */
    @Synchronized
    fun register(): Boolean {
        if (destroyed.get()) error("Provider has been destroyed")

        return when (serviceProxy.connectionStatus) {
            ConnectionStatus.CONNECTED,
            ConnectionStatus.CONNECTING -> false

            else -> {
                performRegistration()
                true
            }
        }
    }

    /**
     * 执行注册流程：
     * - 设置连接状态为 CONNECTING；
     * - 启动超时任务；
     * - 向中心服务发送注册广播；
     * - 注册回调监听注册成功事件。
     */
    @SuppressLint("MemberExtensionConflict")
    private fun performRegistration() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null

        val registrationCallback = object : OnRegistrationCallback {
            override fun onRegistered() {
                connectionTimeoutJob?.cancel()
                connectionTimeoutJob = null
                binder.removeRegistrationCallback(this)
            }
        }

        serviceProxy.connectionStatus = ConnectionStatus.CONNECTING

        connectionTimeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (serviceProxy.connectionStatus == ConnectionStatus.CONNECTING) {
                serviceProxy.connectionStatus = ConnectionStatus.DISCONNECTED
                binder.removeRegistrationCallback(registrationCallback)

                serviceProxy.connectionListeners.forEach {
                    runCatching { it.onConnectTimeout(this@LyriconProvider) }
                }
            }
        }

        binder.addRegistrationCallback(registrationCallback)

        centralPackageNames.forEach { centralPackageName ->
            val bundle = Bundle().apply {
                putBinder(ProviderConstants.EXTRA_BINDER, binder)
            }

            val intent = Intent(ProviderConstants.ACTION_REGISTER_PROVIDER).apply {
                setPackage(centralPackageName)
                putExtra(ProviderConstants.EXTRA_BUNDLE, bundle)
            }

            appContext.sendBroadcast(intent)
        }
    }

    /**
     * 注销提供者，释放连接资源。
     *
     * @throws IllegalStateException 实例已被销毁
     */
    @Synchronized
    fun unregister() {
        if (destroyed.get()) error("Provider has been destroyed")
        unregisterByUser()
    }

    /**
     * 用户主动注销实现：
     * - 取消注册超时任务；
     * - 断开远程服务连接。
     */
    @SuppressLint("MemberExtensionConflict")
    private fun unregisterByUser() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
        serviceProxy.disconnect(RemoteServiceProxy.DisconnectType.USER)
    }

    /**
     * 销毁实例，释放所有资源。
     *
     * 销毁后对象不可再次使用。
     */
    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return

        scope.cancel()
        unregisterByUser()
        CentralServiceReceiver.removeServiceListener(centralServiceListener)
    }
}