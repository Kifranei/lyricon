/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.subscriber

/** 订阅端与中心服务交互使用的内部常量。 */
internal object SubscriberConstants {

    /** 注册订阅者广播动作 */
    internal const val ACTION_REGISTER_SUBSCRIBER: String =
        "io.github.proify.lyricon.lyric.bridge.REGISTER_SUBSCRIBER"
    /** 中心服务启动完成广播动作 */
    internal const val ACTION_CENTRAL_BOOT_COMPLETED: String =
        "io.github.proify.lyricon.lyric.bridge.CENTRAL_BOOT_COMPLETED"

    /** 广播中承载 Binder 的 Bundle key。 */
    internal const val EXTRA_BUNDLE: String = "bundle"

    /** Bundle 中订阅端 Binder 的 key。 */
    internal const val EXTRA_BINDER: String = "binder"

    /** 默认中心服务包名。 */
    const val SYSTEM_UI_PACKAGE_NAME: String = "com.android.systemui"
}
