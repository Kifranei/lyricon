# First Run

For the first setup, follow the checklist before changing visual settings. This helps separate
module/provider issues from style issues.

## Checklist

1. Lyricon APK is installed.
2. The Lyricon module is enabled in LSPosed.
3. The **System UI** scope is selected.
4. System UI or the device has been restarted.
5. The home screen shows the module as active.
6. The lyric provider for the current player is installed.
7. The player is playing a track that has lyrics.
8. Lyrics appear in the status bar before style tuning begins.

## Home Screen Status

The home screen shows the current module and System UI state. Use it as the first troubleshooting
signal.

| Status                        | Description                                                                |
|:------------------------------|:---------------------------------------------------------------------------|
| Active                        | The Lyricon module service is connected                                    |
| Not active                    | LSPosed is not enabled, the scope is wrong, or System UI was not restarted |
| Waiting for System UI restart | The current version or configuration needs reinjection                     |
| System UI error, disabled     | Lyricon stopped rendering to avoid affecting System UI                     |

## Test Lyrics For The First Time

1. Open a supported music player.
2. Play a track that definitely has lyrics.
3. Keep the player either foreground or background.
4. Check whether lyrics appear in the status bar.
5. If not, open **Lyric Providers** in Lyricon and confirm that a provider is available.

## First Position Tuning

If lyrics appear but are not positioned correctly, adjust in this order:

1. Choose a proper anchor in **Basic Settings**.
2. Change insertion order to before or after.
3. Adjust width to avoid status icons.
4. Use margins and paddings for small corrections.
5. Configure visibility rules if needed.

## Recommended Initial Strategy

- Keep the default style until lyrics are confirmed working.
- Test one player first before testing multiple players.
- If a player has no lyrics, check provider support before changing style settings.
- If lyrics overlap status icons, reduce width or change the anchor first.
