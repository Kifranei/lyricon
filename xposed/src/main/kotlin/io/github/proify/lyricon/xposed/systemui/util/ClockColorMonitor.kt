/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.util

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member
import java.util.WeakHashMap

object ClockColorMonitor {

    private const val INVALID_ID = -1
    private const val CLOCK_ID_NAME = "clock"
    private const val CLOCK_ID_TYPE = "id"

    private val listeners = WeakHashMap<View, OnColorChangeListener>()
    private val luminanceCache = HashMap<Int, Float>()

    @Volatile
    private var hooked = false

    @Volatile
    private var clockId: Int = INVALID_ID

    fun setListener(view: View, listener: OnColorChangeListener?) {
        if (listener == null) {
            listeners.remove(view)
        } else {
            listeners[view] = listener
        }
    }

    fun hook() {
        if (hooked) return
        hooked = true

        val methods = arrayOf<Member>(
            TextView::class.java.getDeclaredMethod(
                "setTextColor",
                ColorStateList::class.java
            ),
            TextView::class.java.getDeclaredMethod(
                "setTextColor",
                Int::class.javaPrimitiveType
            )
        )

        val hook = object : XC_MethodHook() {

            @SuppressLint("DiscouragedApi")
            override fun afterHookedMethod(param: MethodHookParam) {
                val tv = param.thisObject as? TextView ?: return

                if (clockId == INVALID_ID) {
                    clockId = tv.resources.getIdentifier(
                        CLOCK_ID_NAME,
                        CLOCK_ID_TYPE,
                        tv.context.packageName
                    )
                    if (clockId == INVALID_ID) return
                }

                if (tv.id != clockId) return

                val listener = listeners[tv] ?: return

                val color = tv.currentTextColor
                val luminance = luminanceCache.getOrPut(color) {
                    ColorUtils.calculateLuminance(color).toFloat()
                }

                listener.onColorChanged(color, luminance)
            }
        }

        methods.forEach { XposedBridge.hookMethod(it, hook) }
    }
}