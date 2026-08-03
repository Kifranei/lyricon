<!--suppress ALL -->

<p align="center">
  <img src="resources/logo.svg" width="100" alt="Lyricon Logo"/>
</p>

<h1 align="center">Lyricon</h1>

<p align="center">
  <b>An Xposed-based Android status-bar lyric enhancer (personal enhanced fork)</b>
</p>

<p align="center">
  <a href="https://github.com/kifranei/lyricon/releases"><img src="https://img.shields.io/github/v/release/kifranei/lyricon?style=flat&color=blue" alt="Version"></a>
  <a href="https://github.com/kifranei/lyricon/releases"><img src="https://img.shields.io/github/downloads/kifranei/lyricon/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/kifranei/lyricon/commits"><img src="https://img.shields.io/github/last-commit/kifranei/lyricon?style=flat" alt="Last Commit"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/文档-中文-red.svg" alt="ZH"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/IXif8Zi0Iq"><img src="https://img.shields.io/badge/QQ%20Group-0084FF?style=flat&logo=qq&logoColor=white" alt="QQ Group"></a>
  <a href="https://t.me/cslyric"><img src="https://img.shields.io/badge/Telegram-0084FF?style=flat&logo=telegram&logoColor=white" alt="Telegram"></a>
</p>

<p align="center">
  <img src="resources/z.gif" alt="Demo" width="539"/>
</p>

---

## ⚠ About This Fork

This repository is a **personal enhanced fork** of [Lyricon](https://github.com/tomakino/Lyricon). It keeps the original status-bar lyrics, lyric-source plugins and style configuration, and adds a set of custom features plus Xiaomi / ColorOS adaptations.

- Tracks upstream; when a feature is already provided natively upstream, upstream takes precedence.
- Coexists with the original but uses a **different application id** (`io.github.kifranei.lyricon.fork`), so it does not overwrite the original install.

---

## ✨ Features

### Original

- 🎤 **Lyric display** — per-word lyrics, translations, duet mode.
- 🧩 **Modular design** — a plugin system to extend lyric sources for different players.
- 🎨 **Visual customization** — font style, logo display, coordinate offsets, animations.

### Fork Enhancements

- 🏝️ **Xiaomi super island (HyperOS dynamic island) integration** — automatically hides the island while the lyric is showing and restores it seamlessly afterwards; can instead auto-shrink the lyric width when the island appears.
- 🌈 **Rainbow lyrics** — a built-in one-tap rainbow gradient (light / dark palettes), no manual color picking, still overridable.
- ✨ **Sustain glow** — a breathing glow on long-held highlighted notes, with HDR brightening and rainbow gradient support.
- 🔆 **HDR highlight** — pushes the current highlight beyond SDR brightness on HDR / wide-gamut displays (optional, off by default).
- 💧 **Liquid-glass bottom bar** — the app's main screen supports a docked bar (gaussian-blurred background) and a liquid-glass floating bar.
- 🪞 **Redesigned About page** — shader-driven animated background, frosted-glass cards.
- 🧊 **libxposed API 101 / 102** — supports LSPosed 1.0.2.

---

## 🚀 Getting Started

### 📋 Requirements

- **OS**: Android 10 (API 29) or newer.
- **Prerequisites**: a **rooted** device with **LSPosed** supporting **libxposed API 101 / 102** (e.g. LSPosed 1.0.2) or a compatible Xposed framework.

> [!TIP]
> Prefer a framework version that explicitly supports API 102 (API 101 is still supported). Temporary root is discouraged — a fragile Zygote may cause unexpected issues.

### ⚙️ Installation

1. **Install the app** from [Releases](https://github.com/kifranei/lyricon/releases).
2. **Activate the module** in LSPosed, enable the **System UI (`com.android.systemui`)** scope; on Xiaomi, also enable **`miui.systemui.plugin`** for super-island integration.
3. **Restart** System UI to inject the hooks.
4. **Install a plugin** for your player from [LyricProvider](https://github.com/tomakino/LyricProvider).
5. **Tune** the anchor, width and visual style in the app. To avoid overlapping the clock, `clock` is hidden by default while lyrics show; set its view rule to "Default" to keep it visible.
6. **Test** by playing music and checking the status bar.

---

## 🧩 Ecosystem & Support

| Category    | Link                                                              | Notes                    |
|:------------|:-----------------------------------------------------------------|:-------------------------|
| **Plugins** | [LyricProvider](https://github.com/tomakino/LyricProvider)        | Adapters for major players |
| **Docs**    | [Doc center](https://tomakino.github.io/lyricon/)                | App & Lyric docs         |

### 💡 Natively Adapted Apps

- [**Cone Player**](https://coneplayer.trantor.ink/)
- **Flamingo**
- [**BBPlayer**](https://bbplayer.roitium.com/)
- **MobiMusic**
- [**Kanade**](https://github.com/rcmiku/Kanade)
- **Sollin Player**
- [**QZ Music**](https://github.com/lqtmcstudio/QZMusic)
- [**Pure Music**](https://github.com/pure-music/PureMusic)
- [**Smart Music Next**](https://qun.qq.com/universal-share/share?ac=1&authKey=k1hftnugk%2Bx5FZnOePE2RTS%2ByBftX2E87Trhz59sfxtVtvC3nw1MXnlxycVUIPZw&busi_data=eyJncm91cENvZGUiOiIzMzA0NzM2OTYiLCJ0b2tlbiI6IlB0NWpkSW0zWTA0UXBCTHFFdjZ0SDBsN014aUVnTitxMllFUnlMV0JpdTJEem1sdDBvRWZEM2p0RXJGVUFpZTgiLCJ1aW4iOiIyOTIwNTMzMzczIn0%3D&data=388N05tm4gkrgDLeoysN-LIYOHsCk5mUfrcBBVE9UW3WyoWG_DxkLZqDttvrptZWN5VOQWvYBwZ7d3MgKUDmTg&svctype=4&tempid=h5_group_info)
- [**LunaBeat**](https://github.com/2755337087/LunaBeat)
- [**Halcyon**](https://github.com/Kifranei/Halcyon)
- [**NeriPlayer**](https://github.com/cwuom/NeriPlayer)
- [**Prism Music**](https://github.com/Ryderwe/PrismMusic-Release)


#### Adapted but your player is missing? Please [open an issue](https://github.com/tomakino/lyricon/issues).

---

## 👥 Contributors

[![Contributors](https://contrib.rocks/image?repo=kifranei/lyricon)](https://github.com/kifranei/lyricon/graphs/contributors)

---

### 👀 Visitors

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_lyricon?theme=moebooru" alt="Visitor Count" />
</p>
