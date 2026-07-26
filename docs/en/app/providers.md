# Lyric Providers

Lyric providers send lyrics, playback state, and translation data to Lyricon. The main
Lyricon app manages display configuration, but it does not guarantee lyrics for every player by
itself.

## Sources

Install lyric providers from the official source:

- [LyricProvider Releases](https://github.com/proify/LyricProvider/releases)

## View Available Providers

Open Lyricon and enter **Lyric Providers**. Available and recognized providers are grouped by
category.

| Field   | Description                                                        |
|:--------|:-------------------------------------------------------------------|
| Name    | Provider or adapter name                                           |
| Author  | Provider author information                                        |
| Version | Installed provider version                                         |
| Tags    | Supported capabilities, such as word-by-word lyrics or translation |

## List Display Mode

The top-right menu can switch the provider list mode.

| Mode    | Description                                              |
|:--------|:---------------------------------------------------------|
| Compact | Shows essential information for daily use                |
| Full    | Shows more tags and provider details                    |

## Capability Tags

| Tag                 | Meaning                                        |
|:--------------------|:-----------------------------------------------|
| Word-by-word lyrics | The provider can supply word-level timing data |
| Translation         | The provider can supply translated lyrics      |

If a tag is missing, Lyricon may still show plain lyrics, but that capability will not be available.

## Provider Installed But Not Working

Check in this order:

1. The provider APK was installed successfully.
2. The provider supports the current player version.
3. The player is playing a track with available lyrics.
4. The provider appears in the Lyricon provider page.
5. The player was reopened after installing or updating the provider.

## No Available Providers

If the page shows that no lyric providers are available:

- Confirm that a provider is installed.
- Confirm that Lyricon has permission to query installed apps.
- Confirm that the provider package and version are compatible with Lyricon.
- Reopen Lyricon and the music player.
