/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class VisibilityRule(
    val id: String,
    var mode: Int
) : Parcelable {
    companion object {
        const val MODE_NORMAL: Int = 0

//        @Deprecated(
//            "功能变更，仅在播放时更新view配置，所以这两个弃用",
//            level = DeprecationLevel.ERROR
//        )
//        const val MODE_ALWAYS_VISIBLE: Int = 1

//        @Deprecated(
//            "功能变更，仅在播放时更新view配置，所以这两个弃用",
//            level = DeprecationLevel.ERROR
//        )
        // const val MODE_ALWAYS_HIDDEN: Int = 2

        const val MODE_HIDE_WHEN_PLAYING: Int = 3
    }
}