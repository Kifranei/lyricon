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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 封面图处理助手
 * 负责监听媒体状态变化、提取专辑封面并持久化至本地
 */
object NotificationCoverHelper {

    private const val TAG = "NotificationCoverHelper"
    private const val COVER_FILE_NAME = "cover.png"

    private val listeners = CopyOnWriteArraySet<OnCoverUpdateListener>()
    private val isInitialized = AtomicBoolean(false)

    /** 协程作用域：关联全局生命周期，使用 SupervisorJob 确保子任务异常不影响全局 */
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 正在执行的任务池，Key 为 PackageName */
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /** 互斥锁池：确保同一个 App 的封面文件 IO 不会发生并发冲突 */
    private val fileLocks = ConcurrentHashMap<String, Mutex>()

    /** 内存缓存：记录 controller 对应的上一次封面 generationId，减少重复 IO */
    private val imgCache =
        Collections.synchronizedMap(WeakHashMap<MediaController, Int>())

    /**
     * 注册封面更新监听器
     */
    fun registerListener(listener: OnCoverUpdateListener) {
        listeners.add(listener)
    }

    /**
     * 注销封面更新监听器
     */
    fun unregisterListener(listener: OnCoverUpdateListener) {
        listeners.remove(listener)
    }

    /**
     * 初始化 Hook 监听
     */
    fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) return

        SystemUIMediaHooker.registerListener(object : SystemUIMediaHooker.MediaControllerCallback {
            override fun onMediaChanged(controller: MediaController, metadata: MediaMetadata) {
                val pkg = controller.packageName ?: return

                // 取消该包名下正在进行的旧任务
                activeJobs[pkg]?.cancel()

                activeJobs[pkg] = helperScope.launch {
                    try {
                        processCoverSave(controller, metadata)
                    } finally {
                        activeJobs.remove(pkg)
                    }
                }
            }
        })
    }

    /**
     * 处理封面保存逻辑
     * @param controller 媒体控制器
     * @param metadata 媒体元数据
     */
    private suspend fun processCoverSave(controller: MediaController, metadata: MediaMetadata) {
        val cover = metadata.extractAlbumArt() ?: return
        val packageName = controller.packageName ?: return

        // 验证 Bitmap 有效性
        if (cover.isRecycled) return

        val currentId = cover.generationId
        // 如果当前 Controller 对应的封面 ID 未变，跳过后续逻辑
        if (imgCache[controller] == currentId) {
            Log.d(TAG, "Skipping cover update for $packageName")
            return
        }

        val lock = fileLocks.getOrPut(packageName) { Mutex() }

        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val coverFile = getCoverFile(packageName)

                    // 确保父目录存在
                    val parent = coverFile.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }

                    // 保存至文件系统
                    cover.saveTo(coverFile)

                    // 更新缓存标识
                    imgCache[controller] = currentId

                    // 回到主线程通知
                    withContext(Dispatchers.Main) {
                        notifyListeners(packageName, coverFile)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to save cover for $packageName", e)
                }
            }
        }
    }

    /**
     * 通知所有监听器
     */
    private fun notifyListeners(packageName: String, coverFile: File) {
        listeners.forEach { listener ->
            try {
                listener.onCoverUpdated(packageName, coverFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error in listener: ${listener.javaClass.simpleName}", e)
            }
        }
    }

    /**
     * 获取指定包名的封面文件路径
     */
    fun getCoverFile(packageName: String): File =
        File(Directory.getPackageDataDir(packageName), COVER_FILE_NAME)

    /**
     * 从元数据中提取最佳位图
     */
    private fun MediaMetadata.extractAlbumArt(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    /**
     * 封面更新回调接口
     */
    fun interface OnCoverUpdateListener {
        /**
         * 当封面图片成功更新并保存后调用
         * @param packageName 目标应用包名
         * @param coverFile 保存的图片文件句柄
         */
        fun onCoverUpdated(packageName: String, coverFile: File)
    }
}