# Subscriber Active Player

The active player is the current data source selected by Lyricon. After subscribing to the active
player, a Subscriber can receive song, lyrics, playback state, and display options from the active
Provider.

## Subscribe

```kotlin
val listener = object : SimpleActivePlayerListener {
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        // Active Provider changed
    }
}

subscriber.subscribeActivePlayer(listener)
```

The return value indicates whether the subscription request was sent successfully.

## Unsubscribe

```kotlin
subscriber.unsubscribeActivePlayer(listener)
```

Unsubscribe when the UI or business logic no longer needs lyric state.

## ProviderInfo

`ProviderInfo` is a snapshot of the active Provider.

| Field                 | Description                               |
|:----------------------|:------------------------------------------|
| `providerPackageName` | Provider app package name                 |
| `playerPackageName`   | Player app package name                   |
| `logo`                | Provider or player logo, for display only |
| `metadata`            | Provider metadata                         |
| `processName`         | Player process name                       |

`ProviderInfo` equality only compares `providerPackageName`, `playerPackageName`, and `processName`.
`logo` and `metadata` are display-only fields.

## No Active Player

```kotlin
override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
    if (providerInfo == null) {
        // No active player
    }
}
```

`providerInfo == null` means no active player is available. The UI should clear its current state or
enter a waiting state.

## Player Switch

When the active player changes, Subscriber receives `onActiveProviderChanged()`. Subsequent song,
lyric, playback state, and position callbacks come from the new active Provider.

Recommended handling:

- Clear temporary UI state for the previous player.
- Wait for the next `onSongChanged()` or `onReceiveText()`.
- Reset playback position from the new Provider callbacks.
