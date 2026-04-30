# App 使用文档

词幕是基于 Xposed / LSPosed 的 Android 状态栏歌词工具。主体应用用于配置显示效果，歌词展示运行在 System
UI 作用域中。

## 要求

- Android 9.0 (API 28) 及以上。
- 设备已 Root。
- 已安装 LSPosed 或兼容的 Xposed 框架。

## 安装

1. 从 [Releases](https://github.com/tomakino/lyricon/releases) 下载并安装词幕。
2. 在 LSPosed 中启用词幕模块。
3. 勾选 **系统界面 (System UI)** 作用域。
4. 重启 System UI 或重启设备。
5. 打开词幕，确认模块状态正常。

## 插件

词幕通过插件适配播放器歌词来源。请根据使用的播放器安装对应插件。

- [LyricProvider 插件仓库](https://github.com/tomakino/LyricProvider)

插件安装后，重新打开播放器并播放歌曲。如果没有歌词，先确认插件是否支持当前播放器版本。

## 配置

常用配置项：

| 配置      | 说明            |
|:--------|:--------------|
| 位置锚点    | 设置歌词在状态栏中的位置  |
| 显示宽度    | 限制歌词区域，避免遮挡图标 |
| 坐标偏移    | 微调横向和纵向位置     |
| 字体样式    | 调整文字显示效果      |
| Logo 显示 | 控制播放器或插件 Logo |
| 动画效果    | 控制歌词切换和滚动效果   |

不同 ROM 的状态栏布局差异较大，建议先确认歌词可显示，再调整位置和样式。

## 支持的应用

- [ConePlayer](https://coneplayer.trantor.ink/)
- Flamingo
- [BBPlayer](https://bbplayer.roitium.com/)
- MobiMusic
- [Kanade](https://github.com/rcmiku/Kanade)
- Sollin Player
- [QZ Music](https://github.com/lqtmcstudio/QZMusic)

## 排障

### 没有显示歌词

- 确认 LSPosed 中已启用词幕。
- 确认作用域包含 **系统界面 (System UI)**。
- 确认已重启 System UI 或设备。
- 确认已安装对应播放器插件。
- 确认当前歌曲有可用歌词。

### 位置异常

进入词幕调整位置锚点、显示宽度和坐标偏移。

### 没有逐字歌词或翻译

逐字歌词和翻译依赖歌词来源。插件未提供对应数据时，只能显示普通歌词。
