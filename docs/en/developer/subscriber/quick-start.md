# Subscriber Quick Start

This page shows the minimal Subscriber integration flow.

Subscriber requires the Lyricon core service.

## Dependency

![subscriber version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

Subscriber returns an empty implementation on Android versions earlier than 8.1.

## Create Subscriber

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)
```

## Subscribe

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onSongChanged(song: Song?) {
    }

    override fun onReceiveText(text: String?) {
    }
})
```

## Register

```kotlin
subscriber.register()
```

## Release

```kotlin
subscriber.unregister()
subscriber.destroy()
```
