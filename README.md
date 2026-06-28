# Lyricon HDR

> 基于 Lyricon 的个人实验分支，主要用于验证 ColorOS / SystemUI 状态栏歌词的 HDR 高亮、封面颜色与图标灰显问题。

使用Codex协作完成

本仓库不是上游 Lyricon 的稳定发布版。当前代码保留原有状态栏歌词、歌词源插件、样式配置等能力，并在此基础上加入一组面向 HDR 调试的 SystemUI Hook、渲染路径。

<p align="center">
  <img src="resources/z.gif" alt="展示动画" width="539"/>
</p>

## 当前目标

- 在支持 HDR / wide color 的设备上，让当前歌词高亮获得高于 SDR 的亮度表现。
- 针对 ColorOS 状态栏合成链路，验证哪些 HDR 路线真实有效。
- 修复或绕过状态栏环境下歌词颜色、相对进度、封面图标被灰显的问题。
- 保留足够的 LSPosed 日志，便于通过日志判断问题发生在配置、渲染、Surface 还是系统合成阶段。


## 主要改动

### HDR 高亮

- 新增基础样式开关：HDR 高亮、HDR 亮度倍率、局部探针、独立 Surface 探针、独立 Overlay 探针。
- 当前 HDR 亮度倍率范围由 SystemUI Hook 侧限制为 `1.0` 到 `4.0`，默认值为 `1.5`。
- 歌词高亮颜色使用扩展色域打包，彩色高亮会限制过曝并保留饱和度。
- HDR 状态会同步到歌词文本和图标视图，避免只亮文字、不更新封面补偿。

### ColorOS / SystemUI HDR Hook

- 对 StatusBar 根 Surface 尝试提交 HDR dataspace 和 HDR/SDR ratio。
- 对 `ViewRootImpl`、`WindowManager.LayoutParams`、`ThreadedRenderer` 做多路径反射兼容。
- 保留 `Renderer HDR hints forced`、`Surface HDR transaction applied` 等关键日志，方便确认 HDR 是否真正提交到系统层。
- 保留局部 Canvas、独立 Surface、独立 Overlay 三类探针，用于排查 ColorOS 是否压平子 Surface 或 overlay window。

### 封面与图标灰显处理

- `SuperLogo` 已从 `ImageView` 路线改为普通 `View` 自绘，减少被 ColorOS/SystemUI 当作状态栏图标统一灰显或 tint 的概率。
- 专辑封面使用自绘 bitmap shader 绘制，支持圆形和圆角方形封面。
- HDR 开启时对封面做轻量饱和度、对比度、亮度补偿。
- 新增 `Cover render state:` LSPosed 日志，用于确认封面是否已经走自绘路径、是否残留 tint/filter/alpha。

### 封面取色与自定义颜色

- 保留封面主色、封面渐变色和自定义颜色逻辑。
- 相对进度高亮与普通歌词高亮共用 HDR 颜色保护逻辑，避免彩色高亮在 HDR 状态下回退成灰或白。
- 日志中 `Cover palette extracted` 和 `Status color applied` 可用于判断封面源图是否仍有有效颜色。

## 已知限制

- ColorOS 可能在状态栏合成阶段对某些子 Surface、overlay window 或图标路径做 tonemap / 灰显处理。本分支只能绕过部分路径，不能保证所有机型都实现真正局部 HDR。
- 当前确认最有价值的 HDR 路线是 StatusBar 根 Surface HDR。它可能影响整个状态栏，而不是严格只影响歌词像素。
- 独立 Surface / Overlay 探针主要用于诊断；在部分 ColorOS 版本上可能提交成功但视觉无变化。

## 运行环境

- Android 10 / API 29 及以上。
- 已 Root 的设备。
- LSPosed 或兼容 Xposed 框架。
- 作用域需要勾选系统界面：`com.android.systemui`。
- 推荐在支持 HDR 显示、wide color 或高亮度 headroom 的 ColorOS 设备上测试。

## 构建

仅编译 Kotlin / 检查模块引用：

```powershell
.\gradlew.bat :xposed:compileStandardDebugKotlin :app:compileStandardDebugKotlin
```

构建可安装 APK：

```powershell
.\gradlew.bat :lyricon:assembleStandardDebug
```

注意：`:app` 是库模块，`assembleStandardDebug` 生成的是 AAR，不是可安装 APK。可安装 APK 来自 `:lyricon`。

当前 `lyricon/build.gradle.kts` 中 debug 和 release 都使用 `release` 签名配置。构建 APK 前需要满足以下任一方式：

- 在 `lyricon/release.jks` 放置签名文件。
- 设置环境变量：`RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。
- 本地自行调整 debug 签名配置，但不要把私钥提交到仓库。

## 安装与测试

1. 构建并安装 `:lyricon` APK。
2. 在 LSPosed 中启用模块，并将作用域设为系统界面。
3. 重启 SystemUI 或重启设备。
4. 打开 Lyricon，进入基础样式中的 HDR 区域。
5. 开启 HDR 高亮，设置 HDR 亮度倍率。建议先从 `2.0` 开始，再测试 `3.5` 或 `4.0`。
6. 播放音乐，观察当前歌词高亮、相对进度、状态栏系统图标和封面图标。

## 建议日志检查点

出现视觉问题时，优先收集 LSPosed 日志并搜索以下关键词：

```text
refreshHdrHighlightState
HDR enabled, ratio=
Renderer HDR hints forced
Surface HDR transaction applied
Probe Surface HDR transaction applied
HDR overlay probe added
Cover palette extracted
Status color applied
Cover render state
```

判断方向：

- 有 `HDR enabled` 但没有 `Surface HDR transaction applied`：优先排查 SystemUI Hook 或 SurfaceControl。
- 有 `Surface HDR transaction applied` 但视觉无变化：大概率是系统合成或 tonemap 限制。
- `Cover palette extracted` 是彩色但封面视觉灰：源图正常，重点看 `Cover render state` 是否仍有 tint/filter/alpha 或是否未走自绘。
- `Status color applied` 变成系统灰色：说明当前取色来源没有使用封面或自定义颜色。

## 相关模块

- `xposed`: SystemUI Hook、HDR Surface 控制、LSPosed 日志。
- `lyric:statusbarlyric`: 状态栏歌词容器、封面/图标渲染、局部 HDR 探针。
- `lyric:view`: 歌词行渲染、HDR 高亮颜色打包。
- `lyric:style`: 样式配置与 HDR 开关持久化。
- `app`: 设置界面库模块。
- `lyricon`: 最终 Android 应用模块。

## 来源与许可

本项目基于 Lyricon 修改，保留原项目的 Apache License 2.0 许可。请在发布自己的仓库时保留原作者和许可证信息。
