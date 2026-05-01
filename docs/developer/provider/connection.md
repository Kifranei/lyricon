# Provider 连接生命周期

`LyriconProvider` 负责与 Lyricon 中心服务建立连接。Provider 需要注册到中心服务后，才能发送歌曲、歌词和播放状态。

## 注册

```kotlin
provider.register()
```

`register()` 会向中心服务发送注册请求。返回值表示请求是否成功发出，不代表业务数据已经被显示。

## 注销

```kotlin
provider.unregister()
```

`unregister()` 用于主动断开当前中心服务连接。适合播放器退出、服务停止或临时不再推送歌词的场景。

## 销毁

```kotlin
provider.destroy()
```

`destroy()` 会释放监听器、注册回调和远端连接资源。通常在应用组件最终销毁时调用。

## 连接监听

```kotlin
provider.service.addConnectionListener {
    onConnected { }
    onReconnected { }
    onDisconnected { }
    onConnectTimeout { }
}
```

回调说明：

| 回调                 | 说明        |
|:-------------------|:----------|
| `onConnected`      | 首次连接成功    |
| `onReconnected`    | 断线后重新连接成功 |
| `onDisconnected`   | 连接断开      |
| `onConnectTimeout` | 连接中心服务超时  |

## 自动同步

```kotlin
provider.autoSync = true
```

`autoSync` 控制连接或重连成功后是否自动同步最近一次缓存的播放器状态。默认行为由实现决定，通常建议保持启用，避免中心服务重启后歌词状态丢失。

## 推荐实践

- 在播放器服务或应用级组件中持有 Provider，避免频繁创建。
- 注册后先同步当前歌曲，再同步播放状态和进度。
- 连接超时时提示用户检查 Lyricon、LSPosed 或 LocalCentralService 状态。
- 应用退出或服务停止时调用 `destroy()`。
