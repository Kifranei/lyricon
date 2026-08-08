# Subscriber FAQ

## Why are callbacks not received?

Possible causes:

- Lyricon core service is not installed.
- Subscriber did not call `register()`.
- `subscribeActivePlayer()` was not called.
- There is no active Provider.
- The device is below the supported Android version and an empty implementation is returned.

## Why is `providerInfo` null?

`providerInfo == null` means there is no active player, or the active Provider has disconnected.
Clear current song and lyric state in the UI.

## What is the difference between `onSongChanged()` and `onReceiveText()`?

`onSongChanged()` receives structured song and lyric data, suitable for timed lyrics, word-by-word
lyrics, and translations.

`onReceiveText()` receives plain text without a structured timeline.

## What is the difference between `onPositionChanged()` and `onSeekTo()`?

`onPositionChanged()` is a normal playback position update.

`onSeekTo()` indicates an explicit seek operation and is suitable for resetting lyric rendering
state immediately.

## Does Subscriber require Root or LSPosed?

The Subscriber app itself does not necessarily require Root or LSPosed, but it must be able to
connect to the Lyricon core service. Runtime requirements depend on how the core service is
deployed.

## What should be called when an Activity is destroyed?

If the Subscriber lifecycle is bound to the current `Activity`, call:

```kotlin
subscriber.destroy()
```

If receiving should only be stopped temporarily, call:

```kotlin
subscriber.unregister()
```

## Can only one callback be handled?

Yes. Use `SimpleActivePlayerListener` and override only the required methods.

```kotlin
subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onReceiveText(text: String?) {
    }
})
```

## Can multiple Subscribers exist at the same time?

Yes. The central service uses `SubscriberInfo` to distinguish subscribers from different apps or
processes.
