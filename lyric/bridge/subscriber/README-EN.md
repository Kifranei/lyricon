# Lyricon Lyrics Subscription

## 1. Adding Dependencies
![version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

Add dependencies in `build.gradle.kts`:
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