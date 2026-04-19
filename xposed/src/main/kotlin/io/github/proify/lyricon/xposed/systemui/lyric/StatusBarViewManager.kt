/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 状态栏视图管理器 (StatusBar View Manager)
 *
 * 该单例负责管理所有 [StatusBarViewController] 的生命周期，并提供统一的线程调度与遍历接口。
 * 使用 [CopyOnWriteArrayList] 确保在主线程遍历与异步生命周期变动时的线程安全。
 *
 * @author Tomakino
 * @since 2026
 */
@Suppress("unused")
object StatusBarViewManager {
    const val TAG = "StatusBarViewManager"

    /** 全局主线程 Looper */
    val MAIN_LOOPER: Looper by lazy { Looper.getMainLooper() }

    /** 全局主线程 Handler */
    val mainHandler by lazy { Handler(MAIN_LOOPER) }

    /** 存储活跃的控制器集合 */
    private val _controllers = CopyOnWriteArrayList<StatusBarViewController>()

    /** 对外暴露的只读控制器列表 */
    val controllers: List<StatusBarViewController> get() = _controllers

    /**
     * 注册并初始化控制器。
     * 如果控制器尚未注册，则将其添加到集合中并触发 [StatusBarViewController.onCreate]。
     *
     * @param controller 需要注册的 [StatusBarViewController] 实例
     */
    fun add(controller: StatusBarViewController) {
        if (_controllers.addIfAbsent(controller)) {
            runOnMainThread {
                controller.onCreate()
                Log.d(TAG, "Successfully added and created controller: $controller")
            }
        }
    }

    /**
     * 移除并销毁控制器。
     * 如果控制器已注册，则从集合中移除并触发 [StatusBarViewController.onDestroy]。
     *
     * @param controller 需要移除的 [StatusBarViewController] 实例
     */
    fun remove(controller: StatusBarViewController) {
        if (_controllers.remove(controller)) {
            runOnMainThread {
                controller.onDestroy()
                Log.d(TAG, "Successfully removed and destroyed controller: $controller")
            }
        }
    }

    /**
     * 执行遍历操作。
     * 注意：此方法在调用者当前线程执行。
     * @param block 针对每个控制器执行的操作块
     */
    inline fun forEach(crossinline block: (StatusBarViewController) -> Unit) {
        val currentList = controllers
        for (controller in currentList) {
            try {
                block(controller)
            } catch (e: Exception) {
                Log.e(TAG, "Error in forEach for $controller: ${e.message}")
            }
        }
    }

    /**
     * 确保在主线程安全地遍历所有控制器。
     * 适用于 UI 状态更新、显示/隐藏等低频操作。
     *
     * @param block 针对每个控制器执行的操作块
     */
    inline fun forEachOnMainThread(crossinline block: (StatusBarViewController) -> Unit) {
        runOnMainThread {
            forEach(block)
        }
    }

    /**
     * 线程调度辅助方法。
     * 如果当前线程已是主线程，则同步立即执行；否则邮寄至消息队列。
     *
     * @param action 需要执行的匿名函数块
     */
    inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Thread.currentThread() === MAIN_LOOPER.thread) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }
}