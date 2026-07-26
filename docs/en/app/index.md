# App Guide

Lyricon is an Android status bar lyrics tool based on Xposed / LSPosed. The app manages position,
visual style, lyric providers, and app preferences, while lyrics are injected into the System UI
status bar area.

## Who It Is For

- Users who want current lyrics to stay visible in the Android status bar.
- Users who want different lyric styles for different music players.
- Users who need word-by-word lyrics, translated lyrics, cover color extraction, or status bar
  visual customization.
- Android users familiar with Root, LSPosed, and System UI scope configuration.

## How It Works

Lyricon works through three parts:

| Part           | Purpose                                                            |
|:---------------|:-------------------------------------------------------------------|
| Lyricon app    | Manages configuration, styles, lyric providers, and backup/restore |
| LSPosed module | Injects the lyric view into System UI                              |
| Lyric provider | Provides lyric data from players or external sources               |

The app itself does not directly draw status bar lyrics. Lyrics appear only after the module is
enabled in LSPosed, the System UI scope is selected, and System UI has been restarted.

## Main Features

| Feature             | Description                                                            |
|:--------------------|:-----------------------------------------------------------------------|
| Status bar lyrics   | Shows current lyrics in the status bar area                            |
| Word-by-word lyrics | Supports word-level progress and highlight when provided by the source |
| Translated lyrics   | Supports provider translations and OpenAI-compatible translation       |
| Per-app styles      | Uses separate text, logo, and animation styles for different players   |
| Position tuning     | Supports anchor, insertion order, width, margins, and paddings         |
| Logo display        | Supports provider logo, app icon, album cover, and custom logo         |
| Animation effects   | Provides lyric transition animation presets                            |
| Backup and restore  | Exports and restores Lyricon configuration                             |

## Quick Start

1. Download and install Lyricon from [Releases](https://github.com/proify/lyricon/releases).
2. Enable the Lyricon module in LSPosed.
3. Select the **System UI** scope.
4. Restart System UI or reboot the device.
5. Install the lyric provider for your music player.
6. Play a track and confirm that lyrics appear in the status bar.
7. Return to Lyricon and adjust basic settings and app styles.

## Recommended Reading

| Goal                                 | Page                                    |
|:-------------------------------------|:----------------------------------------|
| Install and activate                 | [Installation](./installation.md)       |
| Check the first-run setup            | [First Run](./first-run.md)             |
| Manage lyric sources                 | [Lyric Providers](./providers.md)       |
| Adjust display position              | [Basic Settings](./basic-settings.md)   |
| Configure text, logo, and animations | [App Styles](./app-style.md)            |
| Configure AI translation             | [Translation](./translation.md)         |
| Troubleshoot issues                  | [Troubleshooting](./troubleshooting.md) |

## Tips

- Confirm that lyrics can be displayed before changing position and style.
- Status bar structures vary across ROMs, so there is no universal anchor or width value.
- Word-by-word lyrics and translations depend on what the lyric source provides.
- After a system update, ROM update, LSPosed update, or Lyricon upgrade, restart System UI first if
  display issues appear.
