# Provider 播放器控制

Provider 通过 `provider.player` 获取 `RemotePlayer`，并向 Lyricon 中心服务发送播放器状态。

```kotlin
val player = provider.player
```

## 设置歌曲

```kotlin
player.setSong(song)
```

`setSong(song: Song?)` 用于设置当前播放歌曲和结构化歌词。

- `song != null`：更新当前歌曲。
- `song == null`：清空当前播放。

## 发送纯文本

```kotlin
player.sendText("我无法只是普通朋友")
```

`sendText(text: String?)` 用于发送无时间轴的普通文本。调用该方法会清除之前设置的歌曲信息，播放器进入纯文本模式。

## 设置播放状态

```kotlin
player.setPlaybackState(true)
```

`setPlaybackState(playing: Boolean)` 用于同步播放/暂停状态。

- `true`：播放中
- `false`：暂停

也可以使用 Android `PlaybackState`：

```kotlin
player.setPlaybackState(playbackState)
```

中心服务可根据 `PlaybackState.position`、播放速度和更新时间计算实时进度。传入 `null` 表示停止使用该模式。

## 同步播放位置

```kotlin
player.setPosition(1000)
```

`setPosition(position: Long)` 用于更新当前播放位置，单位为毫秒。它通常用于持续同步播放进度。

## 跳转播放位置

```kotlin
player.seekTo(60000)
```

`seekTo(position: Long)` 表示用户或播放器发生了主动跳转。它适合进度条拖动、切歌恢复进度等场景。

## 设置位置读取间隔

```kotlin
player.setPositionUpdateInterval(500)
```

`setPositionUpdateInterval(interval: Int)` 用于设置中心服务读取播放位置的间隔，一般无需修改。

## 翻译和罗马音

```kotlin
player.setDisplayTranslation(true)
player.setDisplayRoma(true)
```

这两个方法只控制显示状态。是否有内容可显示，取决于 `RichLyricLine` 中是否提供了翻译或罗马音数据。

## 推荐调用顺序

```kotlin
player.setSong(song)
player.setPosition(currentPosition)
player.setPlaybackState(isPlaying)
player.setDisplayTranslation(displayTranslation)
player.setDisplayRoma(displayRoma)
```

如果只是展示临时文本：

```kotlin
player.setPlaybackState(true)
player.sendText(text)
```
