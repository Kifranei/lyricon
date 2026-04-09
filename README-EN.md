<!--suppress ALL -->
<p align="center">
  <img src="resources/logo.svg" width="100" alt="Lyricon Logo"/>
</p>

<h1 align="center">Lyricon</h1>

<p align="center">
  <b>An Xposed-based Status Bar Lyric Enhancement Tool for Android</b>
</p>

<p align="center">
  <a href="https://github.com/tomakino/lyricon/releases"><img src="https://img.shields.io/github/v/release/tomakino/lyricon?style=flat&color=blue" alt="Version"></a>
  <a href="https://github.com/tomakino/lyricon/releases"><img src="https://img.shields.io/github/downloads/tomakino/lyricon/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/tomakino/lyricon/commits"><img src="https://img.shields.io/github/last-commit/tomakino/lyricon?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/tomakino/lyricon/blob/main/LICENSE"><img src="https://img.shields.io/github/license/tomakino/lyricon?style=flat" alt="License"></a>
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

- 🎤 **Lyric Display** — Supports word-by-word lyrics, translations, and duet modes.
- 🧩 **Modular Design** — Extend lyric sources for various players through an independent plugin
  system.
- 🎨 **Visual Customization** — Adjust font styles, logos, coordinate offsets, and animations to fit
  your UI.

---

## 🚀 Quick Start

### 📋 Requirements

- **System**: Android 8.1 (API 27) or higher.
- **Prerequisites**: Device must be **Rooted** with the **LSPosed** (or compatible Xposed) framework
  installed.

> [!TIP]
> It is recommended to use the latest stable version of LSPosed for optimal compatibility.

### ⚙️ Installation & Setup

1. **Download Core Service**: Get it from
   the [GitHub Core Release](https://github.com/tomakino/lyricon/releases/tag/core).
2. **Download Main App**: Get the Lyricon app
   from [Releases](https://github.com/tomakino/lyricon/releases).
3. **Activate Module**: Enable "Lyricon" in the LSPosed manager and ensure the **System UI** scope
   is checked.
4. **Apply Changes**: Restart System UI or reboot your device to complete the Hook injection.
5. **Install Plugins**: Download the corresponding plugin for your music player
   from [LyricProvider](https://github.com/tomakino/LyricProvider).
6. **Configuration**: Open the Lyricon app to adjust position anchors, width, and visual styles.
7. **Enjoy**: Play music and check your status bar for live lyrics.

---

## 🧩 Ecosystem & Support

| Category           | Links                                                                                                      | Description                                 |
|:-------------------|:-----------------------------------------------------------------------------------------------------------|:--------------------------------------------|
| **Plugin Library** | [LyricProvider Repo](https://github.com/tomakino/LyricProvider)                                            | Plugins for mainstream music platforms      |
| **Development**    | [Dev Guide](https://github.com/tomakino/lyricon/blob/master/lyric/bridge/provider/README-EN.md)            | Learn how to build lyric provider plugins   |
| **Subscription**   | [Subscription Guide](https://github.com/tomakino/lyricon/blob/master/lyric/bridge/subscriber/README-EN.md) | Methods for 3rd-party apps to access lyrics |

### 💡 Native Support Apps

- [**ConePlayer**](https://coneplayer.trantor.ink/)
- **Flamingo**
- [**BBPlayer**](https://bbplayer.roitium.com/)
- **MobiMusic**
- [**Kanade**](https://github.com/rcmiku/Kanade)
- **Sollin Player**
- [**QZ Music**](https://github.com/lqtmcstudio/QZMusic)

---

## 👥 Contributors

[![Contributors](https://contrib.rocks/image?repo=tomakino/lyricon)](https://github.com/tomakino/lyricon/graphs/contributors)

---

## ⭐ Star History

<p align="center">
  <a href="https://www.star-history.com/#tomakino/lyricon&Date">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=tomakino/lyricon&type=Date&theme=dark" />
      <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=tomakino/lyricon&type=Date" />
      <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=tomakino/lyricon&type=Date" width="600" />
    </picture>
  </a>
</p>

---

### 👀 Traffic

<p align="center">
  <img src="https://count.getloli.com/get/@tomakino_lyricon?theme=minecraft" alt="Visitor Count" />
</p>