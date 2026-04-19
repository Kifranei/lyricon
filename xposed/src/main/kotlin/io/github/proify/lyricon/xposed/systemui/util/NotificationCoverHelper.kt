/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.util.Log
import io.github.proify.android.extensions.saveTo
import io.github.proify.lyricon.xposed.systemui.Directory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object NotificationCoverHelper {

    private const val TAG = "NotificationCoverHelper"
    private const val COVER_FILE_NAME = "cover.png"
    private const val TEMP_FILE_NAME = "cover.png.tmp" // 用于原子写入

    private val listeners = CopyOnWriteArraySet<OnCoverUpdateListener>()
    private val isInitialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mediaControllerManagers = ConcurrentHashMap<String, MediaControllerManager>()

    fun registerListener(listener: OnCoverUpdateListener) = listeners.add(listener)
    fun unregisterListener(listener: OnCoverUpdateListener) = listeners.remove(listener)

    @Synchronized
    private fun getMCM(packageName: String): MediaControllerManager {
        return mediaControllerManagers.getOrPut(packageName) {
            MediaControllerManager(packageName)
        }
    }

    private data class MediaControllerManager(val packageName: String) {
        @Volatile
        private var lastGenerationId: Int = -1
        private val mutex = Mutex()

        fun onMediaChanged(metadata: MediaMetadata) {
            val originalCover = metadata.extractAlbumArt() ?: return

            // 1. 立即检查回收状态和 ID
            if (originalCover.isRecycled) return
            val currentId = originalCover.generationId
            if (lastGenerationId == currentId) return

            /**
             * 克隆 Bitmap
             * 必须在主线程/当前线程立即克隆，因为 metadata 里的 Bitmap 生命周期不可控。
             */
            val coverCopy = try {
                originalCover.copy(originalCover.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bitmap for $packageName", e)
                null
            } ?: return

            scope.launch {
                processCoverSave(coverCopy, currentId)
            }
        }

        private suspend fun processCoverSave(cover: Bitmap, currentId: Int) {
            try {
                mutex.withLock {
                    if (lastGenerationId == currentId) {
                        cover.recycle() // 及时回收副本
                        return@withLock
                    }

                    val success = withContext(Dispatchers.IO) {
                        try {
                            val dir = Directory.getPackageDataDir(packageName)
                            val targetFile = File(dir, COVER_FILE_NAME)
                            val tempFile = File(dir, TEMP_FILE_NAME)

                            /**
                             * 原子写入逻辑
                             * 先写临时文件，写完后 rename。防止读取端读到写了一半的文件。
                             */
                            if (cover.saveTo(tempFile)) {
                                if (tempFile.renameTo(targetFile)) {
                                    true
                                } else {
                                    // 某些系统 rename 失败（如跨分区），尝试覆盖拷贝
                                    tempFile.copyTo(targetFile, overwrite = true)
                                    tempFile.delete()
                                    true
                                }
                            } else false
                        } catch (e: Exception) {
                            Log.e(TAG, "IO error saving cover for $packageName", e)
                            false
                        }
                    }

                    if (success) {
                        lastGenerationId = currentId
                        notifyListeners(packageName, getCoverFile(packageName))
                    }

                    // 无论成功失败，处理完都要释放副本内存
                    if (!cover.isRecycled) cover.recycle()
                }
            } catch (e: CancellationException) {
                if (!cover.isRecycled) cover.recycle()
                throw e
            }
        }
    }

    fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) return
        SystemUIMediaUtils.registerListener(object : SystemUIMediaUtils.MediaControllerCallback {
            override fun onMediaChanged(controller: MediaController, metadata: MediaMetadata) {
                val pkg = controller.packageName ?: return
                getMCM(pkg).onMediaChanged(metadata)
            }

            override fun onSessionDestroyed(controller: MediaController) {
                val pkg = controller.packageName ?: return
                mediaControllerManagers.remove(pkg)
            }
        })
    }

    private fun notifyListeners(packageName: String, coverFile: File) {
        listeners.forEach { it.onCoverUpdated(packageName, coverFile) }
    }

    fun getCoverFile(packageName: String): File =
        File(Directory.getPackageDataDir(packageName), COVER_FILE_NAME)

    private fun MediaMetadata.extractAlbumArt(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    fun interface OnCoverUpdateListener {
        fun onCoverUpdated(packageName: String, coverFile: File)
    }

    fun destroy() {
        scope.cancel()
        mediaControllerManagers.clear()
    }
}