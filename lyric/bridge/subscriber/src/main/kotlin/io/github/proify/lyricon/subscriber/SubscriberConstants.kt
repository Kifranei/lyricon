/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.subscriber

object SubscriberConstants {

    /** 注册订阅者广播动作 */
    internal const val ACTION_REGISTER_SUBSCRIBER: String =
        "io.github.proify.lyricon.lyric.bridge.REGISTER_SUBSCRIBER"
    /** 中心服务启动完成广播动作 */
    internal const val ACTION_CENTRAL_BOOT_COMPLETED: String =
        "io.github.proify.lyricon.lyric.bridge.CENTRAL_BOOT_COMPLETED"

    internal const val EXTRA_BUNDLE: String = "bundle"
    internal const val EXTRA_BINDER: String = "binder"

    const val SYSTEM_UI_PACKAGE_NAME: String = "com.android.systemui"

}