# Provider

Provider 用于向词幕发送歌曲、歌词、播放状态和显示配置。适用于播放器或歌词来源插件。

## 接入流程

1. 添加 Provider 依赖。
2. 配置 `AndroidManifest.xml` 元数据。
3. 创建 `LyriconProvider`。
4. 调用 `register()` 注册。
5. 通过 `provider.player` 发送数据。
6. 不再使用时调用 `unregister()` 或 `destroy()`。

## 依赖

![provider version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

```kotlin
implementation("io.github.proify.lyricon:provider:0.1.70")
```

## 文档

- [快速开始](quick-start.md)
- [Manifest 配置](manifest.md)
- [连接生命周期](connection.md)
- [播放器控制](player-control.md)
- [歌词数据结构](lyrics-model.md)
- [本地测试](local-testing.md)
- [常见问题](faq.md)

## 最小示例

```kotlin
val provider = LyriconFactory.createProvider(context)

provider.register()

provider.player.setPlaybackState(true)
provider.player.sendText("我无法只是普通朋友")
```
