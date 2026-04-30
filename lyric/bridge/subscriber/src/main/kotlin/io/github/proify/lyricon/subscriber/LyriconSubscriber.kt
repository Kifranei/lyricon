/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

/**
 * Lyricon 订阅端入口。
 *
 * 订阅端用于注册到中心服务，监听当前活跃播放器变化，并接收连接生命周期回调。
 * 实例通常由 [LyriconFactory.createSubscriber] 创建。
 */
interface LyriconSubscriber {
    /** 注册到中心服务时上报的订阅端信息。 */
    val subscriberInfo: SubscriberInfo

    /** 添加连接生命周期监听器。 */
    fun addConnectionListener(listener: ConnectionListener)

    /** 移除之前添加的连接生命周期监听器。 */
    fun removeConnectionListener(listener: ConnectionListener)

    /** 订阅当前活跃播放器状态。 */
    fun subscribeActivePlayer(listener: ActivePlayerListener): Boolean

    /** 取消 [listener] 对当前活跃播放器状态的订阅。 */
    fun unsubscribeActivePlayer(listener: ActivePlayerListener): Boolean

    /** 向中心服务发送注册请求。 */
    fun register()

    /** 断开当前中心服务连接。 */
    fun unregister()

    /** 释放监听器、注册回调和远程连接资源。 */
    fun destroy()
}
