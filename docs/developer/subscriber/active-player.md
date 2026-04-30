# Subscriber 活跃播放器

活跃播放器是 Lyricon 当前选择的数据来源。Subscriber 订阅活跃播放器后，可以接收该 Provider
推送的歌曲、歌词、播放状态和显示配置。

## 订阅活跃播放器

```kotlin
val listener = object : SimpleActivePlayerListener {
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        // 活跃 Provider 变化
    }
}

subscriber.subscribeActivePlayer(listener)
```

返回值表示订阅请求是否成功发出。

## 取消订阅

```kotlin
subscriber.unsubscribeActivePlayer(listener)
```

当界面或业务逻辑不再需要接收歌词状态时，应取消订阅，避免继续持有回调对象。

## ProviderInfo

`ProviderInfo` 是当前活跃 Provider 的信息快照。

| 字段                    | 说明              |
|:----------------------|:----------------|
| `providerPackageName` | 提供端应用包名         |
| `playerPackageName`   | 播放器应用包名         |
| `logo`                | 提供端或播放器图标，仅用于展示 |
| `metadata`            | 提供端附加元数据        |
| `processName`         | 播放器所在进程名        |

`ProviderInfo` 的相等性只比较 `providerPackageName`、`playerPackageName` 和 `processName`。`logo` 与
`metadata` 只作为展示信息，不参与身份判断。

## 无活跃播放器

```kotlin
override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
    if (providerInfo == null) {
        // 当前没有活跃播放器
    }
}
```

`providerInfo == null` 表示当前没有可用的活跃播放器。界面应清空或进入等待状态。

## 播放器切换

当活跃播放器发生切换时，Subscriber 会收到 `onActiveProviderChanged()`。后续的歌曲、歌词、播放状态和进度回调会来自新的活跃
Provider。

建议在 Provider 切换时：

- 清空旧播放器的临时 UI 状态。
- 等待新的 `onSongChanged()` 或 `onReceiveText()`。
- 将播放进度重置为新 Provider 回调中的值。
