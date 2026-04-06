# Lyricon 歌词订阅

## 一、添加依赖

![version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

在`build.gradle.kts` 中添加依赖：

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

## 二、创建 `LyriconSubscriber`

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)
subscriber.subscribeActivePlayer(...)
subscriber.addConnectionListener(...)
subscriber.register()
```