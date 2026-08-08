# Subscriber 常见问题

## 为什么收不到回调？

可能原因：

- 未安装 Lyricon 核心服务。
- Subscriber 未调用 `register()`。
- 未调用 `subscribeActivePlayer()`。
- 当前没有活跃 Provider。
- 当前设备系统版本低于 Bridge 支持范围，返回了空实现。

## `providerInfo` 为什么是 null？

`providerInfo == null` 表示当前没有活跃播放器，或活跃 Provider 已断开。界面应清空当前歌曲和歌词状态。

## `onSongChanged()` 和 `onReceiveText()` 有什么区别？

`onSongChanged()` 接收结构化歌曲和歌词，适合时间轴歌词、逐字歌词、翻译歌词等场景。

`onReceiveText()` 接收纯文本歌词，没有结构化时间轴。

## `onPositionChanged()` 和 `onSeekTo()` 有什么区别？

`onPositionChanged()` 是普通播放进度更新。

`onSeekTo()` 表示发生了主动跳转，适合立即重置歌词渲染状态。

## Subscriber 需要 Root 或 LSPosed 吗？

Subscriber 应用本身不一定需要 Root 或 LSPosed，但需要能连接到 Lyricon 核心服务。具体运行条件以核心服务部署方式为准。

## Activity 销毁时需要调用什么？

如果 Subscriber 生命周期只绑定当前 `Activity`，建议在销毁时调用：

```kotlin
subscriber.destroy()
```

如果只是暂时停止接收，可以调用：

```kotlin
subscriber.unregister()
```

## 可以只监听一个回调吗？

可以。使用 `SimpleActivePlayerListener`，只覆写关心的方法即可。

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onReceiveText(text: String?) {
    }
})
```

## 可以同时存在多个 Subscriber 吗？

可以。中心服务通过 `SubscriberInfo` 区分不同应用或同一应用内不同进程的订阅者。
