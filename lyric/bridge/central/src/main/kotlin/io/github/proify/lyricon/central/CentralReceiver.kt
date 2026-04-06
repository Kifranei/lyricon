/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.proify.lyricon.central.CentralReceiver.pendingProviders
import io.github.proify.lyricon.central.provider.ProviderManager
import io.github.proify.lyricon.central.provider.RemoteProvider
import io.github.proify.lyricon.central.subscriber.RemoteSubscriber
import io.github.proify.lyricon.central.subscriber.SubscriberManager
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.subscriber.ISubscriberBinder
import io.github.proify.lyricon.subscriber.SubscriberInfo

/**
 * Lyricon 中央接收器，负责处理来自不同进程的 Provider 和 Subscriber 注册广播。
 * * 优化点：
 * 1. 引入恢复期 (Recovery Phase) 机制，启动初期 Provider 进入 [pendingProviders] 队列。
 * 2. Subscriber 始终优先立即注册。
 * 3. 恢复期结束后一次性冲刷队列，后续 Provider 注册转为即时处理。
 */
internal object CentralReceiver : BroadcastReceiver() {

    private const val TAG = "CentralReceiver"

    /**
     * 恢复期持续时间，在此时间内 Provider 会等待 Subscriber 注册
     * 应该小于4000ms，因为Provider连接超时时间为4000ms
     */
    private const val RECOVERY_WINDOW_MS = 3000L

    /** 待注册的 Provider Intent 队列 */
    private val pendingProviders = mutableListOf<Intent>()

    /** 是否处于恢复期 */
    private var isRecoveryPhase = true

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 结束恢复期并处理所有积压 Provider 的任务 */
    private val flushTask = Runnable {
        if (!isRecoveryPhase) return@Runnable
        Log.d(TAG, "Recovery phase ended. Flushing ${pendingProviders.size} pending providers.")
        isRecoveryPhase = false

        // 批量处理积压的 Provider
        val iterator = pendingProviders.iterator()
        while (iterator.hasNext()) {
            val intent = iterator.next()
            registerProvider(intent)
            iterator.remove()
        }
    }

    init {
        // 启动即开启恢复期倒计时
        mainHandler.postDelayed(flushTask, RECOVERY_WINDOW_MS)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received intent: $action")

        when (action) {
            Constants.ACTION_REGISTER_SUBSCRIBER -> {
                // 订阅者具有最高优先级，始终立即注册
                registerSubscriber(intent)
            }
            Constants.ACTION_REGISTER_PROVIDER -> {
                if (isRecoveryPhase) {
                    // 恢复期内，将 Provider 放入队列暂存
                    pendingProviders.add(intent)
                    Log.d(TAG, "Provider registration pended during recovery.")
                } else {
                    // 恢复期后，直接处理
                    registerProvider(intent)
                }
            }
        }
    }

    /**
     * 从 Intent 的 Bundle 中安全地提取并转换指定类型的 AIDL Binder。
     */
    private inline fun <reified T> getBinder(intent: Intent): T? = runCatching {
        val binder = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
            ?.getBinder(Constants.EXTRA_BINDER) ?: return null

        when (T::class) {
            IProviderBinder::class -> IProviderBinder.Stub.asInterface(binder) as? T
            ISubscriberBinder::class -> ISubscriberBinder.Stub.asInterface(binder) as? T
            else -> {
                Log.e(TAG, "Unknown binder type: ${T::class.java.simpleName}")
                null
            }
        }
    }.onFailure {
        Log.e(TAG, "Failed to get binder from intent", it)
    }.getOrNull()

    /**
     * 处理 Provider 的注册逻辑。
     */
    private fun registerProvider(intent: Intent) {
        val binder = getBinder<IProviderBinder>(intent) ?: return
        var provider: RemoteProvider? = null

        try {
            val providerInfo = binder.providerInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(ProviderInfo.serializer(), it) }

            if (providerInfo?.providerPackageName.isNullOrBlank() ||
                providerInfo.playerPackageName.isBlank()
            ) {
                Log.e(TAG, "Provider info is invalid: $providerInfo")
                return
            }

            val registered = ProviderManager.getProvider(providerInfo)
            if (registered != null) {
                provider = registered
                Log.w(
                    TAG,
                    "Provider already registered, Sharing the same player service $providerInfo"
                )
            } else {
                provider = RemoteProvider(binder, providerInfo)
                ProviderManager.register(provider)
                Log.d(TAG, "Provider registered: $providerInfo")
            }

            binder.onRegistrationCallback(provider.service)

        } catch (e: Exception) {
            Log.e(TAG, "Provider registration failed", e)
            provider?.let { ProviderManager.unregister(it) }
        }
    }

    /**
     * 处理 Subscriber 的注册逻辑。
     */
    private fun registerSubscriber(intent: Intent) {
        val binder = getBinder<ISubscriberBinder>(intent) ?: return
        var subscriber: RemoteSubscriber? = null

        try {
            val subscriberInfo = binder.subscriberInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(SubscriberInfo.serializer(), it) }

            if (subscriberInfo?.packageName.isNullOrBlank() ||
                subscriberInfo.processName.isBlank()
            ) {
                Log.e(TAG, "Subscriber info is invalid: $subscriberInfo")
                return
            }

            val registered = SubscriberManager.getSubscriber(subscriberInfo)
            if (registered != null) {
                subscriber = registered
                Log.w(
                    TAG,
                    "Subscriber already registered, Sharing the same player service $subscriberInfo"
                )
            } else {
                subscriber = RemoteSubscriber(binder, subscriberInfo)
                SubscriberManager.register(subscriber)
                Log.d(TAG, "Subscriber registered: $subscriberInfo")
            }

            binder.onRegistrationCallback(subscriber.service)

        } catch (e: Exception) {
            Log.e(TAG, "Subscriber registration failed", e)
            subscriber?.let { SubscriberManager.unregister(it) }
        }
    }
}