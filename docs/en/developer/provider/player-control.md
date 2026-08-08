# Provider Player Control

Provider uses `provider.player` to get a `RemotePlayer` and send player state to the Lyricon central
service.

```kotlin
val player = provider.player
```

## Set Song

```kotlin
player.setSong(song)
```

`setSong(song: Song?)` sets the current song and structured lyrics.

- `song != null`: updates the current song.
- `song == null`: clears the current song.

## Send Plain Text

```kotlin
player.sendText("I can't just be an ordinary friend")
```

`sendText(text: String?)` sends plain text without a timeline. It clears the previously set song and
switches the player to plain-text mode.

## Set Playback State

```kotlin
player.setPlaybackState(true)
```

`setPlaybackState(playing: Boolean)` syncs playback state.

- `true`: playing
- `false`: paused

Android `PlaybackState` is also supported:

```kotlin
player.setPlaybackState(playbackState)
```

The central service can calculate real-time progress from `PlaybackState.position`, playback speed,
and update time. Pass `null` to stop using this mode.

## Sync Position

```kotlin
player.setPosition(1000)
```

`setPosition(position: Long)` updates the current playback position in milliseconds. It is usually
used for continuous progress sync.

## Seek

```kotlin
player.seekTo(60000)
```

`seekTo(position: Long)` indicates an explicit seek operation, such as dragging the progress bar or
restoring a position after switching tracks.

## Position Update Interval

```kotlin
player.setPositionUpdateInterval(500)
```

`setPositionUpdateInterval(interval: Int)` sets how often the central service reads the playback
position. It usually does not need to be changed.

## Translation and Romanization

```kotlin
player.setDisplayTranslation(true)
player.setDisplayRoma(true)
```

These methods only control display flags. The corresponding content must exist in `RichLyricLine`.

## Recommended Order

```kotlin
player.setSong(song)
player.setPosition(currentPosition)
player.setPlaybackState(isPlaying)
player.setDisplayTranslation(displayTranslation)
player.setDisplayRoma(displayRoma)
```

For temporary text:

```kotlin
player.setPlaybackState(true)
player.sendText(text)
```
