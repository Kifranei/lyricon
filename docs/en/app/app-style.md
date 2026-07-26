# App Styles

App Styles configure lyric text, logo, and animation. Lyricon supports a default style and separate
styles for individual apps.

## Default Style

The default style applies to all apps that do not have their own style. For initial setup, configure
the default style first.

## Per-App Styles

Create a per-app style when a specific player needs different display behavior. When that app is
playing, Lyricon uses its own style first.

Good use cases:

- A player often has long lyrics and needs a smaller font or wider area.
- Cover color extraction works well for one player and should be enabled only there.
- One player should show the app icon while another should show album cover.
- Different players should use different animations.

## Style Priority

```text
Current playing app style > Default style
```

If no style exists for the current app, Lyricon uses the default style.

## Page Structure

The App Styles page has three tabs:

| Tab       | Description                                                           |
|:----------|:----------------------------------------------------------------------|
| Text      | Font size, color, font, marquee, word-by-word lyrics, and translation |
| Logo      | Logo switch, source, size, color, and insertion order                 |
| Animation | Lyric transition animation switch and presets                         |

## Add An App Style

1. Enter **App Styles**.
2. Tap the title area to switch or add an app.
3. Select the app that needs a separate style.
4. Modify settings in the Text, Logo, and Animation tabs.

## Reset An App Style

If an app style becomes confusing, reset that app style. The app will then fall back to the default
style.

## Recommendations

- Configure the default style first, then add per-app styles only for special players.
- If all apps need the same change, do not create a per-app style.
- Confirm the basic position and width before tuning visual style.
