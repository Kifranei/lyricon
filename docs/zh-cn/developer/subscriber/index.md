# Subscriber

Subscriber 用于订阅当前活跃播放器、歌曲、歌词、播放状态和显示配置。适用于需要读取词幕数据的第三方应用。

## 接入流程

1. 添加 Subscriber 依赖。
2. 创建 `LyriconSubscriber`。
3. 注册连接监听。
4. 调用 `subscribeActivePlayer()` 订阅状态。
5. 调用 `register()` 注册。
6. 不再使用时调用 `unregister()` 或 `destroy()`。

## 依赖

![subscriber version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

## 文档

- [快速开始](quick-start.md)
- [连接生命周期](connection.md)
- [活跃播放器](active-player.md)
- [回调说明](callbacks.md)
- [常见问题](faq.md)

## 最小示例

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)

subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onSongChanged(song: Song?) {
    }
})

subscriber.register()
```
