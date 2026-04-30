# Provider 常见问题

## 注册后为什么没有显示歌词？

可能原因：

- Lyricon 中心服务未运行。
- LSPosed / Xposed 作用域未正确配置。
- Provider 未调用 `register()`。
- Provider 已连接但没有调用 `sendText()` 或 `setSong()`。
- 使用 LocalCentralService 测试时未授予悬浮窗权限。

## `sendText()` 和 `setSong()` 有什么区别？

`sendText()` 用于发送无时间轴的普通文本，会清除之前设置的歌曲信息。

`setSong()` 用于发送结构化歌曲和歌词，适合行级歌词、逐字歌词、翻译歌词等场景。

## 什么时候用 `setPosition()`？

`setPosition()` 用于持续同步当前播放位置，例如播放器正常播放时定期更新进度。

## 什么时候用 `seekTo()`？

`seekTo()` 表示主动跳转到指定位置，例如用户拖动进度条、切歌后恢复播放位置。

## 翻译歌词为什么不显示？

需要同时满足：

- `RichLyricLine` 中提供了翻译字段。
- 调用了 `player.setDisplayTranslation(true)`。
- 展示端配置允许显示翻译。

## 罗马音为什么不显示？

需要同时满足：

- 歌词数据中提供了罗马音内容。
- 调用了 `player.setDisplayRoma(true)`。
- 展示端配置允许显示罗马音。

## 是否支持 Java？

Java 调用方式未经过完整验证，不保证 API 友好性或稳定性。建议使用 Kotlin 接入。

## 发布前需要检查什么？

- 删除 LocalCentralService 的 `centralPackageName` 测试配置。
- 确认 `AndroidManifest.xml` 已声明 Lyricon 模块元数据。
- 确认模块标签与实际能力一致。
- 确认连接断开和应用退出时会释放资源。
