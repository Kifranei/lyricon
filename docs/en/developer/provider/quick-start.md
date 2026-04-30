# Provider Quick Start

This page shows the minimal Provider integration flow.

## Dependency

![provider version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

```kotlin
implementation("io.github.proify.lyricon:provider:0.1.70")
```

Provider returns an empty implementation on Android versions earlier than 8.1.

## Create Provider

```kotlin
val provider = LyriconFactory.createProvider(context)
```

## Register

```kotlin
provider.register()
```

## Send Lyrics

```kotlin
val player = provider.player

player.setPlaybackState(true)
player.sendText("I can't just be an ordinary friend")
```

## Release

```kotlin
provider.unregister()
provider.destroy()
```
