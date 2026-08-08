/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.subscriber

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import io.github.proify.lyricon.subscriber.ProviderLogo.Companion.TYPE_BITMAP
import io.github.proify.lyricon.subscriber.ProviderLogo.Companion.TYPE_SVG
import kotlinx.serialization.Serializable

/**
 * 提供端图标数据。
 *
 * 支持位图和 SVG 两种格式。该类会跨进程传输，因此只保存原始字节和格式标记。
 *
 * @property data 图标原始字节数据。
 * @property type 图标类型，取值见 [TYPE_BITMAP]、[TYPE_SVG]。
 * @property colorful 是否为彩色图标；中心服务可据此决定是否应用着色。
 */
@Serializable
data class ProviderLogo(
    val data: ByteArray,
    val type: Int,
    val colorful: Boolean = false
) {

    /** 将位图格式的 [data] 解码为 [Bitmap]，非 [TYPE_BITMAP] 类型返回 `null`。 */
    fun toBitmap(): Bitmap? = if (type == TYPE_BITMAP) {
        runCatching {
            BitmapFactory.decodeByteArray(
                data, 0, data.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Config.ARGB_8888
                }
            )
        }.getOrNull()
    } else null

    /** 将 SVG 格式的 [data] 解码为字符串，非 [TYPE_SVG] 类型返回 `null`。 */
    fun toSvg(): String? = if (type == TYPE_SVG) data.toString(Charsets.UTF_8) else null

    companion object {
        /** PNG/Bitmap 字节图标。 */
        const val TYPE_BITMAP: Int = 0

        /** SVG 文本图标。 */
        const val TYPE_SVG: Int = 1

        /** 获取图标类型名称 */
        internal fun typeName(type: Int): String =
            when (type) {
                TYPE_BITMAP -> "Bitmap"
                TYPE_SVG -> "SVG"
                else -> "Unknown"
            }
    }

    override fun toString(): String =
        "ProviderLogo(type=${typeName(type)}, colorful=$colorful, data=${data.size} bytes)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProviderLogo

        if (type != other.type) return false
        if (colorful != other.colorful) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + colorful.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
