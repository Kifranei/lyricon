# Subscriber 回调说明

Subscriber 通过 `ActivePlayerListener` 接收活跃播放器状态。只关心部分事件时，推荐实现
`SimpleActivePlayerListener`。

## 回调总览

| 回调                            | 触发时机         | 主要用途      |
|:------------------------------|:-------------|:----------|
| `onActiveProviderChanged`     | 活跃播放器变化      | 更新当前来源    |
| `onSongChanged`               | 当前歌曲或结构化歌词变化 | 渲染歌曲和歌词数据 |
| `onReceiveText`               | 收到纯文本歌词      | 显示无时间轴文本  |
| `onPlaybackStateChanged`      | 播放/暂停变化      | 控制动画或播放状态 |
| `onPositionChanged`           | 播放进度更新       | 驱动歌词滚动    |
| `onSeekTo`                    | 主动跳转进度       | 立即校准显示状态  |
| `onDisplayTranslationChanged` | 翻译显示开关变化     | 控制翻译歌词 UI |
| `onDisplayRomaChanged`        | 罗马音显示开关变化    | 控制罗马音 UI  |

## onActiveProviderChanged

```kotlin
override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
}
```

当活跃播放器应用发生切换时触发。`providerInfo == null` 表示当前没有活跃播放器。

## onSongChanged

```kotlin
override fun onSongChanged(song: Song?) {
}
```

当当前播放歌曲或结构化歌词发生变化时触发。`song == null` 表示当前歌曲已清空。

## onReceiveText

```kotlin
override fun onReceiveText(text: String?) {
}
```

当 Provider 发送纯文本歌词时触发。一些 Provider 可能会发送多行文本，例如：

```text
你好 世界
Hello world
```

如果应用只支持结构化歌词，也应该处理纯文本回调，避免界面没有任何反馈。

## onPlaybackStateChanged

```kotlin
override fun onPlaybackStateChanged(isPlaying: Boolean) {
}
```

当播放状态变化时触发。

- `true`：播放中
- `false`：暂停

## onPositionChanged

```kotlin
override fun onPositionChanged(position: Long) {
}
```

当播放进度更新时触发，单位为毫秒。可用于驱动歌词滚动或逐字进度。

## onSeekTo

```kotlin
override fun onSeekTo(position: Long) {
}
```

当发生主动跳转进度操作时触发，单位为毫秒。与普通进度更新相比，该回调更适合立即重置渲染状态。

## onDisplayTranslationChanged

```kotlin
override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
}
```

当是否显示翻译歌词的配置变化时触发。该回调只表示显示开关变化，不保证当前歌词一定包含翻译内容。

## onDisplayRomaChanged

```kotlin
override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
}
```

当是否显示罗马音的配置变化时触发。该回调只表示显示开关变化，不保证当前歌词一定包含罗马音内容。

## SimpleActivePlayerListener

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onSongChanged(song: Song?) {
    }
})
```

`SimpleActivePlayerListener` 为所有回调提供空实现，适合只关心少量事件的场景。

## 容错建议

- 所有回调都应允许参数为空。
- 不要依赖固定回调顺序。
- 收到 `onActiveProviderChanged(null)` 时清理当前 UI 状态。
- 收到 `onReceiveText()` 时，应视为进入纯文本模式。
- 收到 `onSeekTo()` 时，应立即校准歌词位置。
