# Basic Settings

Basic Settings control the lyric container position, size, visibility, and filtering behavior in the
status bar. They affect all app styles and should be adjusted first when position issues occur.

## Entry

Open Lyricon and enter **Basic Settings**.

## Position And Size

| Option          | Description                                      | Recommendation                                        |
|:----------------|:-------------------------------------------------|:------------------------------------------------------|
| Anchor          | Selects the reference view in the status bar     | Adjust first when lyrics are misplaced                |
| Insertion order | Inserts lyrics before or after the anchor        | Use together with the anchor                          |
| Width           | Limits the maximum lyric view width              | Avoid covering clock, signal, battery, or other icons |
| Margins         | Sets outer spacing around the lyric view         | Use for overall position tuning                       |
| Paddings        | Sets spacing between content and container edges | Use for visual spacing                                |

Margin and padding values are ordered as left, top, right, bottom, in pixels.

## ColorOS Fluid Cloud

On ColorOS / OPlus devices, Lyricon provides **Width in Fluid Cloud mode**. It limits the lyric view
width separately when Fluid Cloud is active.

If lyrics are squeezed or overlap icons in Fluid Cloud, adjust this width first.

## View Rules

**Configure view visibility rules** controls how certain status bar views behave while lyrics are
playing. A common use case is hiding system views that overlap with lyrics.

System UI view hierarchies vary by ROM, so rules may need to be reconfigured after a system update.

## Blocked Lyric Regex

**Blocked lyric regex** hides lyric lines that match the regular expression. Leave it empty to
disable filtering.

It is useful for blocking:

- Ads or source markers.
- Placeholder text.
- Fixed lyric fragments you do not want to display.

## Chinese Conversion

| Option                 | Description                                   |
|:-----------------------|:----------------------------------------------|
| Keep original          | Do not convert lyric text                     |
| Convert to Simplified  | Convert Chinese lyrics to Simplified Chinese  |
| Convert to Traditional | Convert Chinese lyrics to Traditional Chinese |

## Visibility And Hide Policies

| Option                     | Description                                                          |
|:---------------------------|:---------------------------------------------------------------------|
| Hide on lock screen        | Hides lyrics on the lock screen                                      |
| No lyric hide timeout      | Hides the lyric view after a delay when no lyrics are available      |
| No update hide timeout     | Hides the lyric view after lyric updates stop                        |
| Keyword match hide timeout | Hides the lyric view after a keyword rule is matched                 |
| Keyword regex list         | One regex per line; matching lyrics trigger the keyword hide timeout |

A timeout value of `0` means never hide.

## Gestures

With **Enable lyrics gestures** turned on, you can swipe or tap the status bar lyrics to control
playback.

| Gesture    | Default action    | Available actions                                                    |
|:-----------|:------------------|:---------------------------------------------------------------------|
| Swipe left | Next track        | No action / Play-Pause / Previous track / Next track / Open control panel |
| Swipe right| Previous track    | Same as above                                                        |
| Tap        | Open control panel| Same as above                                                        |
| Long press | Play / Pause      | Same as above                                                        |

Slow horizontal dragging is recognized as a swipe, no flick velocity is required. With gestures
disabled, tapping the lyrics still opens the control panel.

Gestures come with touch feedback: a subtle shrink while pressed, the lyric content follows your
finger when swiping and springs back, long press slightly enlarges it, and tap / long press / swipe
can trigger haptic vibration when recognized (switch it off under "Gestures" if undesired).

## Tuning Recommendations

1. Choose the anchor and insertion order first.
2. Adjust width to avoid status icons.
3. Use margins and paddings for fine tuning.
4. Configure view rules and hide policies only when specific scenes still overlap.
