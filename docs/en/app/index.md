# App Guide

Lyricon is an Android status bar lyrics tool based on Xposed / LSPosed. The app configures display behavior, while lyrics are rendered in the System UI scope.

## Requirements

- Android 9.0 (API 28) or later.
- Root access.
- LSPosed or a compatible Xposed framework.

## Installation

1. Download and install Lyricon from [Releases](https://github.com/tomakino/lyricon/releases).
2. Enable the Lyricon module in LSPosed.
3. Select the **System UI** scope.
4. Restart System UI or reboot the device.
5. Open Lyricon and confirm that the module is active.

## Plugins

Lyricon uses plugins to adapt lyric sources for different music players.

- [LyricProvider repository](https://github.com/tomakino/LyricProvider)

After installing a plugin, reopen the player and start playback. If lyrics are not shown, check whether the plugin supports the current player version.

## Configuration

| Option | Description |
|:---|:---|
| Position anchor | Sets the lyric position in the status bar |
| Width | Limits the lyric area to avoid status icons |
| Offset | Fine-tunes horizontal and vertical position |
| Font style | Adjusts text appearance |
| Logo | Controls player or plugin logo display |
| Animation | Controls lyric transition and scrolling effects |

Status bar layouts vary across ROMs. Confirm that lyrics can be displayed first, then adjust position and style.

## Troubleshooting

### Lyrics are not displayed

- Ensure Lyricon is enabled in LSPosed.
- Ensure the scope includes **System UI**.
- Restart System UI or reboot the device.
- Install the plugin for the current player.
- Confirm that lyrics are available for the current track.

### Incorrect position

Adjust the position anchor, width, and offset in Lyricon.

### No word-by-word lyrics or translation

Word-by-word lyrics and translations depend on the lyric source. If the plugin does not provide these fields, Lyricon can only display plain lyrics.
