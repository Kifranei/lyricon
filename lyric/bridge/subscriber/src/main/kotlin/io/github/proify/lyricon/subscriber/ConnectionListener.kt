/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

/**
 * 服务连接状态监听器。
 * 用于接收订阅者与远程服务之间连接生命周期的状态变更回调。
 */
interface ConnectionListener {
    /**
     * 当用户主动调用注册且连接成功时触发。
     * @param subscriber 触发该事件的订阅者实例。
     */
    fun onConnected(subscriber: LyriconSubscriber)

    /**
     * 当系统被动恢复（如服务重启或超时重试成功）且连接成功时触发。
     *  @param subscriber 触发该事件的订阅者实例。
     */
    fun onReconnected(subscriber: LyriconSubscriber)

    /**
     * 当连接断开时触发。
     * 包括用户主动注销以及远程服务端发起的断开。
     * @param subscriber 触发该事件的订阅者实例。
     */
    fun onDisconnected(subscriber: LyriconSubscriber)

    /**
     * 当连接尝试达到最大重试次数仍未成功时触发。
     *  @param subscriber 触发该事件的订阅者实例。
     */
    fun onConnectTimeout(subscriber: LyriconSubscriber)
}