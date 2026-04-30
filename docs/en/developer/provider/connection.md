# Provider Connection Lifecycle

`LyriconProvider` connects to the Lyricon central service. A Provider must be registered before it
can send songs, lyrics, and playback state.

## Register

```kotlin
provider.register()
```

`register()` sends a registration request to the central service. Its return value indicates whether
the request was sent, not whether the lyrics are already displayed.

## Unregister

```kotlin
provider.unregister()
```

`unregister()` disconnects from the current central service connection. Use it when the player
exits, a service stops, or lyrics should no longer be pushed.

## Destroy

```kotlin
provider.destroy()
```

`destroy()` releases listeners, callbacks, and remote connection resources. It is usually called
when the app component is finally destroyed.

## Connection Listener

```kotlin
provider.service.addConnectionListener {
    onConnected { }
    onReconnected { }
    onDisconnected { }
    onConnectTimeout { }
}
```

| Callback           | Description                                       |
|:-------------------|:--------------------------------------------------|
| `onConnected`      | First connection succeeded                        |
| `onReconnected`    | Reconnected after disconnect                      |
| `onDisconnected`   | Connection disconnected                           |
| `onConnectTimeout` | Timed out while connecting to the central service |

## Auto Sync

```kotlin
provider.autoSync = true
```

`autoSync` controls whether the last cached player state is synchronized after connection or
reconnection. Keeping it enabled is generally recommended.

## Recommendations

- Keep the Provider in a player service or app-level component.
- Sync the current song first, then playback state and position.
- On connection timeout, ask the user to check Lyricon, LSPosed, or LocalCentralService.
- Call `destroy()` when the app or service stops.
