# Subscriber 接入文档

Subscriber 是 Lyricon 的歌词订阅端接口。第三方应用可以通过 Subscriber 订阅当前活跃播放器、歌曲、歌词、播放状态和显示配置变化。

## 适用场景

- 在自己的应用中读取 Lyricon 当前播放歌曲。
- 监听当前活跃播放器变化。
- 接收结构化歌词或纯文本歌词。
- 根据播放进度同步自定义歌词展示。
- 监听翻译歌词或罗马音显示开关。

## 接入流程

1. 添加 Subscriber 依赖。
2. 确认设备已安装 Lyricon 核心服务。
3. 创建 `LyriconSubscriber`。
4. 添加连接生命周期监听器。
5. 调用 `subscribeActivePlayer()` 订阅活跃播放器。
6. 调用 `register()` 注册到中心服务。
7. 在不再使用时调用 `unregister()` 或 `destroy()`。

## 文档目录

- [快速开始](quick-start.md)
- [连接生命周期](connection.md)
- [活跃播放器](active-player.md)
- [回调说明](callbacks.md)
- [常见问题](faq.md)

## 核心类型

| 类型                           | 说明                             |
|:-----------------------------|:-------------------------------|
| `LyriconFactory`             | 创建 Subscriber 实例的工厂            |
| `LyriconSubscriber`          | Subscriber 入口，负责注册、注销、订阅和资源释放  |
| `ActivePlayerListener`       | 活跃播放器状态监听器                     |
| `SimpleActivePlayerListener` | `ActivePlayerListener` 的空实现适配器 |
| `ConnectionListener`         | 连接生命周期监听器                      |
| `ProviderInfo`               | 当前活跃 Provider 信息               |
| `SubscriberInfo`             | Subscriber 注册信息                |

## 最小示例

```kotlin
val subscriber = LyriconFactory.createSubscriber(context)

subscriber.subscribeActivePlayer(object : SimpleActivePlayerListener {
    override fun onSongChanged(song: Song?) {
        // 接收当前歌曲和结构化歌词
    }

    override fun onReceiveText(text: String?) {
        // 接收纯文本歌词
    }
})

subscriber.register()
```
