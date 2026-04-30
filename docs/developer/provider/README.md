# Provider 开发文档

Provider 是 Lyricon 的歌词提供端接口。音乐播放器或歌词插件通过 Provider 将当前歌曲、歌词、播放状态和显示配置发送给
Lyricon 中心服务，再由 Lyricon 负责展示或分发。

## 适用场景

- 音乐播放器希望接入 Lyricon 状态栏歌词显示。
- 歌词插件希望为第三方播放器提供歌词数据。
- 应用需要向 Lyricon 推送纯文本歌词、行级歌词或逐字歌词。
- 应用希望同步播放进度、播放状态、翻译开关或罗马音开关。

## 接入流程

1. 添加 Provider 依赖。
2. 在 `AndroidManifest.xml` 中声明 Lyricon 插件元数据。
3. 创建 `LyriconProvider`。
4. 监听连接状态。
5. 调用 `register()` 注册到中心服务。
6. 通过 `provider.player` 推送歌曲、歌词和播放状态。
7. 在不再使用时调用 `unregister()` 或 `destroy()`。

## 文档目录

- [快速开始](quick-start.md)
- [Manifest 配置](manifest.md)
- [连接生命周期](connection.md)
- [播放器控制](player-control.md)
- [歌词数据结构](lyrics-model.md)
- [本地测试](local-testing.md)
- [常见问题](faq.md)

## 核心类型

| 类型                 | 说明                       |
|:-------------------|:-------------------------|
| `LyriconFactory`   | 创建 Provider 实例的工厂        |
| `LyriconProvider`  | Provider 入口，负责注册、注销和资源释放 |
| `RemotePlayer`     | 歌曲、歌词、播放状态发送入口           |
| `ProviderInfo`     | Provider 注册信息            |
| `ProviderLogo`     | Provider 或播放器图标          |
| `ProviderMetadata` | Provider 附加元数据           |
| `ProviderService`  | 暴露给中心服务调用的本地命令处理器        |

## 最小示例

```kotlin
val provider = LyriconFactory.createProvider(context)

provider.service.addConnectionListener {
    onConnected { }
    onReconnected { }
    onDisconnected { }
    onConnectTimeout { }
}

provider.register()

provider.player.setPlaybackState(true)
provider.player.sendText("我无法只是普通朋友")
```
