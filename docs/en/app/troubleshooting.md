# Troubleshooting

This page lists checks by symptom. When something fails, check the home screen module status first,
then lyric providers.

## Lyrics Are Not Displayed At All

Check in this order:

1. Lyricon is enabled in LSPosed.
2. The **System UI** scope is selected.
3. System UI or the device has been restarted.
4. The home screen shows the module as active.
5. The lyric provider for the current player is installed.
6. The current track has available lyrics.
7. Basic Settings do not use an extremely small width or a hide rule.

## Module Is Not Active

Possible causes:

- The LSPosed module is not enabled.
- System UI is not selected as a scope.
- System UI was not restarted.
- LSPosed or Root is not working correctly.

Recheck LSPosed configuration and restart System UI or the device.

## Waiting For System UI Restart

After a Lyricon upgrade or some setting changes, System UI needs reinjection. Use the restart action
on the home screen or reboot the device.

If restart fails, confirm that Root permission is granted.

## System UI Error, Disabled

This is a protection mechanism to avoid repeatedly affecting System UI.

1. Review recently changed anchors, view rules, width, or styles.
2. Restore suspicious settings to safer values.
3. Restart System UI.
4. If the issue remains, reboot and check again.

## Incorrect Lyric Position

Adjust in this order:

1. Anchor.
2. Insertion order.
3. Width.
4. Margins.
5. Paddings.

There is no universal position value across ROMs. Adjust gradually and test repeatedly.

## Lyrics Overlap Status Icons

Try these actions:

- Reduce width in Basic Settings.
- Change the anchor.
- Adjust insertion order.
- Disable the logo or reduce logo size.
- Configure view rules to hide conflicting views.

## No Word-By-Word Lyrics

Word-by-word lyrics depend on the provider. If the provider does not supply word-level timing,
Lyricon can only show plain lyrics.

Check whether the provider page shows the **Word-by-word lyrics** tag.

## No Translation

Check in this order:

1. The lyric provider supports translation.
2. Hide translation is not enabled.
3. Translation-only mode is not enabled without translation data.
4. OpenAI translation has API key, model, and Base URL configured.
5. Ignore Chinese lyrics is not skipping the current lyrics.

## AI Translation Fails

Common causes:

- Invalid API key.
- Incorrect Base URL.
- Model does not exist or is not accessible.
- Network is unavailable.
- Service quota is exhausted.

Try loading the model list in translation settings first. If the list loads, test actual playback
again.

## Lyric Provider Not Found

Try these actions:

- Confirm the provider APK is installed.
- Confirm the provider version is compatible with Lyricon.
- Confirm Lyricon can query installed apps.
- Reopen Lyricon and the player.
- Reboot if necessary.

## Settings Do Not Apply

Some settings require System UI reinjection. Try:

1. Return to the home screen and check for a restart prompt.
2. Restart System UI.
3. Restart the player.
4. Reboot the device.

## Display Issues After Update

After updating Lyricon, the ROM, LSPosed, or the player:

1. Restart System UI or reboot.
2. Check the LSPosed scope.
3. Update lyric providers.
4. Retune anchor and width.
5. Check whether a per-app style overrides the default style.

## Information To Provide When Reporting Issues

- Lyricon version.
- Android version and ROM name.
- LSPosed version.
- Player name and version.
- Lyric provider name and version.
- Screenshot or screen recording.
- Whether other status-bar-related modules are used.
