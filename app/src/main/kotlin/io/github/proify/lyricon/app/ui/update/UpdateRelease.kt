/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.update

import android.os.Build
import io.github.proify.lyricon.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Fork 的发布仓库；检查更新与「前往发布页」都指向这里。 */
const val UPDATE_REPO = "Kifranei/lyricon"
const val UPDATE_RELEASES_URL = "https://github.com/$UPDATE_REPO/releases"

private const val LATEST_RELEASE_API = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"

sealed interface UpdateUiState {
    data object Loading : UpdateUiState
    data class Ready(val release: GithubRelease, val hasUpdate: Boolean) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

data class ReleaseApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class GithubRelease(
    val tagName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val publishedAt: String,
    val assets: List<ReleaseApkAsset> = emptyList(),
    val matchedAsset: ReleaseApkAsset? = null,
) {
    val versionName: String get() = tagName.trim().removePrefix("v").removePrefix("V")
}

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
}

fun fetchLatestRelease(): GithubRelease {
    val request = Request.Builder()
        .url(LATEST_RELEASE_API)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Lyricon/${BuildConfig.VERSION_NAME}")
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}")
        val json = JSONObject(response.body.string())
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val assetList = (0 until assets.length())
            .asSequence()
            .mapNotNull { assets.optJSONObject(it) }
            .mapNotNull { obj ->
                val name = obj.optString("name")
                val url = obj.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    ReleaseApkAsset(name, url, obj.optLong("size", 0L))
                } else {
                    null
                }
            }
            .toList()

        val matchedAsset = matchAssetForDevice(assetList)
        return GithubRelease(
            tagName = json.optString("tag_name").ifBlank { json.optString("name") },
            title = json.optString("name").ifBlank { json.optString("tag_name") },
            body = json.optString("body"),
            htmlUrl = json.optString("html_url").ifBlank { UPDATE_RELEASES_URL },
            downloadUrl = matchedAsset?.downloadUrl ?: assetList.firstOrNull()?.downloadUrl,
            publishedAt = json.optString("published_at").take(10),
            assets = assetList,
            matchedAsset = matchedAsset,
        )
    }
}

/**
 * 按设备 ABI 挑选发布附件。
 *
 * 本项目 release 目前只出 arm64-v8a，但发布资产命名随时可能变，
 * 因此保留完整的 ABI 匹配与 universal / 通用包回退链。
 */
fun matchAssetForDevice(
    assets: List<ReleaseApkAsset>,
    supportedAbis: Array<String> = Build.SUPPORTED_ABIS,
): ReleaseApkAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apks.isEmpty()) return null
    if (apks.size == 1) return apks.first()

    for (abi in supportedAbis) {
        val matched = when (abi.lowercase()) {
            "arm64-v8a" -> apks.firstOrNull {
                val n = it.name.lowercase()
                (n.contains("arm64") || n.contains("aarch64") || n.contains("v8a")) && !n.contains("v7a")
            }

            "armeabi-v7a" -> apks.firstOrNull {
                val n = it.name.lowercase()
                (n.contains("armeabi-v7a") || n.contains("armv7") || n.contains("v7a")) &&
                        !n.contains("arm64") && !n.contains("v8a")
            }

            "armeabi" -> apks.firstOrNull {
                val n = it.name.lowercase()
                n.contains("armeabi") && !n.contains("v7a") && !n.contains("v8a") && !n.contains("arm64")
            }

            "x86_64" -> apks.firstOrNull {
                val n = it.name.lowercase()
                n.contains("x86_64") || n.contains("x64")
            }

            "x86" -> apks.firstOrNull {
                val n = it.name.lowercase()
                n.contains("x86") && !n.contains("x86_64") && !n.contains("x64")
            }

            else -> apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }
        }
        if (matched != null) return matched
    }

    apks.firstOrNull {
        val n = it.name.lowercase()
        n.contains("universal") || n.contains("all") || n.contains("fat")
    }?.let { return it }

    apks.firstOrNull {
        val n = it.name.lowercase()
        !n.contains("arm") && !n.contains("x86") && !n.contains("v7") && !n.contains("v8")
    }?.let { return it }

    return apks.first()
}

fun detectArchLabel(assetName: String): String? {
    val name = assetName.lowercase()
    return when {
        name.contains("arm64") || name.contains("aarch64") || name.contains("v8a") -> "arm64-v8a"
        name.contains("armeabi-v7a") || name.contains("armv7") || name.contains("v7a") -> "armeabi-v7a"
        name.contains("x86_64") || name.contains("x64") -> "x86_64"
        name.contains("x86") -> "x86"
        name.contains("universal") -> "universal"
        else -> null
    }
}

/**
 * 按数字段逐位比较版本号，忽略 `v` 前缀与 `-fork1` / `.rc` 之类的非数字后缀。
 *
 * 注意本项目版本名形如 `1.0.40-rc1`：`rc1` 段以字母开头，takeWhile 取不到数字，
 * 因此实际参与比较的是 `1.0.40`，rc 序号不影响新旧判定。
 */
fun compareVersionNames(left: String, right: String): Int {
    val l = left.versionParts()
    val r = right.versionParts()
    for (i in 0 until maxOf(l.size, r.size)) {
        val result = (l.getOrNull(i) ?: 0).compareTo(r.getOrNull(i) ?: 0)
        if (result != 0) return result
    }
    return 0
}

private fun String.versionParts(): List<Int> =
    trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
