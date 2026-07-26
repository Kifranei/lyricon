# Provider Manifest

Provider apps must declare Lyricon metadata under the `<application>` node in `AndroidManifest.xml`
so Lyricon can identify the app as a lyric provider.

## Required Metadata

```xml

<application>
    <meta-data android:name="lyricon_module" android:value="true" />

    <meta-data android:name="lyricon_module_author" android:value="your name" />

    <meta-data android:name="lyricon_module_description" android:value="module description" />
</application>
```

| Field                        | Description                       |
|:-----------------------------|:----------------------------------|
| `lyricon_module`             | Marks the app as a Lyricon module |
| `lyricon_module_author`      | Module author                     |
| `lyricon_module_description` | Module description                |

## Module Tags

Module tags declare the lyric capabilities supported by the plugin. They are used for display only.

```xml

<meta-data android:name="lyricon_module_tags" android:resource="@array/lyricon_module_tags" />
```

Declare the array in `res/values/arrays.xml`:

```xml

<string-array name="lyricon_module_tags">
    <item>$syllable</item>
    <item>$translation</item>
</string-array>
```

Supported tags:

| Code           | Meaning                                |
|:---------------|:---------------------------------------|
| `$syllable`    | Supports word-by-word / dynamic lyrics |
| `$translation` | Supports translated lyrics             |

## Notes

- If `lyricon_module` is missing, Lyricon may not recognize the app as a module.
- Tags only declare capabilities. They do not enable word-by-word lyrics or translations
  automatically.
- Author and description should be user-facing text rather than internal identifiers.
