# Provider Local Testing

Normally, Provider testing requires the Lyricon central service activated through LSPosed / Xposed.
If LSPosed is not available, use LocalCentralService for basic testing.

## LocalCentralService

LocalCentralService is a local central service implementation for testing. It provides part of the
Lyricon central service behavior for non-LSPosed environments.

Download:

- [LocalCentralService](https://github.com/proify/lyricon/releases/tag/localcentral)

## Steps

1. Install LocalCentralService.
2. Open the app and activate the service.
3. Grant overlay permission.
4. Set the local central service package name when creating the Provider.
5. Call `register()`.
6. Send playback state and lyrics, then check the display.

## Provider Configuration

```kotlin
val provider = LyriconFactory.createProvider(
    context,
    centralPackageName = "io.github.lyricon.localcentralapp"
)
```

> [!WARNING]
> `centralPackageName` for LocalCentralService is only for testing. Remove it before release and use
> the default central service package.

## Testing Tips

- Use `sendText()` first to verify the connection.
- Use `setSong()` to verify structured lyrics.
- Test playback position, translation, and romanization flags last.
- If connection times out, check whether LocalCentralService is running and overlay permission is
  granted.
