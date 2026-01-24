/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MayBeConstant")

package io.github.proify.lyricon.xposed.hook.systemui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.android.extensions.deflate
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.common.util.ViewHierarchyParser
import io.github.proify.lyricon.xposed.util.CrashDetector
import io.github.proify.lyricon.xposed.util.LyricPrefs
import io.github.proify.lyricon.xposed.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.util.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.util.ViewVisibilityTracker

object SystemUIHooker : YukiBaseHooker() {
    private val testCrash = false
    private var layoutInflaterResult: YukiMemberHookCreator.MemberHookCreator.Result? = null

    @SuppressLint("StaticFieldLeak")
    private var statusBarViewManager: StatusBarViewManager? = null

    private var safeMode = false

    override fun onHook() {
        onAppLifecycle {
            onCreate {
                onPreAppCreate()
            }
        }
    }

    private fun onPreAppCreate() {
        YLog.info("onPreAppCreate")
        val context = appContext ?: return

        val detector = CrashDetector.getInstance(context)
        detector.record()
        if (detector.isContinuousCrash()) {
            safeMode = true
            YLog.error("检测到连续崩溃，已停止hook")
            try {
                handleCrashMode()
            } catch (it: Throwable) {
                YLog.error(it)
            }
        }

        if (safeMode) {
            detector.reset()
            return
        }

        onAppCreate()
    }

    private fun onAppCreate() {
        initialize()
        YLog.info("onAppCreate")

        layoutInflaterResult = LayoutInflater::class.resolve()
            .firstMethod {
                name = "inflate"
                parameters(Int::class.java, ViewGroup::class.java, Boolean::class.java)
            }.hook {
                after {
                    val id = args(0).int()
                    if (id == Constants.statusBarLayoutId) {
                        val result = result<ViewGroup>() ?: return@after
                        setupStatusBarView(result)
                        layoutInflaterResult?.remove()
                        layoutInflaterResult = null
                    }
                }
            }
    }

    private fun initialize() {
        YLog.info("onInit")
        val context = appContext ?: return
        ScreenStateMonitor.initialize(context)
        OplusCapsuleHooker.initialize(context.classLoader)
        BridgeCentral.initialize(context)
        Constants.initResourceIds(context)
        NotificationCoverHelper.initialize(context.classLoader)
        ViewVisibilityTracker.initialize(context.classLoader)
        initDataChannel()
        ActivePlayerDispatcher.addActivePlayerListener(LyricViewController)
    }

    private fun initDataChannel() {
        dataChannel.wait(key = AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE) {
            val style = LyricPrefs.getLyricStyle()
            statusBarViewManager?.updateLyricStyle(style)
        }
        dataChannel.wait<String>(key = AppBridgeConstants.REQUEST_HIGHLIGHT_VIEW) { id ->
            statusBarViewManager?.highlightView(id)
        }
        dataChannel.wait<String>(key = AppBridgeConstants.REQUEST_VIEW_TREE) { _ ->
            statusBarViewManager?.let {
                val node = ViewHierarchyParser.buildNodeTree(it.statusBarView)
                val data = json.safeEncode(node)
                    .toByteArray(Charsets.UTF_8)
                    .deflate()

                dataChannel.put(
                    AppBridgeConstants.REQUEST_VIEW_TREE_CALLBACK,
                    data
                )
            }
        }
    }

    private fun setupStatusBarView(view: ViewGroup) {
        YLog.info("setupStatusBarView $view")
        val statusBarViewManager = StatusBarViewManager(
            view,
            LyricPrefs.getLyricStyle()
        )
        this.statusBarViewManager = statusBarViewManager

        LyricViewController.statusBarViewManager = statusBarViewManager
        ScreenStateMonitor.addListener(statusBarViewManager)

        BridgeCentral.sendBootCompleted()

        if (testCrash) view.postDelayed({ error("test crash") }, 3000)
    }

    private fun handleCrashMode() {
        dataChannel.put(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE_CALLBACK, safeMode)
        dataChannel.wait(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE) {
            dataChannel.put(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE_CALLBACK, safeMode)
        }
    }
}