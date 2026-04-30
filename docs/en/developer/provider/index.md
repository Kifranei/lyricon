# Provider

Provider sends songs, lyrics, playback state, and display options to Lyricon. It is intended for music players and lyric provider plugins.

## Flow

1. Add the Provider dependency.
2. Configure `AndroidManifest.xml` metadata.
3. Create a `LyriconProvider`.
4. Call `register()`.
5. Send data through `provider.player`.
6. Call `unregister()` or `destroy()` when it is no longer needed.

## Dependency

![provider version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

```kotlin
implementation("io.github.proify.lyricon:provider:0.1.70")
```

## Minimal Example

```kotlin
val provider = LyriconFactory.createProvider(context)

provider.register()

provider.player.setPlaybackState(true)
provider.player.sendText("I can't just be an ordinary friend")
```

## More

- [Quick Start](quick-start.md)
