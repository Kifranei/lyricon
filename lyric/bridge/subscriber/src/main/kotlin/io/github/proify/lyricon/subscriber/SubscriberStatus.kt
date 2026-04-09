/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.subscriber

enum class SubscriberStatus {
    DISCONNECTED,
    DISCONNECTED_BY_REMOTE,

    CONNECTING,
    CONNECTED;

    fun isConnecting(): Boolean = this == CONNECTING
    fun isConnected(): Boolean = this == CONNECTED
    fun isDisconnected(): Boolean = this == DISCONNECTED
    fun isDisconnectedByRemote(): Boolean = this == DISCONNECTED_BY_REMOTE
}