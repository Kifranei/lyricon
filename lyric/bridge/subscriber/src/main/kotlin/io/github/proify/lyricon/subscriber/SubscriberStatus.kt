/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.subscriber

/** 订阅端与中心服务之间的连接状态。 */
enum class SubscriberStatus {
    /** 未连接或已由本地主动断开。 */
    DISCONNECTED,

    /** 远端 Binder 死亡或中心服务主动断开。 */
    DISCONNECTED_BY_REMOTE,

    /** 注册广播已发送，正在等待中心服务回调。 */
    CONNECTING,

    /** 已完成注册并绑定远端服务。 */
    CONNECTED;

    /** 是否正在连接中。 */
    fun isConnecting(): Boolean = this == CONNECTING

    /** 是否已连接。 */
    fun isConnected(): Boolean = this == CONNECTED

    /** 是否已断开连接。 */
    fun isDisconnected(): Boolean = this == DISCONNECTED

    /** 是否已由远端主动断开连接。 */
    fun isDisconnectedByRemote(): Boolean = this == DISCONNECTED_BY_REMOTE
}
