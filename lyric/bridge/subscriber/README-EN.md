# Lyricon Lyric Subscription

> [!WARNING]
> This requires the installation of the [Core Service](https://github.com/tomakino/lyricon/releases/tag/core).

## 1. Add Dependency

![version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

Add the dependency in your `build.gradle.kts`:

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

## 2. Create `LyriconSubscriber`

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)
subscriber.subscribeActivePlayer(...)
subscriber.addConnectionListener(...)
subscriber.register()
```