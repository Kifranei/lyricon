# Subscriber Connection Lifecycle

`LyriconSubscriber` connects to the Lyricon central service. A Subscriber must be registered before
it can receive active player and lyric state.

## Register

```kotlin
subscriber.register()
```

`register()` sends a registration request to the central service. After registration succeeds, the
Subscriber can receive callbacks.

## Unregister

```kotlin
subscriber.unregister()
```

`unregister()` disconnects from the current central service connection. Use it when the app no
longer needs to listen, or when a service stops.

## Destroy

```kotlin
subscriber.destroy()
```

`destroy()` releases listeners, callbacks, and remote connection resources. It is usually called
when the component is finally destroyed.

## Connection Listener

```kotlin
val listener = object : ConnectionListener {
    override fun onConnected(subscriber: LyriconSubscriber) {}
    override fun onReconnected(subscriber: LyriconSubscriber) {}
    override fun onDisconnected(subscriber: LyriconSubscriber) {}
    override fun onConnectTimeout(subscriber: LyriconSubscriber) {}
}

subscriber.addConnectionListener(listener)
```

Remove the listener when it is no longer needed:

```kotlin
subscriber.removeConnectionListener(listener)
```

| Callback           | Description                                                    |
|:-------------------|:---------------------------------------------------------------|
| `onConnected`      | Connected after user registration                              |
| `onReconnected`    | Reconnected after service restart or retry                     |
| `onDisconnected`   | Disconnected, including manual unregister or remote disconnect |
| `onConnectTimeout` | Connection failed after maximum retries                        |

## Lifecycle Recommendations

- In an `Activity`, register in `onStart()` or `onCreate()`, and release in `onStop()` or
  `onDestroy()` depending on background needs.
- In a `Service`, manage the Subscriber with the service lifecycle.
- Do not assume data is available immediately after `register()`.
- Handle empty song, lyrics, or Provider state after disconnection.
