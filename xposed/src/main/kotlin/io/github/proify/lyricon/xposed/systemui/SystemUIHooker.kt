/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.android.extensions.deflate
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.common.util.ViewHierarchyParser
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.xposed.systemui.lyric.AiTranslationManager
import io.github.proify.lyricon.xposed.systemui.lyric.LyricPrefs
import io.github.proify.lyricon.xposed.systemui.lyric.LyricViewController
import io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewController
import io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewManager
import io.github.proify.lyricon.xposed.systemui.util.ClockColorMonitor
import io.github.proify.lyricon.xposed.systemui.util.CrashDetector
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.util.StatusBarDisableHooker
import io.github.proify.lyricon.xposed.systemui.util.StatusBarDisableHooker.OnStatusBarDisableListener
import io.github.proify.lyricon.xposed.systemui.util.ViewVisibilityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SystemUI Hook 入口对象
 * 负责状态栏视图注入、第三方逻辑初始化及跨进程通信绑定
 */
object SystemUIHooker : YukiBaseHooker() {

    private const val TEST_CRASH = false
    private var isSafeMode = false
    private var isAppCreated = false
    private var layoutInflaterResult: YukiMemberHookCreator.MemberHookCreator.Result? = null

    private val mainCoroutineScope by lazy {
        CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onHook() {
        onAppLifecycle {
            onCreate {
                if (isAppCreated) return@onCreate
                isAppCreated = true
                onPreAppCreate()
            }
        }
    }

    /**
     * 应用创建前的准备工作，包含崩溃检测逻辑
     */
    private fun onPreAppCreate() {
        YLog.info("onPreAppCreate")
        val context = appContext ?: return

        CrashDetector.getInstance(context).apply {
            record()
            // 检测到多次连续崩溃时进入安全模式，停止后续注入
            if (isContinuousCrash()) {
                isSafeMode = true
                YLog.error("检测到连续崩溃，已停止hook")
            }
            if (isSafeMode) reset()
        }

        initCrashDataChannel()
        if (!isSafeMode) onAppCreate()
    }

    /**
     * 正式初始化 Hook 逻辑
     */
    @SuppressLint("DiscouragedApi")
    private fun onAppCreate() {
        YLog.info("onAppCreate")
        val context = appContext ?: return

        initialize()

        // 获取状态栏布局资源 ID
        val statusBarLayoutId =
            context.resources.getIdentifier("status_bar", "layout", context.packageName)

        // Hook 布局加载器，在状态栏布局填充后注入自定义视图
        layoutInflaterResult = LayoutInflater::class.resolve()
            .firstMethod {
                name = "inflate"; parameters(
                Int::class.java,
                ViewGroup::class.java,
                Boolean::class.java
            )
            }
            .hook {
                after {
                    if (args(0).int() != statusBarLayoutId) return@after
                    result<ViewGroup>()?.let { addStatusBarView(it) }
                }
            }
    }

    /**
     * 各类辅助工具和监控器的初始化
     */
    private fun initialize() {
        YLog.info("onInit")
        val context = appContext ?: return

        ScreenStateMonitor.initialize(context)
        OplusCapsuleHooker.initialize(context.classLoader)
        NotificationCoverHelper.initialize(context.classLoader)
        ViewVisibilityTracker.initialize(context.classLoader)
        initDataChannel()

        // 初始化歌词订阅器
        val subscriber = LyriconFactory.createSubscriber(context)
        subscriber.subscribeActivePlayer(LyricViewController)
        subscriber.addConnectionListener(object : ConnectionListener{
            override fun onConnected(subscriber: LyriconSubscriber) {
                YLog.info("lyriconSubscriber onConnected")
            }

            override fun onReconnected(subscriber: LyriconSubscriber) {
                YLog.info("lyriconSubscriber onReconnected")
            }

            override fun onDisconnected(subscriber: LyriconSubscriber) {
                YLog.info("lyriconSubscriber onDisconnected")
            }

            override fun onConnectTimeout(subscriber: LyriconSubscriber) {
                YLog.info("lyriconSubscriber onConnectTimeout")
            }

        })
        mainCoroutineScope.launch {
            delay(2000)
            subscriber.register()
        }

        // 监听状态栏禁用状态，确保在某些界面（如锁屏、全屏）下正确显示/隐藏
        StatusBarDisableHooker.inject(context.classLoader)
        StatusBarDisableHooker.addListener(object : OnStatusBarDisableListener {
            private var lastDisableStateChanged: Boolean? = null
            override fun onDisableStateChanged(shouldHide: Boolean, animate: Boolean) {
                if (lastDisableStateChanged == shouldHide) return
                lastDisableStateChanged = shouldHide
                StatusBarViewManager.forEach { it.onDisableStateChanged(shouldHide) }
            }
        })

        ClockColorMonitor.hook()
        AiTranslationManager.init(context)
    }

    /**
     * 初始化 DataChannel 通信隧道，处理来自 App 进程的控制请求
     */
    private fun initDataChannel() {
        val channel = dataChannel
        // 样式更新请求
        channel.wait(key = AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE) {
            LyricViewController.updateLyricViewStyle(LyricPrefs.getLyricStyle())
        }
        // 视图高亮请求
        channel.wait<String>(key = AppBridgeConstants.REQUEST_HIGHLIGHT_VIEW) { id ->
            StatusBarViewManager.forEach { it.highlightView(id) }
        }
        // 获取视图树请求（用于调试或布局分析）
        channel.wait<String>(key = AppBridgeConstants.REQUEST_VIEW_TREE) {
            StatusBarViewManager.forEach { controller ->
                val data = json.safeEncode(ViewHierarchyParser.buildNodeTree(controller.statusBarView))
                    .toByteArray(Charsets.UTF_8)
                    .deflate()
                channel.put(AppBridgeConstants.REQUEST_VIEW_TREE_CALLBACK, data)
                return@forEach
            }
        }
        // 清除翻译缓存请求
        channel.wait(key = AppBridgeConstants.REQUEST_CLEAR_TRANSLATION_DB) {
            AiTranslationManager.clearCache()
            LyricViewController.notifyTranslationDbChange()
        }
    }

    /**
     * 将自定义控制器绑定到状态栏视图
     */
    private fun addStatusBarView(view: ViewGroup) {
        view.doOnAttach {
            val controller = StatusBarViewController(view, LyricPrefs.getLyricStyle())
            StatusBarViewManager.add(controller)

            val isFirst = StatusBarViewManager.controllers.size == 1
            if (isFirst) {
                if (TEST_CRASH) view.postDelayed({ error("test crash") }, 3000)
            }
        }
    }

    /**
     * 初始化崩溃相关的通信频道
     */
    private fun initCrashDataChannel() {
        dataChannel.put(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE_CALLBACK, isSafeMode)
        dataChannel.wait(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE) {
            dataChannel.put(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE_CALLBACK, isSafeMode)
        }
    }
}