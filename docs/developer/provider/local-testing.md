# Provider 本地测试

正常情况下，Provider 需要通过 Lyricon 在 LSPosed / Xposed 环境中激活的中心服务进行测试。如果当前设备无法使用
LSPosed，可以使用 LocalCentralService 做基础测试。

## LocalCentralService

LocalCentralService 是一个用于测试的本地中心服务实现，提供 Lyricon 中心服务的部分能力，方便在无
LSPosed 环境下验证 Provider 接入流程。

下载地址：

- [LocalCentralService](https://github.com/proify/lyricon/releases/tag/localcentral)

## 测试步骤

1. 安装 LocalCentralService。
2. 打开应用并激活服务。
3. 授予悬浮窗权限。
4. 在 Provider 创建时指定本地中心服务包名。
5. 调用 `register()`。
6. 推送播放状态和歌词，检查显示效果。

## Provider 配置

```kotlin
val provider = LyriconFactory.createProvider(
    context,
    centralPackageName = "io.github.lyricon.localcentralapp"
)
```

> [!WARNING]
> `centralPackageName` 指向 LocalCentralService 仅用于测试，正式发布前应删除该配置，使用默认中心服务包名。

## 测试建议

- 先使用 `sendText()` 验证连接是否正常。
- 再使用 `setSong()` 验证结构化歌词。
- 最后测试播放进度、翻译开关和罗马音开关。
- 如果连接超时，先确认 LocalCentralService 是否已启动并授权悬浮窗权限。
