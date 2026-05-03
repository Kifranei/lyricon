<!--suppress ALL -->

<p align="center">
  <img src="resources/logo.svg" width="100" alt="Lyricon Logo"/>
</p>

<h1 align="center">Lyricon</h1>

<p align="center">
  <b>An Android status bar lyric enhancement tool based on the Xposed framework</b>
</p>

<p align="center">
  <a href="https://github.com/kifranei/lyricon/releases"><img src="https://img.shields.io/github/v/release/kifranei/lyricon?style=flat&color=blue" alt="Version"></a>
  <a href="https://github.com/kifranei/lyricon/releases"><img src="https://img.shields.io/github/downloads/kifranei/lyricon/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/kifranei/lyricon/commits"><img src="https://img.shields.io/github/last-commit/kifranei/lyricon?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/kifranei/lyricon/blob/main/LICENSE"><img src="https://img.shields.io/github/license/kifranei/lyricon?style=flat" alt="License"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/Document-Chinese-red.svg" alt="CN"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/IXif8Zi0Iq"><img src="https://img.shields.io/badge/QQ_Group-0084FF?style=flat&logo=qq&logoColor=white" alt="QQ Group"></a>
  <a href="https://t.me/cslyric"><img src="https://img.shields.io/badge/Telegram-0084FF?style=flat&logo=telegram&logoColor=white" alt="Telegram"></a>
</p>

<p align="center">
  <img src="resources/z.gif" alt="Demo Animation" width="539"/>
</p>

---

## ✨ Features

- 🎤 **Lyric Display** — Supports word-by-word lyrics, translated lyrics, and duet mode.
- 🧩 **Modular Design** — Supports extending lyric sources for different music players through an
  independent plugin system.
- 🎨 **Visual Customization** — Supports adjusting font styles, logo display, coordinate offsets, and
  animation effects.

---

## ⚠️ This is a Fork Version, [Original Project](https://github.com/tomakino/Lyricon)
- Since the original version has added many features that I've contributed since forking, it has been decided to officially archive this fork starting from 2026.5.3.
- If you want to use or participate in development, please go to the original project address.
- Existing bugs and feature requests will no longer be handled. It is recommended to resubmit them to the original project.

## 🚀 Quick Start

### 📋 Requirements

- **System Version**: Android 9.0 (API 28) or later.
- **Prerequisites**: The device must have **Root** access and the **LSPosed** framework, or a
  compatible Xposed framework, installed.

> [!TIP]
> To ensure functional stability, it is recommended to use LSPosed 2.0.1 or above. At the same time, it is not recommended for temporary Root users, as the Zygote process is fragile and may trigger unknown bugs.

### ⚙️ Installation & Configuration

1. **Download the Main App**: Download and install Lyricon
   from [Releases](https://github.com/tomakino/lyricon/releases).
2. **Activate the Module**: Enable the "Lyricon" module in LSPosed Manager and select the **System
   UI** scope.
3. **Restart to Apply**: Restart System UI or reboot the device to complete Hook injection.
4. **Install Plugins**: Download the corresponding plugin
   from [LyricProvider](https://github.com/tomakino/LyricProvider) according to the music player you
   use.
5. **Adjust Parameters**: Open the Lyricon app and adjust the position anchor, width, and visual
   style according to your screen.
6. **Test It**: Launch your music player and play music, then check whether lyrics are displayed
   correctly in the status bar.

---

## 🧩 Ecosystem & Support

| Category           | Resource Link                                                         | Description                                       |
|:-------------------|:----------------------------------------------------------------------|:--------------------------------------------------|
| **Plugin Library** | [LyricProvider Repository](https://github.com/tomakino/LyricProvider) | Adaptation plugins for mainstream music platforms |
| **Documentation**  | [Documentation Center](https://tomakino.github.io/lyricon/)           | App and Lyric documentation                       |

### 💡 Natively Supported Apps

- [**Light Cone Player**](https://coneplayer.trantor.ink/)
- **Flamingo**
- [**BBPlayer**](https://bbplayer.roitium.com/)
- **MobiMusic**
- [**Kanade**](https://github.com/rcmiku/Kanade)
- **Sollin Player**
- [**QZ Music**](https://github.com/lqtmcstudio/QZMusic)

#### Is your player already supported but not listed here? Please [submit an issue](https://github.com/tomakino/lyricon/issues).

---

## 👥 Contributors

[![Contributors](https://contrib.rocks/image?repo=kifranei/lyricon)](https://github.com/kifranei/lyricon/graphs/contributors)

---

## ⭐ Star History

<p align="center">
  <a href="https://www.star-history.com/#kifranei/lyricon&Date">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=kifranei/lyricon&type=Date&theme=dark" />
      <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=kifranei/lyricon&type=Date" />
      <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=kifranei/lyricon&type=Date" width="600" />
    </picture>
  </a>
</p>

---

### 👀 Visit Statistics

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_lyricon?theme=moembooru-h" alt="Visitor Count" />
</p>
