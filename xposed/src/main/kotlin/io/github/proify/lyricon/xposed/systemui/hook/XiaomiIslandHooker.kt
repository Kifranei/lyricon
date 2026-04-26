/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.proify.android.extensions.getChildAtOrNull
import io.github.proify.lyricon.common.rom.HyperOS.isXiaomiHyperOs3OrAbove
import io.github.proify.lyricon.subscriber.SimpleActivePlayerListener
import io.github.proify.lyricon.xposed.logger.YLog
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 小米超级岛（island_container）运行时控制器：
 * 在系统界面进程中直接追踪目标 View，并在歌词显示时将尺寸压缩到 0，隐藏时恢复原尺寸。
 */
@Deprecated("无效功能，未尝试")
object XiaomiIslandHooker {
    private const val TAG = "XiaomiIslandHooker"
    private const val TARGET_ID_NAME = "island_container"
    private var TARGET_ID = -1
    private var noTargetId = false

    private val activePlayerListener = object : SimpleActivePlayerListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            setHideByLyric(isPlaying)
        }
    }

    private data class ViewState(
        val width: Int,
        val height: Int,
        val visibility: Int,
        val alpha: Float
    )

    private val islandViews = CopyOnWriteArrayList<WeakReference<View>>()
    private val islandViewsLock = Any()
    private val originalStates = Collections.synchronizedMap(WeakHashMap<View, ViewState>())

    private var hideByLyric = false
    private var lastHideByLyric: Boolean? = null
    private val unhooks = mutableListOf<XC_MethodHook.Unhook>()
    private var addViewHooked = false

    fun reset() {
        unhooks.forEach { it.unhook() }
        unhooks.clear()
        addViewHooked = false
        synchronized(islandViewsLock) { islandViews.clear() }
        originalStates.clear()
        hideByLyric = false
        lastHideByLyric = null

        //SystemUIHooker.subscriber?.unsubscribeActivePlayer(activePlayerListener)
    }

    fun initialize(classLoader: ClassLoader) {
        reset()
        if (!isXiaomiHyperOs3OrAbove()) {
            YLog.info(tag = TAG, msg = "Not running on Xiaomi HyperOS 3 or above")
            return
        }
        //SystemUIHooker.subscriber?.subscribeActivePlayer(activePlayerListener)

        setHideByLyric(true)

        unhooks += XposedHelpers.findAndHookMethod(
            classLoader.loadClass(View::class.java.name),
            "onAttachedToWindow",
            TrackIslandAttachHook()
        )
        unhooks += XposedHelpers.findAndHookMethod(
            classLoader.loadClass(View::class.java.name),
            "setVisibility",
            Int::class.javaPrimitiveType,
            TrackIslandVisibilityHook()
        )
        hookViewGroupAddView(classLoader)
        YLog.info(tag = TAG, msg = "Initialized")
    }

    fun setHideByLyric(shouldHide: Boolean) {
        hideByLyric = shouldHide
        if (lastHideByLyric == shouldHide) {
            // 允许在同状态下重复执行，以修复部分 ROM 下视图重建后的残留隐藏状态
            if (!shouldHide) {
                applyStateToTrackedViews(false)
            }
            return
        }
        lastHideByLyric = shouldHide
        applyStateToTrackedViews(shouldHide)
    }

    private fun applyStateToTrackedViews(shouldHide: Boolean) {
        val trackedViews = collectTrackedViewsSnapshot()
        trackedViews.forEach { view ->
            applyState(view, shouldHide)
        }
    }

    private fun collectTrackedViewsSnapshot(): List<View> {
        synchronized(islandViewsLock) {
            val result = ArrayList<View>(islandViews.size)
            val iterator = islandViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next().get()
                if (view == null) {
                    iterator.remove()
                    continue
                }
                result.add(view)
            }
            return result
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun tryTrackIslandView(view: View): Boolean {
        val id = view.id
        if (id == View.NO_ID) return false

        if (noTargetId) return false
        if (TARGET_ID == -1) {
            try {
                TARGET_ID =
                    view.resources.getIdentifier(TARGET_ID_NAME, "id", view.context.packageName)
            } catch (e: Exception) {
                noTargetId = true
                return false
            }
        }

        if (id != TARGET_ID) return false

        var exists = false
        synchronized(islandViewsLock) {
            val iterator = islandViews.iterator()
            while (iterator.hasNext()) {
                val tracked = iterator.next().get()
                if (tracked == null) {
                    iterator.remove()
                } else if (tracked === view) {
                    exists = true
                }
            }
            if (!exists) {
                islandViews.add(WeakReference(view))
            }
        }
        if (!exists) {
            YLog.info(tag = TAG, msg = "Tracked island_container: $view")
        }
        return true
    }

    private fun trackViewTree(root: View) {
        if (tryTrackIslandView(root)) {
            applyState(root, hideByLyric)
        }
        if (root !is ViewGroup) return
        val count = root.childCount
        for (index in 0 until count) {
            val child = root.getChildAtOrNull(index) ?: continue
            trackViewTree(child)
        }
    }

    private fun applyState(view: View, shouldHide: Boolean) {
        val lp = view.layoutParams ?: return

        if (shouldHide) {
            originalStates[view] = originalStates[view] ?: ViewState(
                width = lp.width,
                height = lp.height,
                visibility = view.visibility,
                alpha = view.alpha
            )
            if (lp.width != 0 || lp.height != 0) {
                lp.width = 0
                lp.height = 0
                view.layoutParams = lp
            }
            if (view.alpha != 0f) {
                view.alpha = 0f
            }
            if (view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
            view.requestLayout()
            return
        }

        val originalState = originalStates[view] ?: return
        if (lp.width != originalState.width || lp.height != originalState.height) {
            lp.width = originalState.width
            lp.height = originalState.height
            view.layoutParams = lp
        }
        if (view.alpha != originalState.alpha) {
            view.alpha = originalState.alpha
        }
        if (view.visibility != originalState.visibility) {
            view.visibility = originalState.visibility
        }
        view.requestLayout()
    }

    private class TrackIslandAttachHook : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as? View ?: return
            trackViewTree(view)
        }
    }

    private class TrackIslandVisibilityHook : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as? View ?: return
            if (!tryTrackIslandView(view)) return
            if (!hideByLyric) return
            applyState(view, hideByLyric)
        }
    }

    private fun hookViewGroupAddView(classLoader: ClassLoader) {
        if (addViewHooked) return
        addViewHooked = true
        val viewGroupClass = classLoader.loadClass(ViewGroup::class.java.name)
        unhooks.addAll(
            XposedBridge.hookAllMethods(
                viewGroupClass,
                "addView",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val child = param.args.getOrNull(0) as? View ?: return
                        trackViewTree(child)
                    }
                }
            )
        )
    }
}