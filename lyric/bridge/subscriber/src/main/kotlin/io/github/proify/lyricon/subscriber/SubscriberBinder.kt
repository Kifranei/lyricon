/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.subscriber

import java.util.concurrent.CopyOnWriteArraySet

internal class SubscriberBinder(
    val  subscriberInfo: SubscriberInfo
) : ISubscriberBinder.Stub() {

    val subscriberInfoByteArray by lazy {
        json.encodeToString(subscriberInfo).toByteArray()
    }

    private val registrationCallbacks = CopyOnWriteArraySet<RegistrationCallback>()

    fun addRegistrationCallback(callback: RegistrationCallback) {
        registrationCallbacks.add(callback)
    }

    fun removeRegistrationCallback(callback: RegistrationCallback) {}

    override fun onRegistrationCallback(service: IRemoteService?) {
        registrationCallbacks.forEach { it.onRegistered(service) }
    }

    override fun getSubscriberInfo(): ByteArray = subscriberInfoByteArray

    interface RegistrationCallback {
        fun onRegistered(service: IRemoteService?)
    }
}