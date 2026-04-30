# Lyric Bridge 文档

Lyric Bridge 提供面向第三方应用和插件的歌词数据接入能力，主要包含两类接口：

- **Provider**：由音乐播放器或歌词插件接入，用于向 Lyricon 推送歌曲、歌词、播放状态和显示配置。
- **Subscriber**：由第三方应用接入，用于订阅 Lyricon 当前活跃播放器、歌曲、歌词和播放状态。

## 快速入口

- [Provider 开发文档](provider/README.md)
- [Subscriber 接入文档](subscriber/README.md)

## 如何选择

| 目标                     | 应接入接口      |
|:-----------------------|:-----------|
| 让自己的播放器把歌词显示到 Lyricon  | Provider   |
| 为某个播放器开发歌词来源插件         | Provider   |
| 在自己的应用里读取 Lyricon 当前歌词 | Subscriber |
| 监听当前活跃播放器和播放进度         | Subscriber |

## 环境说明

- Provider 和 Subscriber 均面向 Android 应用。
- Provider/Subscriber Bridge 的最低运行版本由库实现决定，目前 Android 8.1 以下会返回空实现。
- 主体应用 README 中的 Lyricon 模块运行要求仍以项目根目录文档为准。
- Provider 可使用 LocalCentralService 在无 LSPosed 环境下进行基本测试。
- Subscriber 需要安装 Lyricon 核心服务。

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

## 相关链接

- [Lyricon Releases](https://github.com/tomakino/lyricon/releases)
- [LyricProvider 仓库](https://github.com/tomakino/LyricProvider)
- [Provider 源码](https://github.com/tomakino/lyricon/tree/master/lyric/bridge/provider)
- [Subscriber 源码](https://github.com/tomakino/lyricon/tree/master/lyric/bridge/subscriber)
