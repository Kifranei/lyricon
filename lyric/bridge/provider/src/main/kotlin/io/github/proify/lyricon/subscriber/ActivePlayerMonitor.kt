/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

interface ActivePlayerMonitor {

    var activePlayerListener: ActivePlayerListener?

    fun addActivePlayerListener(listener: ActivePlayerListener): Boolean

    fun removeActivePlayerListener(listener: ActivePlayerListener): Boolean

    fun register(): Boolean

    fun unregister(): Boolean

    fun destroy(): Boolean
}
