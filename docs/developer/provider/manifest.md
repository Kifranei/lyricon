# Provider Manifest 配置

Provider 应用需要在 `AndroidManifest.xml` 的 `<application>` 节点中声明 Lyricon 元数据，用于让
Lyricon 识别该应用是一个歌词提供端。

## 必需配置

```xml
<application>
    <meta-data android:name="lyricon_module" android:value="true" />

    <meta-data android:name="lyricon_module_author" android:value="your name" />

    <meta-data android:name="lyricon_module_description" android:value="module description" />
</application>
```

字段说明：

| 字段                           | 说明                 |
|:-----------------------------|:-------------------|
| `lyricon_module`             | 标记当前应用为 Lyricon 模块 |
| `lyricon_module_author`      | 模块作者名称             |
| `lyricon_module_description` | 模块说明               |

## 模块标签

模块标签用于声明插件支持的歌词能力，仅用于展示。

```xml
<meta-data android:name="lyricon_module_tags" android:resource="@array/lyricon_module_tags" />
```

在 `res/values/arrays.xml` 中声明：

```xml
<string-array name="lyricon_module_tags">
    <item>$syllable</item>
    <item>$translation</item>
</string-array>
```

支持的标签：

| Code           | 含义          |
|:---------------|:------------|
| `$syllable`    | 支持逐字 / 动态歌词 |
| `$translation` | 支持歌词翻译显示    |

## 常见问题

- `lyricon_module` 缺失时，Lyricon 可能不会把该应用识别为模块。
- 标签只表示能力声明，不会自动启用逐字歌词或翻译歌词。
- 作者和描述建议使用面向用户的可读文本，避免只填写包名或内部代号。
