# Installation

Lyricon needs LSPosed or a compatible Xposed framework to inject into System UI. Installing the APK
is not enough; the module must be enabled, scoped, and loaded by restarting System UI.

## Requirements

| Item              | Requirement                    |
|:------------------|:-------------------------------|
| Android version   | Android 10 (API 29) or later   |
| Root              | Root access is required        |
| LSPosed framework | LSPosed 2.0 or later           |
| Scope             | **System UI** must be selected |

## Install The App

1. Open [Lyricon Releases](https://github.com/proify/lyricon/releases).
2. Download the latest APK.
3. Install the APK on your device.
4. Open Lyricon and make sure the app can enter the home screen.

## Enable The LSPosed Module

1. Open LSPosed Manager.
2. Enable the **Lyricon** module.
3. Open the module scope settings.
4. Select **System UI**.
5. Restart System UI or reboot the device.

## Verify Activation

Open the Lyricon home screen and check the status card.

| Status                        | Meaning                                                  | Action                                          |
|:------------------------------|:---------------------------------------------------------|:------------------------------------------------|
| Active                        | The module service is available                          | Continue with providers and style configuration |
| Not active                    | The module is not injected or the service is unavailable | Check LSPosed and the System UI scope           |
| Waiting for System UI restart | A new version or setting needs reinjection               | Restart System UI or reboot                     |
| System UI error, disabled     | System UI entered protection state                       | Check LSPosed logs and recently changed settings |

## Restart System UI

Lyricon provides a **Restart System UI** action. This usually needs Root permission. If it fails,
confirm that Root has been granted, or reboot the device manually.

## Install Lyric Providers

Lyricon needs lyric providers to receive lyric data. After installing the main app, install the
provider that supports your player.

- [LyricProvider Releases](https://github.com/proify/LyricProvider/releases)

After installing a provider, reopen the player and start playback. If lyrics still do not appear,
see [Lyric Providers](./providers.md) and [Troubleshooting](./troubleshooting.md).

## Upgrading

After upgrading Lyricon, the home screen may show that System UI needs to be restarted. Restart
System UI or reboot the device to reload the module.
