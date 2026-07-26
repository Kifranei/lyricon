# Text Style

Text Style controls lyric text size, color, font, scrolling, word-level progress, and translation
display. It is located in **App Styles** under the **Text** tab.

## Basic Display

| Option                     | Description                                                            |
|:---------------------------|:-----------------------------------------------------------------------|
| Size                       | Lyric text size in pixels                                              |
| Margins                    | Outer spacing around the text area                                     |
| Paddings                   | Spacing between text and layout bounds                                 |
| Multi-line text size ratio | Scale applied to text size in multi-line mode                          |
| Transition speed           | Animation speed for layout/text transitions                            |
| Fading edge length         | Width of the text edge fade effect                                     |
| Placeholder format         | Shows song title, artist, or nothing when no current lyric line exists |

## Color

| Option                 | Description                                       |
|:-----------------------|:--------------------------------------------------|
| Extract cover color    | Extracts text color from the current album cover  |
| Extract cover gradient | Uses a gradient extracted from album cover colors |
| Custom color           | Uses manually configured text colors              |
| Light mode color       | Color used in light mode                          |
| Dark mode color        | Color used in dark mode                           |

Cover color extraction and custom colors are usually mutually exclusive. When custom color is
enabled, Lyricon prefers manually configured colors.

## Font

| Option      | Description                                      |
|:------------|:-------------------------------------------------|
| Custom font | Font path or name; empty uses the system default |
| Font weight | Controls thickness; higher values are bolder     |
| Bold        | Forces bold style                                |
| Italic      | Forces italic style                              |

If the custom font path is invalid, the system falls back to an available font.

## Marquee

Marquee scrolls long lyrics that exceed the available width.

| Option             | Description                                                |
|:-------------------|:-----------------------------------------------------------|
| Marquee            | Enables long-text scrolling                                |
| Speed              | Controls scrolling speed                                   |
| Repeat spacing     | Sets spacing between repeated text                         |
| Initial delay      | Delay before the first scroll, in milliseconds             |
| Delay              | Delay between scroll loops                                 |
| Infinite scrolling | Removes the repeat count limit                             |
| Repeat count       | Maximum scroll repeats when infinite scrolling is disabled |
| Stop at end        | Leaves text at the end position after scrolling            |

## Word-By-Word Lyrics

Word-by-word lyrics depend on the provider. If the provider does not supply word-level timing,
Lyricon can only show plain line lyrics.

| Option                      | Description                                                            |
|:----------------------------|:-----------------------------------------------------------------------|
| Relative progress           | Treats the whole line as one progress unit when word timing is missing |
| Relative progress highlight | Applies highlight to line-level progress                               |
| Word motion                 | Animates CJK lyrics by character and Latin text by word                |
| CJK lift factor             | Controls CJK character lift strength                                   |
| CJK wave factor             | Controls CJK character wave spacing                                    |
| Latin lift factor           | Controls Latin word lift strength                                      |
| Latin wave factor           | Controls Latin word wave spacing                                       |

## Recommendations

- If lyrics overlap status icons, reduce font size and width first.
- For long lyrics, marquee is usually more stable than simply increasing width.
- For readability, fixed custom colors are more predictable than cover color extraction.
- If word animations cause issues, disable word motion first.
