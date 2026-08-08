# Provider Lyric Model

Provider uses `Song`, `RichLyricLine`, and `LyricWord` to describe songs and lyrics. Time values are
in milliseconds.

## Song

`Song` represents the current song. Common fields:

| Field      | Description              |
|:-----------|:-------------------------|
| `id`       | Stable song identifier   |
| `name`     | Song title               |
| `artist`   | Artist name              |
| `duration` | Duration in milliseconds |
| `lyrics`   | Lyric line list          |

You can send metadata before lyrics are ready:

```kotlin
player.setSong(
    Song(
        name = "Ordinary Friend",
        artist = "David Tao"
    )
)
```

## Line-level Lyrics

Line-level lyrics are suitable for standard LRC data with line begin and end times.

```kotlin
player.setSong(
    Song(
        id = "song-id",
        name = "Ordinary Friend",
        artist = "David Tao",
        duration = 2000,
        lyrics = listOf(
            RichLyricLine(
                begin = 0,
                end = 1000,
                text = "I can't just be an ordinary friend"
            ),
            RichLyricLine(
                begin = 1000,
                end = 2000,
                text = "Don't want to be just friends"
            )
        )
    )
)
```

After setting the song, sync the current playback position:

```kotlin
player.setPosition(100)
```

## Word-by-word Lyrics

Word-by-word lyrics are described through `RichLyricLine.words`.

```kotlin
player.setSong(
    Song(
        id = "song-id",
        name = "Ordinary Friend",
        artist = "David Tao",
        duration = 1000,
        lyrics = listOf(
            RichLyricLine(
                begin = 0,
                end = 1000,
                text = "I can't just be an ordinary friend",
                words = listOf(
                    LyricWord(text = "I", begin = 0, end = 200),
                    LyricWord(text = "can't", begin = 200, end = 400),
                    LyricWord(text = "just", begin = 400, end = 600),
                    LyricWord(text = "be", begin = 600, end = 800),
                    LyricWord(text = "friends", begin = 800, end = 1000)
                )
            )
        )
    )
)
```

## Translation and Secondary Lyrics

`RichLyricLine` can contain main lyrics, secondary lyrics, and translated lyrics.

```kotlin
RichLyricLine(
    begin = 0,
    end = 1000,
    text = "I can't just be an ordinary friend",
    secondary = "(Don't want to be just friends)",
    translation = "I can't just be a normal friend"
)
```

Translation display requires:

- Translation content in the lyric line.
- `player.setDisplayTranslation(true)` called by the Provider.
- Translation display enabled by the Lyricon renderer.

## Romanization

If the lyric model contains romanization content, display can be controlled with:

```kotlin
player.setDisplayRoma(true)
```

This only controls display. It does not generate romanization automatically.

## Recommendations

- Use a stable `id` for the same song.
- Use milliseconds for `begin` and `end`.
- Keep word time ranges inside the line time range.
- Use line-level lyrics when word-level data is unavailable.
- Use `sendText()` when no timeline is available.
