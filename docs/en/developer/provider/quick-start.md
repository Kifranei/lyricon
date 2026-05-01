# Provider Quick Start

This page shows the minimal Provider integration flow for sending player state and lyrics to
Lyricon.

## Dependency

![provider version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

Add the dependency in your app module's `build.gradle.kts`:

```kotlin
implementation("io.github.proify.lyricon:provider:0.1.70")
```

> [!WARNING]
> Provider Bridge returns an empty implementation on Android versions earlier than 8.1.

## Create Provider

```kotlin
val provider = LyriconFactory.createProvider(context)
```

To customize registration info, pass more parameters:

```kotlin
val provider = LyriconFactory.createProvider(
    context = context,
    providerPackageName = context.packageName,
    playerPackageName = context.packageName,
    logo = null,
    metadata = null
)
```

## Listen for Connection State

```kotlin
provider.service.addConnectionListener {
    onConnected { provider ->
        // First connection succeeded
    }

    onReconnected { provider ->
        // Reconnected after disconnect
    }

    onDisconnected { provider ->
        // Connection disconnected
    }

    onConnectTimeout { provider ->
        // Connection to central service timed out
    }
}
```

## Register

```kotlin
provider.register()
```

After registration succeeds, the Lyricon central service starts receiving data from this Provider.

## Send Lyrics

```kotlin
val player = provider.player

player.setPlaybackState(true)
player.sendText("I can't just be an ordinary friend")
```

`sendText()` is suitable for simple lyrics display without timeline control. Calling it clears the
previously set song and switches the player to plain-text mode.

## Send Structured Song

```kotlin
player.setSong(
    Song(
        id = "song-id",
        name = "Ordinary Friend",
        artist = "David Tao",
        duration = 2000,
        lyrics = listOf(
            RichLyricLine(
                begin = 0,
                end = 1000,
                text = "I can't just be an ordinary friend"
            ),
            RichLyricLine(
                begin = 1000,
                end = 2000,
                text = "Don't want to be just friends"
            )
        )
    )
)

player.setPosition(100)
```

## Release

```kotlin
provider.unregister()
provider.destroy()
```

`unregister()` disconnects from the current central service connection. `destroy()` releases
listeners, callbacks, and remote connection resources.
