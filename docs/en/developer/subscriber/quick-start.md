# Subscriber Quick Start

This page shows the minimal Subscriber integration flow for subscribing to Lyricon's active player
and lyric state.

> [!WARNING]
> Subscriber requires
> the [Lyricon core service](https://github.com/tomakino/lyricon/releases/tag/core).

## Dependency

![subscriber version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

Add the dependency in your app module's `build.gradle.kts`:

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

> [!WARNING]
> Subscriber Bridge returns an empty implementation on Android versions earlier than 8.1.

## Create Subscriber

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)
```

`SubscriberInfo` is generated from the current app package name and process name. The central
service
uses it to distinguish different subscribers.

## Listen for Connection State

```kotlin
subscriber.addConnectionListener(object : ConnectionListener {
    override fun onConnected(subscriber: LyriconSubscriber) {
        // First connection succeeded
    }

    override fun onReconnected(subscriber: LyriconSubscriber) {
        // Reconnected after disconnect
    }

    override fun onDisconnected(subscriber: LyriconSubscriber) {
        // Connection disconnected
    }

    override fun onConnectTimeout(subscriber: LyriconSubscriber) {
        // Connection to central service timed out
    }
})
```

## Subscribe to Active Player

Use `SimpleActivePlayerListener` when only some events are needed.

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        // Active player changed
    }

    override fun onSongChanged(song: Song?) {
        // Current song or structured lyrics changed
    }

    override fun onReceiveText(text: String?) {
        // Received plain text lyrics
    }

    override fun onPositionChanged(position: Long) {
        // Playback position changed, in milliseconds
    }
})
```

## Register

```kotlin
subscriber.register()
```

After registration succeeds, the central service starts dispatching active player state to the
Subscriber.

## Release

```kotlin
subscriber.unregister()
subscriber.destroy()
```

`unregister()` disconnects from the current central service connection. `destroy()` releases
listeners, callbacks, and remote connection resources.
