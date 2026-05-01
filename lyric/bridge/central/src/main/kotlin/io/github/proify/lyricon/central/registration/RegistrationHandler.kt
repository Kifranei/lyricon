/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.registration

import android.content.Intent
import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.central.json
import io.github.proify.lyricon.central.provider.ProviderConnection
import io.github.proify.lyricon.central.provider.ProviderDirectory
import io.github.proify.lyricon.central.subscriber.SubscriberConnection
import io.github.proify.lyricon.central.subscriber.SubscriberDirectory
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.subscriber.ISubscriberBinder
import io.github.proify.lyricon.subscriber.SubscriberInfo

internal class RegistrationHandler(
    private val providers: ProviderDirectory,
    private val subscribers: SubscriberDirectory
) {

    fun handle(intent: Intent) {
        when (intent.action) {
            Constants.ACTION_REGISTER_PROVIDER -> registerProvider(intent)
            Constants.ACTION_REGISTER_SUBSCRIBER -> registerSubscriber(intent)
        }
    }

    private fun registerProvider(intent: Intent) {
        val binder = getBinder<IProviderBinder>(intent) ?: return
        var connection: ProviderConnection? = null

        try {
            val info = binder.providerInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(ProviderInfo.serializer(), it) }

            if (info?.providerPackageName.isNullOrBlank() || info.playerPackageName.isBlank()) {
                Log.e(TAG, "Provider info is invalid: $info")
                return
            }

            connection = providers.getOrCreate(binder, info)
            Log.d(TAG, "Provider registered: $info")
            binder.onRegistrationCallback(connection.service)
        } catch (e: Exception) {
            Log.e(TAG, "Provider registration failed", e)
            connection?.let { providers.unregister(it) }
        }
    }

    private fun registerSubscriber(intent: Intent) {
        val binder = getBinder<ISubscriberBinder>(intent) ?: return
        var connection: SubscriberConnection? = null

        try {
            val info = binder.subscriberInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(SubscriberInfo.serializer(), it) }

            if (info?.packageName.isNullOrBlank() || info.processName.isBlank()) {
                Log.e(TAG, "Subscriber info is invalid: $info")
                return
            }

            connection = subscribers.getOrCreate(binder, info)
            Log.d(TAG, "Subscriber registered: $info")
            binder.onRegistrationCallback(connection.service)
        } catch (e: Exception) {
            Log.e(TAG, "Subscriber registration failed", e)
            connection?.let { subscribers.unregister(it) }
        }
    }

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

    private companion object {
        private const val TAG = "RegistrationHandler"
    }
}
