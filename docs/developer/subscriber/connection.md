# Subscriber 连接生命周期

`LyriconSubscriber` 负责连接 Lyricon 中心服务。订阅端需要注册后才能接收活跃播放器和歌词状态。

## 注册

```kotlin
subscriber.register()
```

`register()` 会向中心服务发送注册请求。注册成功后，Subscriber 才能接收订阅回调。

## 注销

```kotlin
subscriber.unregister()
```

`unregister()` 用于断开当前中心服务连接。适合应用进入不需要监听的状态，或服务停止时调用。

## 销毁

```kotlin
subscriber.destroy()
```

`destroy()` 会释放监听器、注册回调和远程连接资源。通常在组件最终销毁时调用。

## 连接监听

```kotlin
val listener = object : ConnectionListener {
    override fun onConnected(subscriber: LyriconSubscriber) {}
    override fun onReconnected(subscriber: LyriconSubscriber) {}
    override fun onDisconnected(subscriber: LyriconSubscriber) {}
    override fun onConnectTimeout(subscriber: LyriconSubscriber) {}
}

subscriber.addConnectionListener(listener)
```

不再需要时可以移除：

```kotlin
subscriber.removeConnectionListener(listener)
```

回调说明：

| 回调                 | 说明               |
|:-------------------|:-----------------|
| `onConnected`      | 用户主动注册且连接成功      |
| `onReconnected`    | 服务重启或超时重试后连接恢复   |
| `onDisconnected`   | 连接断开，包括主动注销和远端断开 |
| `onConnectTimeout` | 达到最大重试次数仍未连接成功   |

## 生命周期建议

- 如果在 `Activity` 中使用，建议在 `onStart()` 或 `onCreate()` 注册，在 `onStop()` 或 `onDestroy()`
  释放，具体取决于是否需要后台监听。
- 如果在 `Service` 中使用，建议随服务创建和销毁管理 Subscriber。
- 不要假设 `register()` 调用后立即有数据，应以连接回调和订阅回调为准。
- 连接断开后，界面应能处理歌曲、歌词或 Provider 为空的状态。
