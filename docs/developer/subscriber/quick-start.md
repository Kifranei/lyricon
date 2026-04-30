# Subscriber 快速开始

本文档展示一个最小 Subscriber 接入流程，用于订阅 Lyricon 当前活跃播放器和歌词状态。

> [!WARNING]
> Subscriber 需要安装 [Lyricon 核心服务](https://github.com/tomakino/lyricon/releases/tag/core)。

## 添加依赖

![version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

在应用模块的 `build.gradle.kts` 中添加：

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

> [!WARNING]
> Subscriber Bridge 当前在 Android 8.1 以下会返回空实现。

## 创建 Subscriber

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)
```

创建时会根据当前应用包名和进程名生成 `SubscriberInfo`，中心服务通过该信息区分不同订阅端。

## 添加连接监听

```kotlin
subscriber.addConnectionListener(object : ConnectionListener {
    override fun onConnected(subscriber: LyriconSubscriber) {
        // 首次连接成功
    }

    override fun onReconnected(subscriber: LyriconSubscriber) {
        // 断线后重新连接成功
    }

    override fun onDisconnected(subscriber: LyriconSubscriber) {
        // 连接断开
    }

    override fun onConnectTimeout(subscriber: LyriconSubscriber) {
        // 连接中心服务超时
    }
})
```

## 订阅活跃播放器

只关心部分事件时，推荐使用 `SimpleActivePlayerListener`。

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        // 当前活跃播放器变化
    }

    override fun onSongChanged(song: Song?) {
        // 当前歌曲或结构化歌词变化
    }

    override fun onReceiveText(text: String?) {
        // 收到纯文本歌词
    }

    override fun onPositionChanged(position: Long) {
        // 播放进度变化，单位毫秒
    }
})
```

## 注册 Subscriber

```kotlin
subscriber.register()
```

注册成功后，中心服务会向 Subscriber 分发活跃播放器状态。

## 释放资源

```kotlin
subscriber.unregister()
subscriber.destroy()
```

`unregister()` 用于断开当前中心服务连接；`destroy()` 会释放监听器、注册回调和远程连接资源。
