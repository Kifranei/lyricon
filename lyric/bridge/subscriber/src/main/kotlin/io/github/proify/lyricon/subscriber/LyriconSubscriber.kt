/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

interface LyriconSubscriber {
    val subscriberInfo: SubscriberInfo
    fun addConnectionListener(listener: ConnectionListener)
    fun removeConnectionListener(listener: ConnectionListener)
    fun subscribeActivePlayer(listener: ActivePlayerListener): Boolean
    fun unsubscribeActivePlayer(listener: ActivePlayerListener): Boolean
    fun register()
    fun unregister()
    fun destroy()
}