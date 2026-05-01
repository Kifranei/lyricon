/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central

import io.github.proify.lyricon.central.provider.ProviderDirectory
import io.github.proify.lyricon.central.provider.player.ActivePlayerCoordinator
import io.github.proify.lyricon.central.registration.RegistrationHandler
import io.github.proify.lyricon.central.subscriber.SubscriberDirectory

internal object CentralRuntime {
    val activePlayers = ActivePlayerCoordinator()
    val providers = ProviderDirectory(activePlayers)
    val subscribers = SubscriberDirectory()
    val registration = RegistrationHandler(providers, subscribers)
}
