/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.proify.lyricon.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val assetName: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
    ) : UpdateDownloadState

    data class Completed(val apkFile: File, val assetName: String) : UpdateDownloadState

    data class Failed(val error: String, val assetName: String) : UpdateDownloadState
}

object UpdateDownloadManager {
    private const val TAG = "UpdateDownloadManager"
    private const val BUFFER_SIZE = 32 * 1024
    private const val PROGRESS_SAMPLE_INTERVAL_MS = 300L

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun getUpdatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    /** 复用之前下载好的安装包，避免切回页面后重复下载。 */
    fun checkExistingApk(
        context: Context,
        asset: ReleaseApkAsset,
        expectedVersion: String? = null,
    ): File? {
        val target = File(getUpdatesDir(context), asset.name)
        if (isApkValid(context, target, expectedVersion)) {
            _downloadState.value = UpdateDownloadState.Completed(target, asset.name)
            return target
        }
        return null
    }

    /**
     * 校验本地文件确实是本应用的安装包：包名必须一致，给定期望版本时版本名也要一致。
     * 下载中断留下的半截文件解析不出归档信息，会在这里被判为无效。
     */
    fun isApkValid(context: Context, file: File, expectedVersion: String? = null): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } ?: return false

            if (info.packageName != context.packageName) return false
            if (expectedVersion != null) {
                val actual = info.versionName?.trim()?.removePrefix("v")?.removePrefix("V")
                val expected = expectedVersion.trim().removePrefix("v").removePrefix("V")
                if (actual != null && actual != expected) return false
            }
            true
        }.getOrDefault(false)
    }

    fun startDownload(
        context: Context,
        asset: ReleaseApkAsset,
        expectedVersion: String? = null,
    ) {
        if (_downloadState.value is UpdateDownloadState.Downloading) return

        val updatesDir = getUpdatesDir(context)
        val targetFile = File(updatesDir, asset.name)
        if (isApkValid(context, targetFile, expectedVersion)) {
            _downloadState.value = UpdateDownloadState.Completed(targetFile, asset.name)
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val tempFile = File(updatesDir, asset.name + ".downloading")
            try {
                // 清掉旧版本残留，缓存目录只保留本次下载
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name != asset.name && file.name != tempFile.name) {
                        runCatching { file.delete() }
                    }
                }
                if (tempFile.exists()) tempFile.delete()

                _downloadState.value = UpdateDownloadState.Downloading(
                    assetName = asset.name,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = asset.sizeBytes,
                    speedBytesPerSec = 0L,
                )

                val request = Request.Builder()
                    .url(asset.downloadUrl)
                    .header("User-Agent", "Lyricon/" + BuildConfig.VERSION_NAME)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Server returned HTTP " + response.code)
                    val body = response.body
                    val totalBytes =
                        if (asset.sizeBytes > 0) asset.sizeBytes else body.contentLength()

                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    var lastSampleTime = System.currentTimeMillis()
                    var lastSampleBytes = 0L
                    var speed = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastSampleTime
                                if (elapsed < PROGRESS_SAMPLE_INTERVAL_MS) continue

                                val instant = ((downloaded - lastSampleBytes) * 1000L) / elapsed
                                // 指数平滑：瞬时速率抖动很大，直接显示会一直跳
                                speed = if (speed == 0L) instant else (speed * 3 + instant) / 4
                                lastSampleTime = now
                                lastSampleBytes = downloaded

                                _downloadState.value = UpdateDownloadState.Downloading(
                                    assetName = asset.name,
                                    progress = if (totalBytes > 0) {
                                        (downloaded.toFloat() / totalBytes).coerceIn(0f, 0.99f)
                                    } else 0f,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                )
                            }
                            output.flush()
                        }
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                if (!isApkValid(context, targetFile, expectedVersion)) {
                    targetFile.delete()
                    error("Downloaded file is not a valid APK")
                }

                _downloadState.value = UpdateDownloadState.Completed(targetFile, asset.name)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                if (tempFile.exists()) runCatching { tempFile.delete() }
                _downloadState.value = UpdateDownloadState.Failed(
                    error = e.localizedMessage ?: "Download failed",
                    assetName = asset.name,
                )
            }
        }
    }

    fun reset() {
        downloadJob?.cancel()
        _downloadState.value = UpdateDownloadState.Idle
    }

    /**
     * 拉起系统安装器。Android 8 起需要「安装未知应用」授权，
     * 未授权时先跳到授权页并返回，待用户授权后再次点击安装。
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val opened = runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            ("package:" + context.packageName).toUri()
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    true
                }.getOrDefault(false)
                if (opened) return
            }

            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apkFile
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install intent", e)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> bytes.toString() + " B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String = formatBytes(bytesPerSec) + "/s"
}
