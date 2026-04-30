# Developer 文档

Developer 文档面向播放器、歌词插件和第三方应用开发者。

## 接口

| 接口         | 用途              | 文档                                 |
|:-----------|:----------------|:-----------------------------------|
| Provider   | 向词幕推送歌曲、歌词和播放状态 | [Provider](provider/)     |
| Subscriber | 订阅当前活跃播放器和歌词状态  | [Subscriber](subscriber/) |

## 依赖

Provider：

```kotlin
implementation("io.github.proify.lyricon:provider:<version>")
```

Subscriber：

```kotlin
implementation("io.github.proify.lyricon:subscriber:<version>")
```

建议使用 Maven Central 上的最新版本。

## 运行要求

- Provider / Subscriber 均面向 Android 应用。
- Android 8.1 以下会返回空实现。
- Provider 可使用 LocalCentralService 进行无 LSPosed 环境测试。
- Subscriber 需要可连接到词幕核心服务。
