# Backup And Restore

Backup and restore help migrate or preserve Lyricon configuration. Use them before reinstalling,
changing devices, flashing ROMs, or trying many style changes.

## What Is Backed Up

Backups mainly save in-app Lyricon settings, such as:

- Basic settings.
- Default style and per-app styles.
- Text, logo, and animation settings.
- Translation configuration.
- App language, theme, and other preferences.

> [!Warning]
> Sensitive information such as keys is not backed up.

## Export A Backup

1. Open Lyricon.
2. Enter **Settings**.
3. Tap **Backup**.
4. Choose a save location.
5. Save the backup file.

The default filename is `lyricon_backup.bin`.

## Restore A Backup

1. Open Lyricon.
2. Enter **Settings**.
3. Tap **Restore**.
4. Select the backup file.
5. Wait for restore to complete.
6. Restart Lyricon, System UI, or the device if needed.

## Check After Restore

After restoring, check:

1. The home screen module status is active.
2. Anchor and width still fit the current ROM.
3. App styles are displayed correctly.
4. Lyric providers are installed.
5. Translation API settings still work.

## Cross-Device Restore

When restoring on another device, status bar structure, screen size, and ROM may differ. If position
is wrong, adjust anchor, width, margins, and view rules again.

## Cross-Version Restore

When restoring an old backup to a new version, newly added settings use default values. If display
issues occur, restart System UI and recheck key settings.
