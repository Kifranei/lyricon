# App 使用文档

Lyricon 是基于 Xposed / LSPosed 的 Android 状态栏歌词增强工具。主体应用负责模块入口、显示参数配置和插件协作；真正的歌词展示运行在 System UI 作用域中。

## 适合谁使用

- 想在状态栏持续查看当前播放歌词的用户。
- 希望显示逐字歌词、翻译歌词或对唱歌词的用户。
- 使用第三方播放器，并希望通过插件补充歌词来源的用户。
- 愿意使用 Root 与 LSPosed / Xposed 环境的 Android 高级用户。

## 功能概览

| 能力 | 说明 |
|:---|:---|
| 状态栏歌词 | 在 System UI 状态栏区域显示当前播放歌词 |
| 逐字歌词 | 支持动态逐字歌词进度，效果取决于歌词来源 |
| 翻译显示 | 支持展示翻译歌词，可由 Provider 和显示设置共同控制 |
| 对唱模式 | 支持适配对唱或次要歌词展示 |
| 视觉自定义 | 可调整字体、Logo、坐标偏移、宽度和动画 |
| 插件化来源 | 通过 LyricProvider 插件扩展不同播放器的歌词来源 |

## 环境要求

- Android 9.0 (API 28) 及以上。
- 设备已获取 Root 权限。
- 已安装并启用 LSPosed，或兼容的 Xposed 框架。

> [!TIP]
> 为保证稳定性，建议使用 LSPosed 最新正式版本。

## 安装与激活

1. 从 [Releases](https://github.com/tomakino/lyricon/releases) 下载并安装 Lyricon 主体应用。
2. 打开 LSPosed 管理器，启用 Lyricon 模块。
3. 在作用域中勾选 **系统界面 (System UI)**。
4. 重启 System UI，或直接重启设备。
5. 打开 Lyricon 主体应用，确认模块状态正常。

如果模块未生效，优先检查 LSPosed 作用域是否包含 System UI，以及设备是否已重启 System UI。

## 安装歌词插件

Lyricon 通过插件系统适配不同播放器。主体应用安装后，还需要根据实际使用的播放器安装对应插件。

插件入口：

- [LyricProvider 仓库](https://github.com/tomakino/LyricProvider)

安装建议：

- 只安装当前实际使用播放器对应的插件。
- 插件安装后，重新打开播放器并播放歌曲。
- 如果歌词没有显示，先确认插件支持当前播放器版本。

## 界面配置

进入 Lyricon 主体应用后，可根据设备状态栏布局调整显示效果。

常见配置项：

| 配置 | 说明 |
|:---|:---|
| 位置锚点 | 控制歌词相对状态栏的位置 |
| 显示宽度 | 控制歌词可用显示区域，避免遮挡图标 |
| 坐标偏移 | 微调歌词横向或纵向位置 |
| 字体样式 | 调整字号、字重或相关文字效果 |
| Logo 显示 | 控制播放器或插件 Logo 是否显示 |
| 动画效果 | 控制歌词切换和滚动动画 |

不同 ROM 的状态栏布局差异较大，建议先使用默认配置确认功能正常，再逐项微调。

## 运行测试

1. 启动已适配的音乐播放器。
2. 播放一首有歌词的歌曲。
3. 观察状态栏是否显示歌词。
4. 如果歌词位置不合适，回到 Lyricon 调整位置锚点、宽度和偏移。
5. 如果只有纯文本或没有逐字效果，检查插件是否支持逐字歌词。

## 已原生适配的应用

- [ConePlayer](https://coneplayer.trantor.ink/)
- Flamingo
- [BBPlayer](https://bbplayer.roitium.com/)
- MobiMusic
- [Kanade](https://github.com/rcmiku/Kanade)
- Sollin Player
- [QZ Music](https://github.com/lqtmcstudio/QZMusic)

如果你的播放器已适配但没有列在这里，可以到 [Issues](https://github.com/tomakino/lyricon/issues) 反馈。

## 常见问题

### 状态栏没有歌词

优先检查：

- Lyricon 模块是否已在 LSPosed 中启用。
- 作用域是否勾选 **系统界面 (System UI)**。
- 是否已重启 System UI 或重启设备。
- 是否安装了当前播放器对应的 LyricProvider 插件。
- 播放器当前歌曲是否有可用歌词。

### 歌词位置不对

不同 ROM、挖孔屏、状态栏图标密度都会影响显示位置。进入 Lyricon 调整位置锚点、显示宽度和坐标偏移。

### 没有逐字歌词或翻译

逐字歌词和翻译依赖歌词来源。插件未提供对应数据时，Lyricon 只能显示普通歌词。

### 重启后失效

检查 LSPosed 模块状态和 System UI 作用域。如果 ROM 有后台限制，也需要确认 Lyricon 和相关插件没有被系统冻结。

## 相关入口

- [项目 README](https://github.com/tomakino/lyricon#readme)
- [Lyricon Releases](https://github.com/tomakino/lyricon/releases)
- [LyricProvider 插件仓库](https://github.com/tomakino/LyricProvider)
- [Lyric 文档](../lyric/README.md)
