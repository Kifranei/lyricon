/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.logger

import android.util.Log
import de.robv.android.xposed.XposedBridge

object YLog {
    val processName: String? = null
    const val TAG = "Lyricon"

    private val logger = XposedLogger()

    fun info(tag: String, msg: String) {
        logger.i(tag, msg)
    }

    fun debug(tag: String, msg: String) {
        logger.d(tag, msg)
    }

    fun error(tag: String, msg: String) {
        logger.e(tag, msg)
    }

    fun verbose(tag: String, msg: String) {
        logger.v(tag, msg)
    }

    fun warning(tag: String, msg: String) {
        logger.w(tag, msg)
    }

    fun error(tag: String, msg: String?, e: Throwable?) {
        logger.e(tag, msg, e)
    }

    private class XposedLogger : ILogger {
        override fun v(tag: String?, message: String?) {
            XposedBridge.log(buildMessage(tag, "V", message))
        }

        override fun d(tag: String?, message: String?) {
            XposedBridge.log(buildMessage(tag, "D", message))
        }

        override fun w(tag: String?, message: String?) {
            XposedBridge.log(buildMessage(tag, "W", message))
        }

        override fun i(tag: String?, message: String?) {
            XposedBridge.log(buildMessage(tag, "I", message))
        }

        override fun e(tag: String?, message: String?, throwable: Throwable?) {
            if (throwable != null) {
                XposedBridge.log(
                    buildMessage(
                        tag,
                        "E",
                        if (message != null) {
                            "$message: ${Log.getStackTraceString(throwable)}"
                        } else {
                            Log.getStackTraceString(throwable)
                        }
                    )
                )
            } else XposedBridge.log(buildMessage(tag, "E", message))
        }

        private fun buildMessage(
            tag: String?,
            level: String,
            message: String?
        ): String {
            val tags = listOf(tag, level)
            val tagString =
                tags.filterNotNull().joinToString(prefix = "[", postfix = "]", separator = ",")

            val logs = listOf(tagString, message)
            return logs.filterNotNull().joinToString(separator = " ")
        }
    }
}