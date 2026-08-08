# Logo Style

Logo Style controls the logo or cover displayed next to lyrics. It is located in **App Styles**
under the **Logo** tab.

## Basic Settings

| Option          | Description                                       |
|:----------------|:--------------------------------------------------|
| Enable          | Controls whether the lyric logo is shown          |
| Size            | Sets both logo width and height to keep it square |
| Margins         | Outer spacing around the logo                     |
| Insertion order | Displays the logo before or after lyric text      |

## ColorOS Settings

On ColorOS / OPlus devices, **Hide in Fluid Cloud mode** can hide the lyric logo while Fluid Cloud
is active. This helps reduce status bar crowding.

## Logo Sources

| Style                | Description                                                      |
|:---------------------|:-----------------------------------------------------------------|
| Default              | Uses the provider logo or Lyricon default logo                   |
| App icon             | Uses the current player app icon                                 |
| Album cover (square) | Uses the current album cover in a square or rounded-square shape |
| Album cover (circle) | Uses the current album cover cropped as a circle                 |
| Custom logo          | Uses Base64 image data or raw SVG content entered by the user    |

## Custom Logo

After selecting **Custom logo**, tap the input button and paste:

- Base64-encoded image data.
- Raw SVG content.

Leaving the input empty clears the custom logo content.

## Logo Color

| Option           | Description                   |
|:-----------------|:------------------------------|
| Custom color     | Enables manual logo color     |
| Light mode color | Logo color used in light mode |
| Dark mode color  | Logo color used in dark mode  |

Some logo sources, such as album covers, may not be suitable for forced tinting. The actual effect
depends on the source and rendering path.

## Recommendations

- If status bar space is tight, disable the logo or reduce its size.
- Use the app icon when you want to identify the player quickly.
- Use album cover when you want visuals to match the current song.
- When using album cover logo, cover color extraction for text can make the overall style more
  consistent.
