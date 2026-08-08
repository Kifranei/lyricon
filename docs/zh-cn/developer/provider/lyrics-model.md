# Provider 歌词数据结构

Provider 使用 `Song`、`RichLyricLine` 和 `LyricWord` 描述歌曲和歌词。时间单位统一为毫秒。

## Song

`Song` 表示当前歌曲。常用字段包括：

| 字段         | 说明              |
|:-----------|:----------------|
| `id`       | 歌曲唯一标识，建议稳定且可复用 |
| `name`     | 歌曲名称            |
| `artist`   | 歌手名称            |
| `duration` | 歌曲总时长，单位毫秒      |
| `lyrics`   | 歌词行列表           |

可以先发送只有基础信息的歌曲占位：

```kotlin
player.setSong(
    Song(
        name = "普通朋友",
        artist = "陶喆"
    )
)
```

## 行级歌词

行级歌词适合普通 LRC 场景，只包含每行的开始和结束时间。

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
```

设置歌曲后，应同步当前播放进度：

```kotlin
player.setPosition(100)
```

## 逐字歌词

逐字歌词通过 `RichLyricLine.words` 描述每个词或字的时间范围。

```kotlin
player.setSong(
    Song(
        id = "song-id",
        name = "普通朋友",
        artist = "陶喆",
        duration = 1000,
        lyrics = listOf(
            RichLyricLine(
                begin = 0,
                end = 1000,
                text = "我无法只是普通朋友",
                words = listOf(
                    LyricWord(text = "我", begin = 0, end = 200),
                    LyricWord(text = "无法", begin = 200, end = 400),
                    LyricWord(text = "只是", begin = 400, end = 600),
                    LyricWord(text = "普通", begin = 600, end = 800),
                    LyricWord(text = "朋友", begin = 800, end = 1000)
                )
            )
        )
    )
)
```

## 翻译和次要歌词

`RichLyricLine` 可以同时携带主歌词、次要歌词和翻译歌词。

```kotlin
RichLyricLine(
    begin = 0,
    end = 1000,
    text = "我无法只是普通朋友",
    secondary = "（不想做普通朋友）",
    translation = "I can't just be a normal friend"
)
```

显示翻译需要同时满足：

- 歌词行中存在翻译内容。
- Provider 调用了 `player.setDisplayTranslation(true)`。
- Lyricon 展示端允许显示翻译。

## 罗马音

如果歌词模型中提供了罗马音字段，可通过以下方式控制显示状态：

```kotlin
player.setDisplayRoma(true)
```

显示逻辑与翻译类似，开关只控制展示状态，不会自动生成罗马音。

## 建议

- `id` 尽量使用稳定的歌曲 ID，避免同一首歌重复创建不同状态。
- `begin` 和 `end` 使用毫秒，且建议单调递增。
- 逐字歌词的 `words` 时间范围应落在所在行的时间范围内。
- 没有逐字数据时，提供行级时间轴即可。
- 没有时间轴时，使用 `sendText()` 更简单。
