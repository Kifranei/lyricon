<!--suppress ALL -->

<p align="center">
  <img src="resources/logo.svg" width="100" alt="词幕 Logo"/>
</p>

<h1 align="center">词幕 (Lyricon)</h1>

<p align="center">
  <b>基于 Xposed 框架的 Android 状态栏歌词增强工具</b>
</p>

<p align="center">
  <a href="https://github.com/tomakino/lyricon/releases"><img src="https://img.shields.io/github/v/release/tomakino/lyricon?style=flat&color=blue" alt="Version"></a>
  <a href="https://github.com/tomakino/lyricon/releases"><img src="https://img.shields.io/github/downloads/tomakino/lyricon/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/tomakino/lyricon/commits"><img src="https://img.shields.io/github/last-commit/tomakino/lyricon?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/tomakino/lyricon/blob/main/LICENSE"><img src="https://img.shields.io/github/license/tomakino/lyricon?style=flat" alt="License"></a>
  <a href="README-EN.md"><img src="https://img.shields.io/badge/Document-English-red.svg" alt="EN"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/IXif8Zi0Iq"><img src="https://img.shields.io/badge/QQ交流群-0084FF?style=flat&logo=qq&logoColor=white" alt="QQ Group"></a>
  <a href="https://t.me/cslyric"><img src="https://img.shields.io/badge/Telegram-0084FF?style=flat&logo=telegram&logoColor=white" alt="Telegram"></a>
</p>

<p align="center">
  <img src="resources/z.gif" alt="展示动画" width="539"/>
</p>

---

## ✨ 功能特性

- 🎤 **歌词展示** — 支持逐字歌词、翻译显示及对唱模式。
- 🧩 **模块化设计** — 通过独立插件系统，支持扩展不同播放器的歌词源。
- 🎨 **视觉自定义** — 支持调整字体样式、Logo 显示、坐标偏移及动画效果。

---

## 🚀 快速上手

### 📋 环境要求

- **系统版本**：Android 8.1 (API 27) 及以上。
- **前置条件**：设备需获取 **Root** 权限，并安装 **LSPosed**（或兼容的 Xposed）框架。

> [!TIP]
> 为保证功能稳定，建议使用 LSPosed 最新正式版本。

### ⚙️ 安装与配置

1. **下载核心服务**：从 [GitHub Core](https://github.com/tomakino/lyricon/releases/tag/core) 下载并安装。
2. **下载主体应用**：从 [Releases](https://github.com/tomakino/lyricon/releases) 下载并安装词幕主体。
3. **激活模块**：在 LSPosed 管理器中启用“词幕”模块，并勾选 **系统界面 (System UI)** 作用域。
4. **重启生效**：重启系统界面（System UI）或重启设备以完成 Hook 注入。
5. **安装插件**：根据使用的音乐播放器，在 [LyricProvider](https://github.com/tomakino/LyricProvider)
   下载对应插件。
6. **参数调节**：进入词幕应用，根据实际屏幕情况调整位置锚点、宽度与视觉样式。
7. **运行测试**：启动音乐播放器并播放音乐，检查状态栏是否正常显示。

---

## 🧩 生态与支持

| 类别       | 资源链接                                                                                      | 说明              |
|:---------|:------------------------------------------------------------------------------------------|:----------------|
| **插件库**  | [LyricProvider 仓库](https://github.com/tomakino/LyricProvider)                             | 包含主流音乐平台的适配插件   |
| **开发插件** | [开发指南](https://github.com/tomakino/lyricon/blob/master/lyric/bridge/provider/README.md)   | 了解如何开发自定义歌词提供插件 |
| **订阅接口** | [订阅指南](https://github.com/tomakino/lyricon/blob/master/lyric/bridge/subscriber/README.md) | 第三方应用接入词幕歌词的方法  |

### 💡 已原生适配的应用

- [**光锥音乐**](https://coneplayer.trantor.ink/)
- **Flamingo**
- [**BBPlayer**](https://bbplayer.roitium.com/)
- **MobiMusic**
- [**Kanade**](https://github.com/rcmiku/Kanade)
- **Sollin Player**
- [**QZ Music**](https://github.com/lqtmcstudio/QZMusic)

---

## 👥 贡献者

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

### 👀 访问统计

<p align="center">
  <img src="https://count.getloli.com/get/@tomakino_lyricon?theme=minecraft" alt="Visitor Count" />
</p>