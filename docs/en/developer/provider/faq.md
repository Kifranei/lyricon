# Provider FAQ

## Why are lyrics not displayed after registration?

Possible causes:

- The Lyricon central service is not running.
- LSPosed / Xposed scope is not configured correctly.
- The Provider did not call `register()`.
- The Provider is connected but did not call `sendText()` or `setSong()`.
- LocalCentralService is used without overlay permission.

## What is the difference between `sendText()` and `setSong()`?

`sendText()` sends plain text without a timeline and clears the previously set song.

`setSong()` sends structured song and lyric data, including line-level lyrics, word-by-word lyrics,
and translations.

## When should `setPosition()` be used?

Use `setPosition()` for continuous playback position sync, for example during normal playback.

## When should `seekTo()` be used?

Use `seekTo()` for explicit seek operations, such as dragging the progress bar or restoring a
position after switching tracks.

## Why are translated lyrics not displayed?

All conditions must be met:

- `RichLyricLine` contains translation content.
- `player.setDisplayTranslation(true)` was called.
- The renderer allows translation display.

## Why is romanization not displayed?

All conditions must be met:

- Lyric data contains romanization content.
- `player.setDisplayRoma(true)` was called.
- The renderer allows romanization display.

## Is Java supported?

Java usage has not been fully verified. Kotlin is recommended.

## What should be checked before release?

- Remove the LocalCentralService `centralPackageName` test configuration.
- Ensure `AndroidManifest.xml` declares Lyricon module metadata.
- Ensure module tags match actual capabilities.
- Release resources when the connection is disconnected or the app exits.
