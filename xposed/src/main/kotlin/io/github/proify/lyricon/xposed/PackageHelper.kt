/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import android.app.Application
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.proify.lyricon.xposed.logger.YLog
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

class PackageHelper(
    private val param: XC_LoadPackage.LoadPackageParam
) {
    val classLoader: ClassLoader get() = param.classLoader
    val packageName: String get() = param.packageName
    val processName: String get() = param.processName

    @Volatile
    var appInstance: Application? = null
        private set

    private val appOnCreateListeners = CopyOnWriteArraySet<(Application) -> Unit>()
    private val isHooked = AtomicBoolean(false)

    /**
     * 在 Application 创建时执行回调
     */
    fun doOnAppCreated(callback: (Application) -> Unit) {
        val current = appInstance
        if (current != null) {
            callback(current)
            return
        }

        appOnCreateListeners.add(callback)

        if (isHooked.compareAndSet(false, true)) {
            findAndHookTargetApplication()
        }
    }

    /**
     * 根据 ApplicationInfo 提供的类名进行定向 Hook
     */
    private fun findAndHookTargetApplication() {
        // 1. 获取清单文件中定义的类名，若未定义则默认为系统的 Application 类
        val targetClassName = param.appInfo.className ?: "android.app.Application"

        try {
            YLog.info("PackageHelper", "Targeting Application class: $targetClassName")

            // 2. 直接对该类进行 Hook
            XposedHelpers.findAndHookMethod(
                targetClassName,
                classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    /**
                     * 当 targetClassName 的 onCreate 执行时，Context 环境已完全就绪
                     */
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val instance = param.thisObject as? Application ?: return
                        handleApplicationInstance(instance)
                    }
                }
            )
        } catch (e: Throwable) {
            // 容错处理：如果指定的类找不到（常见于加壳 App 动态替换 Application），则回退到全局 Hook
            YLog.error("PackageHelper", "Failed to hook $targetClassName, falling back to global Application", e)
            fallbackToGlobalHook()
        }
    }

    /**
     * 兜底方案：Hook 通用的 Application 类
     */
    private fun fallbackToGlobalHook() {
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        handleApplicationInstance(param.thisObject as Application)
                    }
                }
            )
        } catch (t: Throwable) {
            YLog.error("PackageHelper", "Critical failure: Global Hook failed", t)
        }
    }

    private fun handleApplicationInstance(instance: Application) {
        if (appInstance != null) return

        synchronized(this) {
            if (appInstance == null) {
                appInstance = instance

                // 此时 getApplicationContext() 保证是非空的
                YLog.info("PackageHelper", "Application Context is ready: ${instance.javaClass.name}")

                appOnCreateListeners.forEach {
                    runCatching { it(instance) }.onFailure {
                        YLog.error("PackageHelper", "Callback error", it)
                    }
                }
                appOnCreateListeners.clear()
            }
        }
    }
}