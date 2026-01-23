# Lyricon 插件开发指南

本文档面向具备 Android 开发基础的读者，说明如何开发并接入 Lyricon Provider 插件。

## 一、添加依赖

![version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider)

在模块的 `build.gradle.kts` 中添加依赖：

```kotlin
implementation("io.github.proify.lyricon:provider:0.1.63")
```

## 二、配置 `AndroidManifest.xml`

在 `application` 节点下声明插件的基础元信息：

```xml

<application>
    <meta-data android:name="lyricon_module" android:value="true" />

    <meta-data android:name="lyricon_module_author" android:value="your name" />

    <meta-data android:name="lyricon_module_description" android:value="module description" />
</application>
```

### 模块标签（可选）

用于声明插件支持的歌词特性，仅用于展示用途：

```xml

<meta-data android:name="lyricon_module_tags" android:resource="@array/lyricon_module_tags" />
```

```xml

<string-array name="lyricon_module_tags">
    <item>$syllable</item>
    <item>$translation</item>
</string-array>
```

#### 支持的标签代码

| Code           | 含义          |
|----------------|-------------|
| `$syllable`    | 支持逐字 / 动态歌词 |
| `$translation` | 支持歌词翻译显示    |

## 三、创建 `LyriconProvider`

```kotlin
val provider = LyriconProvider(
    context
    // logo = ProviderLogo.fromDrawable(context, R.drawable.logo)
)
```

### 连接状态监听

```kotlin
provider.service.addConnectionListener {
    onConnected { }
    onReconnected { }
    onDisconnected { }
    onConnectTimeout { }
}
```

### 注册 Provider

初始化完成后需显式注册：

```kotlin
provider.register()
```

注册成功后，Lyricon 将开始接收该 Provider 推送的播放与歌词数据。

## 四、播放器控制

```kotlin
val player = provider.player

// 设置播放状态（true 表示正在播放）
player.setPlaybackState(true)

// 发送纯文本歌词
player.sendText("我无法只是普通朋友")
```

该方式适用于不需要时间轴控制的简单歌词展示场景。

## 五、高级用法

### 1. 设置歌曲占位信息

在歌词尚未准备完成时，可先发送歌曲基础信息：

```kotlin
player.setSong(
    Song(
        name = "普通朋友",
        artist = "陶喆"
    )
)
```

### 2. 按行时间轴歌词

仅包含行级起止时间，适用于逐行高亮显示：

```kotlin
player.setSong(
    Song(
        id = "歌曲唯一标识",
        name = "普通朋友",
        artist = "陶喆",
        duration = 2000,
        lyrics = listOf(
            RichLyricLine(
                end = 1000,
                text = "我无法只是普通朋友"
            ),
            RichLyricLine(
                begin = 1000,
                end = 2000,
                text = "不想做普通朋友"
            )
        )
    )
)
```

### 3. 完整歌词结构（逐字 / 次要歌词 / 翻译）

```kotlin
player.setSong(
    Song(
        id = "歌曲唯一标识",
        name = "普通朋友",
        artist = "陶喆",
        duration = 1000,
        lyrics = listOf(
            RichLyricLine(
                end = 1000,
                text = "我无法只是普通朋友",
                words = listOf(
                    LyricWord(text = "我", end = 200),
                    LyricWord(text = "无法", begin = 200, end = 400),
                    LyricWord(text = "只是", begin = 400, end = 600),
                    LyricWord(text = "普通", begin = 600, end = 800),
                    LyricWord(text = "朋友", begin = 800, end = 1000)
                ),
                secondary = "（不想做普通朋友）",
                translation = "I can't just be a normal friend"
            )
        )
    )
)

// 控制翻译歌词显示状态
player.setDisplayTranslation(true)
```

## 其它说明

* Java 调用方式未经过完整验证，不保证 API 友好性或稳定性。