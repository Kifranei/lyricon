# Lyricon Plugin Development Guide

This guide is intended for developers with Android experience who wish to develop and integrate with
the Lyricon Provider API.

> [!TIP]
> **Testing Without Xposed**
>
> Typically, testing requires installing LSPosed and activating the Lyricon service. If you cannot
> use LSPosed, you can
> install [LocalCentralService](https://github.com/proify/lyricon/releases/tag/localcentral). This
> app
> emulates core Lyricon service functions for non-LSPosed environments. Once installed and opened,
> activate the service and grant the **Floating Window (Overlay)** permission to begin testing based
> on the documentation below.

## 1. Add Dependency

![version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

Add the following dependency to your `build.gradle.kts`:

> [!WARNING]
> Minimum supported version: Android 8.1 (API 27)

```kotlin
implementation("io.github.proify.lyricon:provider:0.1.68")

```

## 2. Configure `AndroidManifest.xml`

Declare the required metadata within the `<application>` tag:

```xml

<application>
    <meta-data android:name="lyricon_module" android:value="true" />

    <meta-data android:name="lyricon_module_author" android:value="Your Name" />

    <meta-data android:name="lyricon_module_description"
        android:value="A brief description of your module" />
</application>
```

### Module Tags (Optional)

Used to declare which lyric features your plugin supports (for display purposes in the UI):

```xml

<meta-data android:name="lyricon_module_tags" android:resource="@array/lyricon_module_tags" />
```

```xml

<string-array name="lyricon_module_tags">
    <item>$syllable</item>
    <item>$translation</item>
</string-array>
```

#### Supported Tag Codes

| Code           | Meaning                                   |
|----------------|-------------------------------------------|
| `$syllable`    | Supports word-by-word / dynamic lyrics    |
| `$translation` | Supports the display of translated lyrics |

## 3. Initialize `LyriconProvider`

```kotlin
val provider = LyriconFactory.createProvider(
    context,

    // Recommendation: Use a solid color/flat icon
    // logo = ProviderLogo.fromDrawable(context, R.drawable.logo),

    // (Optional) Define the local service provider for non-LSPosed testing. 
    // REMOVE THIS for production releases.
    // centralPackageName = "io.github.lyricon.localcentralapp"
)

// Monitor connection status
provider.service.addConnectionListener {
    onConnected { /* Connection established */ }
    onReconnected { /* Connection restored */ }
    onDisconnected { /* Connection lost */ }
    onConnectTimeout { /* Failed to connect */ }
}

// Register the Provider
provider.register()

```

Once registered, Lyricon will begin receiving playback and lyric data pushed by this Provider.

## 4. Basic Player Control

```kotlin
val player = provider.player

// Set playback state (true for playing)
player.setPlaybackState(true)

// Send simple plain text lyrics.
player.sendText("I can't just be an ordinary friend")

```

This method is ideal for simple lyric displays that do not require precise timeline control.

## 5. Advanced Usage

### A. Song Metadata Placeholder

You can send basic song information before the lyrics are fully parsed or ready:

```kotlin
player.setSong(
    Song(
        name = "Ordinary Friend",
        artist = "David Tao"
    )
)

```

### B. Line-based Timed Lyrics (LRC Style)

Ideal for standard `.lrc` formats with timestamps only at the beginning or end of lines:

```kotlin
player.setSong(
    Song(
        id = "unique_song_id",
        name = "Ordinary Friend",
        artist = "David Tao",
        duration = 2000,
        lyrics = listOf(
            RichLyricLine(
                end = 1000,
                text = "I can't just be an ordinary friend"
            ),
            RichLyricLine(
                begin = 1000,
                end = 2000,
                text = "Don't want to be just friends"
            )
        )
    )
)

// After setting the Song, sync the current playback position (ms)
player.setPosition(100)

```

### C. Full Lyric Structure (Word-by-word / Translation / Romanization)

```kotlin
player.setSong(
    Song(
        id = "unique_song_id",
        name = "Ordinary Friend",
        artist = "David Tao",
        duration = 1000,
        lyrics = listOf(
            RichLyricLine(
                end = 1000,
                text = "I can't just be an ordinary friend",
                words = listOf(
                    LyricWord(text = "I", end = 200),
                    LyricWord(text = "can't", begin = 200, end = 400),
                    LyricWord(text = "just", begin = 400, end = 600),
                    LyricWord(text = "be", begin = 600, end = 800),
                    LyricWord(text = "friends", begin = 800, end = 1000)
                ),
                secondary = "(Don't want to be just friends)",
                translation = "I can't just be a normal friend",
            )
        )
    )
)

// Toggle the visibility of the translation
player.setDisplayTranslation(true)

```

## Additional Notes

* **Java Support**: While technically possible, Java integration has not been fully verified. We do
  not guarantee API stability or a "friendly" developer experience when using Java.