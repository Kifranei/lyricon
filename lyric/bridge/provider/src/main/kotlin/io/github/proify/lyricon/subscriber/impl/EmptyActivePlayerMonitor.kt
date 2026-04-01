/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber.impl

import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ActivePlayerMonitor

internal class EmptyActivePlayerMonitor : ActivePlayerMonitor {
    override var activePlayerListener: ActivePlayerListener? = null

    override fun addActivePlayerListener(listener: ActivePlayerListener): Boolean = false

    override fun removeActivePlayerListener(listener: ActivePlayerListener): Boolean = false

    override fun register(): Boolean = false

    override fun unregister(): Boolean = false

    override fun destroy(): Boolean = false
}
