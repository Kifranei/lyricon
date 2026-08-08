/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import io.github.proify.lyricon.central.CentralRuntime

object ActivePlayerCenter {

    fun addListener(listener: ActivePlayerListener) {
        CentralRuntime.activePlayers.addListener(listener)
    }

    fun removeListener(listener: ActivePlayerListener) {
        CentralRuntime.activePlayers.removeListener(listener)
    }
}
