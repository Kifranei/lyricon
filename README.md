<!--suppress ALL -->

<p align="center">
  <img src="resources/logo.svg" width="100" alt="词幕 Logo"/>
</p>

<h1 align="center">词幕 · Fork</h1>

<p align="center">
  <b>基于 Xposed 框架的 Android 状态栏歌词增强工具（个人增强 Fork）</b>
</p>

<p align="center">
  <a href="https://github.com/kifranei/lyricon/releases"><img src="https://img.shields.io/github/v/release/kifranei/lyricon?style=flat&color=blue" alt="Version"></a>
  <a href="https://github.com/kifranei/lyricon/releases"><img src="https://img.shields.io/github/downloads/kifranei/lyricon/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/kifranei/lyricon/commits"><img src="https://img.shields.io/github/last-commit/kifranei/lyricon?style=flat" alt="Last Commit"></a>
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

## ⚠ 关于本 Fork

本仓库是 [Lyricon](https://github.com/tomakino/Lyricon) 的**个人增强 Fork**，在保留原有状态栏歌词、歌词源插件、样式配置等能力的基础上，加入了一批个人定制功能与针对小米 / ColorOS 的适配。

- 追随上游更新；若某项功能上游已原生提供，则以上游为准。
- 与原版共存但**应用包名不同**（`io.github.kifranei.lyricon.fork`），不会覆盖升级原版。

---

## ✨ 功能特性

### 原有能力

- 🎤 **歌词展示** — 逐字歌词、翻译显示、对唱模式。
- 🧩 **模块化设计** — 独立插件系统，可扩展不同播放器的歌词源。
- 🎨 **视觉自定义** — 字体样式、Logo 显示、坐标偏移、动画效果均可调。

### 本 Fork 增强

- 🏝️ **小米超级岛（HyperOS 灵动岛）联动** — 显示歌词时自动隐藏灵动岛，歌词消失后无缝恢复；也可改为"岛出现时自动缩短歌词宽度"。
- 🌈 **彩虹歌词** — 内置一键彩虹渐变配色（亮 / 暗两套），无需手动配色，也可自定义覆盖。
- ✨ **拉长音发光** — 长音节高亮的呼吸发光效果，支持 HDR 增亮与彩虹渐变。
- 🔆 **HDR 高亮** — 在支持 HDR / 广色域的设备上让当前歌词高亮突破 SDR 亮度（可选，默认关闭）。
- 💧 **液态玻璃底栏** — App 主界面支持停靠底栏（背景高斯模糊）与液态玻璃悬浮底栏两种形态。
- 🪞 **全新关于页** — 着色器动态背景、磨砂玻璃卡片。
- 🧊 **libxposed API 101 / 102** — 适配 LSPosed 1.0.2。

---

## 🚀 快速上手

### 📋 环境要求

- **系统版本**：Android 10 (API 29) 及以上。
- **前置条件**：设备已 **Root**，并安装支持 **libxposed API 101 / 102** 的 **LSPosed**（如 LSPosed 1.0.2）或兼容 Xposed 框架。

> [!TIP]
> 建议使用明确支持 API 102 的框架版本（API 101 仍受支持）。不建议临时 Root，Zygote 进程脆弱可能引发未知问题。

### ⚙️ 安装与配置

1. **下载主体应用**：从 [Releases](https://github.com/kifranei/lyricon/releases) 下载并安装词幕主体。
2. **激活模块**：在 LSPosed 中启用"词幕 · Fork"模块，勾选 **系统界面 (`com.android.systemui`)** 作用域；小米设备如需超级岛联动，请一并勾选 **`miui.systemui.plugin`**。
3. **重启生效**：重启系统界面或重启设备完成 Hook 注入。
4. **安装插件**：根据播放器在 [LyricProvider](https://github.com/tomakino/LyricProvider) 下载对应插件。
5. **参数调节**：进入词幕，按屏幕情况调整位置锚点、宽度与视觉样式。为避免歌词与时间重叠，`clock` 默认在歌词显示时隐藏；若需同时显示，请把它的视图规则设为"默认"。
6. **运行测试**：播放音乐，检查状态栏显示。

---

## 🧩 生态与支持

| 类别       | 资源链接                                                          | 说明             |
|:---------|:--------------------------------------------------------------|:---------------|
| **插件库**  | [LyricProvider 仓库](https://github.com/tomakino/LyricProvider) | 主流音乐平台适配插件     |
| **开发文档** | [文档中心](https://tomakino.github.io/lyricon/)                   | App 与 Lyric 文档 |

### 💡 已原生适配的应用

- [**光锥音乐**](https://coneplayer.trantor.ink/)
- **Flamingo**
- [**BBPlayer**](https://bbplayer.roitium.com/)
- **MobiMusic**
- [**Kanade**](https://github.com/rcmiku/Kanade)
- **Sollin Player**
- [**QZ Music**](https://github.com/lqtmcstudio/QZMusic)
- [**Halcyon**](https://github.com/Kifranei/Halcyon) — 注重体验的本地音乐播放器，也是本项目关于页与视觉设计的来源。
- [**NeriPlayer**](https://github.com/cwuom/NeriPlayer) — 简洁优雅的音乐播放器。
- [**棱镜音乐 PrismMusic**](https://github.com/Ryderwe/PrismMusic-Release) — 功能丰富的第三方音乐客户端。

#### 已适配了但没有你的播放器？请[提交 issue](https://github.com/tomakino/lyricon/issues)。

---

## 👥 贡献者

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

### 👀 访问统计

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_lyricon?theme=moebooru-h" alt="Visitor Count" />
</p>
