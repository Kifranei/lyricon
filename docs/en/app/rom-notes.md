# ROM Notes

Lyricon inserts the lyric view into System UI. Since ROMs implement the status bar differently, one
configuration may not work well on every device.

## Why ROMs Differ

Clock, signal, battery, notification icons, privacy indicators, and vendor features are usually
separate System UI views. Different ROMs may use different view hierarchies, sizes, and animations.

Lyricon uses anchors and insertion order to determine lyric position, so ROM differences affect the
final result.

## General Tuning Order

1. Select an anchor.
2. Adjust insertion order.
3. Limit lyric width.
4. Adjust margins and paddings.
5. Configure view visibility rules.
6. Disable logo or animations if needed.

## ColorOS / OPlus

Lyricon provides extra settings for OPlus devices:

| Setting                       | Description                                         |
|:------------------------------|:----------------------------------------------------|
| Width in Fluid Cloud mode     | Controls lyric width separately in Fluid Cloud mode |
| Hide logo in Fluid Cloud mode | Hides the lyric logo while Fluid Cloud is active    |

If Fluid Cloud leaves too little status bar space, reduce the Fluid Cloud width or hide the logo.

## MIUI / HyperOS

MIUI / HyperOS status bar layout may be affected by themes, notification style, and control center
style. If lyrics are misplaced, change the anchor first, then adjust width.

## AOSP-Like ROMs

AOSP-like ROMs usually have a simpler status bar structure, but custom ROMs may still differ. Width
and margins are often enough for tuning.

## Samsung One UI

One UI may distribute status bar icons and lock screen elements differently from AOSP. If lock
screen layout is affected, enable hide on lock screen.

## Other Status Bar Modules

If other Xposed modules, Magisk modules, or theme plugins also modify the status bar, you may see:

- Lyric position changes.
- Invalid anchors.
- Overlapping status bar elements.
- Different behavior after System UI restarts.

For troubleshooting, temporarily disable other status-bar-related modules.

## After System Updates

System updates may change System UI structure. If Lyricon behaves incorrectly after an update:

1. Reboot the device.
2. Confirm the LSPosed module is still enabled.
3. Confirm the System UI scope is still selected.
4. Reselect the anchor.
5. Review view visibility rules.
