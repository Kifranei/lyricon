# Provider 快速开始

本文档展示一个最小 Provider 接入流程，用于把播放器状态和歌词发送给 Lyricon。

## 添加依赖

![provider version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

在应用模块的 `build.gradle.kts` 中添加：

```kotlin
implementation("io.github.proify.lyricon:provider:0.1.70")
```

> [!WARNING]
> Provider Bridge 当前在 Android 8.1 以下会返回空实现。

## 创建 Provider

```kotlin
val provider = LyriconFactory.createProvider(context)
```

如果需要自定义注册信息，可以传入更多参数：

```kotlin
val provider = LyriconFactory.createProvider(
    context = context,
    providerPackageName = context.packageName,
    playerPackageName = context.packageName,
    logo = null,
    metadata = null
)
```

## 监听连接状态

```kotlin
provider.service.addConnectionListener {
    onConnected { provider ->
        // 首次连接成功
    }

    onReconnected { provider ->
        // 断线后重新连接成功
    }

    onDisconnected { provider ->
        // 连接断开
    }

    onConnectTimeout { provider ->
        // 连接中心服务超时
    }
}
```

## 注册 Provider

```kotlin
provider.register()
```

注册成功后，Lyricon 中心服务会开始接收该 Provider 推送的数据。

## 发送纯文本歌词

```kotlin
val player = provider.player

player.setPlaybackState(true)
player.sendText("我无法只是普通朋友")
```

`sendText()` 适用于不需要时间轴控制的简单歌词展示。调用该方法会清除之前设置的歌曲信息，使播放器进入纯文本模式。

## 发送结构化歌曲

```kotlin
player.setSong(
    Song(
        id = "song-id",
        name = "普通朋友",
        artist = "陶喆",
        duration = 2000,
        lyrics = listOf(
            RichLyricLine(
                begin = 0,
                end = 1000,
                text = "我无法只是普通朋友"
            ),
            RichLyricLine(
                begin = 1000,
                end = 2000,
                text = "不想做普通朋友"
            )
        )
    )
)

player.setPosition(100)
```

## 释放资源

```kotlin
provider.unregister()
provider.destroy()
```

`unregister()` 用于主动断开当前中心服务连接；`destroy()` 会释放监听器、注册回调和远端连接资源。
