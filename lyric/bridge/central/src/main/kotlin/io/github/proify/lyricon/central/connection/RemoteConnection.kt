/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.connection

internal interface RemoteConnection<K> {
    val key: K

    fun setDeathRecipient(onDeath: (() -> Unit)?)

    fun close()
}
