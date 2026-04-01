/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.provider

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import io.github.proify.lyricon.subscriber.impl.ActivePlayerMonitorV27Impl
import io.github.proify.lyricon.subscriber.impl.EmptyActivePlayerMonitor
import io.github.proify.lyricon.provider.impl.EmptyProvider
import io.github.proify.lyricon.provider.impl.ProviderV27Impl
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ActivePlayerMonitor

/**
 * LyriconProvider 工厂对象
 */
object LyriconFactory {

    /**
     * 创建歌词提供者
     *
     * @param context 上下文
     * @param providerPackageName 提供者包名
     * @param playerPackageName 播放器包名
     * @param logo Provider 的图标信息
     * @param metadata Provider 的元数据描述
     * @param processName 播放器进程名
     * @param providerService 本地服务实现
     * @param centralPackageName 中央服务实现包名
     */
    fun createProvider(
        context: Context,
        providerPackageName: String = context.packageName,
        playerPackageName: String = providerPackageName,
        logo: ProviderLogo? = null,
        metadata: ProviderMetadata? = null,
        processName: String? = getCurrentProcessName(context),
        providerService: ProviderService? = null,
        centralPackageName: String = ProviderConstants.SYSTEM_UI_PACKAGE_NAME,
    ): LyriconProvider = createProvider(
        context,
        ProviderInfo(
            providerPackageName = providerPackageName,
            playerPackageName = playerPackageName,
            logo = logo,
            metadata = metadata,
            processName = processName
        ),
        providerService,
        centralPackageName
    )

    /**
     * 创建歌词提供者
     *
     * @param context 上下文
     * @param providerInfo Provider 的基础信息封装
     * @param providerService 本地服务实现
     * @param centralPackageName 中央服务实现包名
     */
    fun createProvider(
        context: Context,
        providerInfo: ProviderInfo,
        providerService: ProviderService? = null,
        centralPackageName: String = ProviderConstants.SYSTEM_UI_PACKAGE_NAME,
    ): LyriconProvider {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            initialize(context)

            return ProviderV27Impl(
                context,
                providerInfo,
                providerService,
                centralPackageName
            )
        }

        return EmptyProvider(providerInfo)
    }

    private fun initialize(context: Context) {
        if (!CentralServiceReceiver.isInitialized) {
            CentralServiceReceiver.initialize(context)
        }
    }

    /**
     * 创建活跃播放器监听器监控器。
     *
     * 可独立于 [createProvider] 使用，仅用于接收 Central 回传的活跃播放器状态。
     * @param context 上下文
     * @param centralPackageName 中央服务实现包名
     * @param listener 播放器监听器
     */
    fun createActivePlayerMonitor(
        context: Context,
        centralPackageName: String = ProviderConstants.SYSTEM_UI_PACKAGE_NAME,
        listener: ActivePlayerListener? = null,
    ): ActivePlayerMonitor {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            initialize(context)
            return ActivePlayerMonitorV27Impl(
                context = context,
                centralPackageName = centralPackageName,
                activePlayerListener = listener
            )
        }

        return EmptyActivePlayerMonitor()
    }

    fun getCurrentProcessName(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
    }
}
