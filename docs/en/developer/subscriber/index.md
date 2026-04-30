# Subscriber

Subscriber receives the active player, song, lyrics, playback state, and display options from Lyricon. It is intended for third-party apps that need to read Lyricon data.

## Flow

1. Add the Subscriber dependency.
2. Create a `LyriconSubscriber`.
3. Register connection listeners.
4. Call `subscribeActivePlayer()`.
5. Call `register()`.
6. Call `unregister()` or `destroy()` when it is no longer needed.

## Dependency

![subscriber version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

## Minimal Example

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)

subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onSongChanged(song: Song?) {
    }
})

subscriber.register()
```

## More

- [Quick Start](quick-start.md)
