/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.util

import android.annotation.SuppressLint
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArrayList

/**
 * colorOS监听流体云显示
 */
object OplusCapsuleHooker {
    private val listeners = CopyOnWriteArrayList<CapsuleStateChangeListener>()

    var isShowing: Boolean = false
        private set

    private var lastIsShowing: Boolean? = null
    private var unhook: XC_MethodHook.Unhook? = null

    fun initialize(classLoader: ClassLoader) {
        unhook?.unhook()
        if (!isSupport(classLoader)) return

        unhook = XposedHelpers.findAndHookMethod(
            classLoader.loadClass(
                View::class.java.getName()
            ),
            "setVisibility",
            Int::class.javaPrimitiveType, SetVisibilityMethodHook()
        )
    }

    fun addListener(listener: CapsuleStateChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CapsuleStateChangeListener) {
        listeners.remove(listener)
    }

    fun triggerEvent() {
        if (lastIsShowing != null && lastIsShowing == isShowing) return

        lastIsShowing = isShowing
        listeners.forEach { it.onCapsuleVisibilityChanged(isShowing) }
    }

    private class SetVisibilityMethodHook : XC_MethodHook() {
        private var capsuleViewVisible = false
        private var capsuleContainerVisible = false

        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as View
            val visibility = param.args[0] as Int
            val name = view.javaClass.getSimpleName()

            if ("CapsuleContainer" == name) {
                capsuleContainerVisible = visibility == View.VISIBLE
            } else if ("CapsuleView" == name) {
                capsuleViewVisible = visibility == View.VISIBLE
            }

            if ("CapsuleContainer" == name || "CapsuleView" == name) {
                isShowing = capsuleContainerVisible && capsuleViewVisible
                triggerEvent()
            }
        }
    }

    @SuppressLint("PrivateApi")
    fun isSupport(classLoader: ClassLoader): Boolean {
        return try {
            classLoader.loadClass("com.android.systemui.plugins.statusbar.CapsulePlugin") != null
        } catch (_: Exception) {
            false
        }
    }

    interface CapsuleStateChangeListener {
        fun onCapsuleVisibilityChanged(isShowing: Boolean)
    }
}