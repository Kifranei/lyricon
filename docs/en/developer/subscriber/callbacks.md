# Subscriber Callbacks

Subscriber receives active player state through `ActivePlayerListener`. Use
`SimpleActivePlayerListener` when only some events are needed.

## Overview

| Callback                      | Trigger                           | Purpose                             |
|:------------------------------|:----------------------------------|:------------------------------------|
| `onActiveProviderChanged`     | Active player changed             | Update current source               |
| `onSongChanged`               | Song or structured lyrics changed | Render song and lyrics              |
| `onReceiveText`               | Plain text received               | Display text without timeline       |
| `onPlaybackStateChanged`      | Play / pause state changed        | Control animation or playback state |
| `onPositionChanged`           | Playback position updated         | Drive lyric scrolling               |
| `onSeekTo`                    | Explicit seek operation           | Immediately align display state     |
| `onDisplayTranslationChanged` | Translation display flag changed  | Control translated lyric UI         |
| `onDisplayRomaChanged`        | Romanization display flag changed | Control romanization UI             |

## onActiveProviderChanged

```kotlin
override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
}
```

Called when the active player app changes. `providerInfo == null` means there is no active player.

## onSongChanged

```kotlin
override fun onSongChanged(song: Song?) {
}
```

Called when the current song or structured lyrics change. `song == null` means the current song has
been cleared.

## onReceiveText

```kotlin
override fun onReceiveText(text: String?) {
}
```

Called when the Provider sends plain text lyrics. Some Providers may send multi-line text, for
example:

```text
你好 世界\nHello world
```

Apps should handle this callback even if they primarily support structured lyrics.

## onPlaybackStateChanged

```kotlin
override fun onPlaybackStateChanged(isPlaying: Boolean) {
}
```

Called when playback state changes.

- `true`: playing
- `false`: paused

## onPositionChanged

```kotlin
override fun onPositionChanged(position: Long) {
}
```

Called when playback position changes. The unit is milliseconds.

## onSeekTo

```kotlin
override fun onSeekTo(position: Long) {
}
```

Called on explicit seek operations. Compared with normal position updates, this callback is better
for resetting rendering state immediately.

## onDisplayTranslationChanged

```kotlin
override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
}
```

Called when the translation display flag changes. It does not guarantee that the current lyrics
contain translation content.

## onDisplayRomaChanged

```kotlin
override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
}
```

Called when the romanization display flag changes. It does not guarantee that the current lyrics
contain romanization content.

## SimpleActivePlayerListener

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onSongChanged(song: Song?) {
    }
})
```

`SimpleActivePlayerListener` provides empty implementations for all callbacks.

## Recommendations

- Allow nullable values in callbacks.
- Do not rely on a fixed callback order.
- Clear UI state when receiving `onActiveProviderChanged(null)`.
- Treat `onReceiveText()` as plain-text mode.
- Align lyric position immediately after `onSeekTo()`.
