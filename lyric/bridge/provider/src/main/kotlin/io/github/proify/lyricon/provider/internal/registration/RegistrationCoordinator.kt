/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.provider.internal.registration

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderConstants.ACTION_REGISTER_PROVIDER
import io.github.proify.lyricon.provider.ProviderConstants.EXTRA_BINDER
import io.github.proify.lyricon.provider.isConnected
import io.github.proify.lyricon.provider.isConnecting
import io.github.proify.lyricon.provider.internal.binding.RegistrationBinder
import io.github.proify.lyricon.provider.internal.connection.CentralConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 注册协调器：发送注册广播、管理连接超时与失败重试，并在中心服务重启后恢复注册。
 *
 * 每次 [start] 建立一次注册尝试；[cancel] 结束尝试并停止重试，之后到达的中心回调
 * 会被 [RegistrationBinder] 忽略，从而同时覆盖"断开已连接"与"取消连接中"两种语义。
 *
 * [wantsConnection] 记录提供端是否期望保持连接：为 true 时，
 * - 中心服务启动广播触发重新注册；
 * - 注册失败/超时按指数退避自动重试（1s 起，封顶 [MAX_RETRY_DELAY_MS]），
 *   覆盖中心暂时不可用、广播丢失等场景，无需等待下一次中心启动。
 *
 * @property context 发送注册广播的上下文。
 * @property centralPackageName 中心服务所在包名。
 * @property registrationBinder 注册 AIDL 桩。
 * @property connection 连接端点。
 * @property scope 超时与重试协程的宿主作用域。
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class RegistrationCoordinator(
    private val context: Context,
    private val centralPackageName: String,
    private val registrationBinder: RegistrationBinder,
    private val connection: CentralConnection,
    private val scope: CoroutineScope,
) : CentralBootReceiver.BootListener {

    /** 当前注册尝试的超时协程。 */
    private var timeoutJob: Job? = null

    /** 失败重试协程。 */
    private var retryJob: Job? = null

    /** 连接健康度看门狗协程。 */
    private var watchdogJob: Job? = null

    /** 期望保持连接（register 时置位，unregister/destroy 时复位）。 */
    @Volatile
    private var wantsConnection: Boolean = false

    /** 下一次失败重试的等待时间（指数退避）。 */
    @Volatile
    private var retryDelayMs: Long = INITIAL_RETRY_DELAY_MS

    /** 当前注册尝试的回调：成功后停止重试，失败后安排下一次重试。 */
    private val attempt = object : RegistrationBinder.RegistrationAttempt {
        override fun onCompleted(connected: Boolean) {
            cancelTimeout()
            if (connected) {
                cancelRetry()
            } else {
                scheduleRetry()
            }
            registrationBinder.endAttempt(this)
        }
    }

    init {
        CentralBootReceiver.addBootListener(this)
    }

    /** 发出注册广播；返回是否真正启动了一次注册。 */
    fun start(): Boolean {
        if (centralPackageName.isBlank()) return false
        // 用户/外部主动触发：取消待定重试，退避回到起点。
        cancelRetry()
        retryDelayMs = INITIAL_RETRY_DELAY_MS
        // 状态机权威判定：连接中或已连接时不重复注册，避免破坏现有连接。
        if (!connection.beginRegistration()) return false

        wantsConnection = true
        startWatchdog()
        registrationBinder.beginAttempt(attempt)
        scheduleTimeout()
        context.sendBroadcast(Intent(ACTION_REGISTER_PROVIDER).apply {
            setPackage(centralPackageName)
            putExtra(
                ProviderConstants.EXTRA_BUNDLE,
                Bundle().apply {
                    putBinder(EXTRA_BINDER, registrationBinder)
                }
            )
        })
        return true
    }

    override fun onBootCompleted() {
        // 中心服务重建后，本地连接状态不再可信：
        // - 状态为 CONNECTED 时，远端 Binder 常常仍然存活（中心进程并未退出，只是
        //   内部注册表随服务重建被清空），死亡监听不会触发，本端会永久停在
        //   "自认为已连接"的僵死态；
        // - 状态为 CONNECTING 时，上一次注册广播可能已丢失，超时协程也可能因
        //   进程被冻结而未推进。
        // 因此这里不再以 isDisconnected() 为前提，而是无条件重走一次完整注册。
        if (!wantsConnection) return
        forceReregister()
    }

    /** 取消当前注册尝试：清空超时与重试、复位期望连接标记，并让迟到回调失效。 */
    fun cancel() {
        wantsConnection = false
        cancelTimeout()
        cancelRetry()
        stopWatchdog()
        registrationBinder.endAttempt(attempt)
    }

    /** 销毁：取消全部等待并停止监听中心启动广播。 */
    fun close() {
        cancel()
        CentralBootReceiver.removeBootListener(this)
    }

    /**
     * 强制重新注册：丢弃当前连接（无论其处于何种状态），重新发起一次注册尝试。
     *
     * 这是"本地状态不可信"场景下的唯一可靠恢复路径。常规的 [start] 会被
     * [CentralConnection.beginRegistration] 在 CONNECTING/CONNECTED 状态下拒绝，
     * 恰好在最需要恢复的时候失效。
     */
    private fun forceReregister() {
        cancelTimeout()
        cancelRetry()
        retryDelayMs = INITIAL_RETRY_DELAY_MS
        // 结束悬挂的注册尝试，避免中心的迟到回调被上一个 attempt 误消费。
        registrationBinder.endAttempt(attempt)
        connection.resetForRegistration()
        start()
    }

    /** 启动连接健康度看门狗（幂等）。 */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                // 单次检查失败不得终止看门狗本身，否则会退回"无人兜底"的僵死态。
                runCatching { checkConnectionHealth() }
                    .onFailure { Log.w(TAG, "Connection health check failed", it) }
            }
        }
    }

    /** 停止连接健康度看门狗。 */
    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    /**
     * 一次连接健康度检查。
     *
     * 只兜底两种"状态与事实脱节"的僵死形态；正常断开后的恢复仍由 [scheduleRetry]
     * 的指数退避负责，因此这里不对 DISCONNECTED 状态做任何事，避免与重试协程互相打断。
     */
    private fun checkConnectionHealth() {
        if (!wantsConnection) return

        val status = connection.connectionStatus
        when {
            // Binder 死亡通知丢失：本地仍认为已连接，但远端实际已不可用。
            status.isConnected() && !connection.isActive -> forceReregister()

            // 停在注册中，但超时协程已经不在了：广播丢失或协程未能推进，
            // 这时状态机自己永远走不出来。
            status.isConnecting() && timeoutJob?.isActive != true -> forceReregister()

            else -> Unit
        }
    }

    /** 取消注册超时等待。 */
    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /** 取消待定的失败重试。 */
    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    /** 启动注册超时等待；超时后结束尝试并按退避安排重试。 */
    private fun scheduleTimeout() {
        cancelTimeout()
        timeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            connection.onRegistrationTimeout()
            registrationBinder.endAttempt(attempt)
            scheduleRetry()
        }
    }

    /** 注册失败/超时后按指数退避安排一次重试（仅当仍期望连接时）。 */
    private fun scheduleRetry() {
        if (!wantsConnection) return
        cancelRetry()
        retryJob = scope.launch {
            delay(retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            start()
        }
    }

    private companion object {
        private const val CONNECTION_TIMEOUT_MS = 4_000L
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L

        /** 连接健康度看门狗的检查周期。 */
        private const val WATCHDOG_INTERVAL_MS = 60_000L

        private const val TAG = "RegistrationCoordinator"
    }
}
