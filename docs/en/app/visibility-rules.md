# View Rules And Hide Policies

View rules manage the relationship between the lyric view and other System UI status bar elements.
Hide policies automatically hide lyrics in specific states.

## Anchor

The anchor is the reference view used when Lyricon inserts lyrics into System UI. Since status bar
structures differ between ROMs, anchor selection directly affects the final position.

If lyrics are misplaced, adjust the anchor and insertion order first.

## View Visibility Rules

Open **Basic Settings** and tap **Configure view visibility rules**. This page controls whether
selected status bar views are hidden while lyrics are playing.

| Option            | Description                             |
|:------------------|:----------------------------------------|
| Default           | Keeps the system's original visibility  |
| Hide when playing | Hides the view while lyrics are playing |

This is useful for hiding status bar elements that overlap with lyrics on some ROMs.

## Hide On Lock Screen

When **Hide on lock screen** is enabled, lyrics are not shown on the lock screen. Use this if lyrics
interfere with the lock screen layout.

## Hide When No Lyrics

**No lyric hide timeout** controls how long Lyricon waits before hiding the lyric view when no
lyrics are available. `0` means never hide.

## Hide When No Updates

**No update hide timeout** controls how long Lyricon waits before hiding the lyric view after lyric
updates stop. This is useful when playback pauses, a provider stops pushing updates, or playback
ends.

## Keyword Hide

Keyword hide uses two settings together:

| Option                     | Description                                       |
|:---------------------------|:--------------------------------------------------|
| Keyword regex list         | One regex per line, matched against lyric content |
| Keyword match hide timeout | Delay before hiding the lyric view after a match  |

It is useful for hiding ads, intro markers, placeholder text, or other unwanted content.

## Blocked Lyric Regex

Blocked lyric regex directly hides matching lyric lines. Unlike keyword hide, it filters specific
lines instead of triggering a full lyric view hide timeout.

## Regex Notes

- One rule per line is easier to maintain.
- Broad rules may accidentally hide normal lyrics.
- Test several tracks after changing rules.
- If you are not familiar with regex, ask AI.

## After ROM Updates

System updates, theme changes, or status bar module changes may alter the System UI view hierarchy.
If lyrics or rules stop working, reselect the anchor and review view rules.
