---
layout: home

hero:
  name: Lyricon
  text: Android 状态栏歌词增强工具
  tagline: 通过 Xposed / LSPosed 将歌词带到系统状态栏，支持逐字歌词、翻译显示、对唱模式和插件化歌词来源。
  image:
    src: /logo.svg
    alt: Lyricon
  actions:
    - theme: brand
      text: 开始使用
      link: /app/
    - theme: alt
      text: 开发接入
      link: /lyric/bridge/
    - theme: alt
      text: GitHub
      link: https://github.com/tomakino/lyricon

features:
  - icon: 🎤
    title: 状态栏歌词
    details: 在 System UI 中展示当前播放歌词，适合沉浸式听歌和跨应用歌词查看。
    link: /app/
  - icon: 🎨
    title: 可视化调整
    details: 调整位置锚点、显示宽度、字体样式、Logo 展示和动画效果，适配不同状态栏布局。
    link: /app/#界面配置
  - icon: 🧪
    title: 插件生态
    details: 通过 Provider 扩展播放器歌词来源，也可通过 Subscriber 订阅当前歌词数据。
    link: /lyric/bridge/
---

## 文档分区

| 分区 | 面向对象 | 内容 |
|:---|:---|
| [App](./app/README.md) | 普通用户 | 安装、激活、插件安装、界面配置和常见问题 |
| [Lyric](./lyric/README.md) | 开发者 | 歌词数据模型、Bridge 接入和显示相关能力 |
| [Bridge](./lyric/bridge/README.md) | 播放器、插件和第三方应用开发者 | Provider 推送歌词，Subscriber 订阅歌词 |

## 快速上手

1. 从 [Releases](https://github.com/tomakino/lyricon/releases) 下载并安装 Lyricon 主体应用。
2. 在 LSPosed 中启用 Lyricon，并勾选 **系统界面 (System UI)** 作用域。
3. 重启 System UI 或重启设备，让 Hook 生效。
4. 从 [LyricProvider](https://github.com/tomakino/LyricProvider) 安装对应播放器插件。
5. 打开音乐播放器播放歌曲，回到 Lyricon 调整显示位置和样式。

## 开发者入口

| 目标 | 文档 |
|:---|:---|
| 为播放器或歌词插件向 Lyricon 推送歌词 | [Provider 开发文档](./lyric/bridge/provider/README.md) |
| 在第三方应用中读取当前歌词和播放状态 | [Subscriber 接入文档](./lyric/bridge/subscriber/README.md) |
| 了解 Bridge 的整体数据流 | [Lyric Bridge 文档](./lyric/bridge/README.md) |

## Maven 坐标

Provider：

```kotlin
implementation("io.github.proify.lyricon:provider:<version>")
```

Subscriber：

```kotlin
implementation("io.github.proify.lyricon:subscriber:<version>")
```

建议使用 Maven Central 上的最新版本。
