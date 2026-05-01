/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.connection

import java.util.concurrent.ConcurrentHashMap

internal class ConnectionRegistry<K, C : RemoteConnection<K>> {

    private val connections = ConcurrentHashMap<K, C>()

    fun register(connection: C): C {
        val existing = connections.putIfAbsent(connection.key, connection)
        if (existing != null) return existing

        connection.setDeathRecipient { unregister(connection.key) }
        return connection
    }

    fun unregister(key: K): C? {
        val removed = connections.remove(key) ?: return null
        removed.close()
        return removed
    }

    fun unregister(connection: C): Boolean {
        val removed = connections.remove(connection.key, connection)
        if (removed) connection.close()
        return removed
    }

    fun get(key: K): C? = connections[key]
}
