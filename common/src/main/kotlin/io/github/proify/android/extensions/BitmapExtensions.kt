/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.android.extensions

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.IntRange
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

private const val TAG = "BitmapExt"

/**
 * 将 [Bitmap] 保存到文件。
 */
fun Bitmap.saveTo(
    file: File,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    @IntRange(from = 0, to = 100) quality: Int = 100
): Boolean {
    if (isRecycled) return false

    return try {
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.outputStream().buffered().use { out ->
            this.compress(format, quality, out)
        }
        true
    } catch (e: IOException) {
        Log.e(TAG, "saveTo: Failed to write file", e)
        false
    }
}

/**
 * 将 [Bitmap] 转换为字节数组。
 */
fun Bitmap.toByteArray(
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    @IntRange(from = 0, to = 100) quality: Int = 100
): ByteArray {
    if (isRecycled) return byteArrayOf()
    return ByteArrayOutputStream().use {
        compress(format, quality, it)
        it.toByteArray()
    }
}

/**
 * 从文件安全解码 Bitmap。
 *
 * [preserveColorSpace] 控制是否尽量保留位图原始格式：
 * - true（默认）：不再强制可变，因此解码器可以返回 `RGBA_F16` / 宽色域位图，
 *   媒体封面里的高位深与广色域信息不会被洗成 SDR sRGB；
 * - false：沿用旧行为（`isMutableRequired = true`），解码器会额外拷贝一份 `ARGB_8888`，
 *   仅在调用方确实需要写入像素时使用。
 *
 * 注意两点：
 * 1. 固定用 [ImageDecoder.ALLOCATOR_SOFTWARE]（强制软件位图）。[ImageDecoder.ALLOCATOR_DEFAULT]
 *    在 Android 14+ 上会优先返回 `Bitmap.Config.HARDWARE`，而硬件位图既无法 [Bitmap.getPixel]，
 *    也不能作为 AGSL [android.graphics.RuntimeShader] 的子着色器采样源（会静默失效）；
 * 2. 不再强制 `isMutableRequired = true`。可变要求会额外拷贝一份 `ARGB_8888`，
 *    把宽色域 / 高位深（HDR 封面）信息洗成 SDR。
 */
@SuppressLint("ObsoleteSdkInt")
fun File.toBitmap(
    reqWidth: Int = 0,
    reqHeight: Int = 0,
    preserveColorSpace: Boolean = true
): Bitmap? {
    if (!exists() || !canRead()) return null

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(this)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // 设置采样率或目标尺寸以节省内存
                if (reqWidth > 0 && reqHeight > 0) {
                    decoder.setTargetSize(reqWidth, reqHeight)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // 不强制可变：可变要求会触发一次 ARGB_8888 拷贝，宽色域 / 高位深随之丢失
                decoder.isMutableRequired = !preserveColorSpace
            }
        } else {
            decodeLegacy(this, reqWidth, reqHeight, preserveColorSpace)
        }
    } catch (e: Exception) {
        Log.e(TAG, "toBitmap: Failed", e)
        null
    }
}

private fun decodeLegacy(
    file: File,
    reqWidth: Int,
    reqHeight: Int,
    preserveColorSpace: Boolean
): Bitmap? {
    val options = BitmapFactory.Options()
    if (reqWidth > 0 && reqHeight > 0) {
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
    }
    // 放宽到 RGBA_F16 才能在 Android P 以下保留 16bit / 宽色域封面；需要可变位图时仍然用 ARGB_8888
    options.inPreferredConfig =
        if (preserveColorSpace) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * Drawable 转 Bitmap。
 */
fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        if (bitmap.isRecycled) {
            // 如果内部位图被回收，需要重新创建
        } else {
            return bitmap
        }
    }

    val width = if (intrinsicWidth <= 0) 1 else intrinsicWidth
    val height = if (intrinsicHeight <= 0) 1 else intrinsicHeight

    return createBitmap(width, height, Bitmap.Config.ARGB_8888).applyCanvas {
        setBounds(0, 0, width, height)
        draw(this)
    }
}

/**
 * 位图是否携带超出 SDR sRGB 的信息（高位深 / 宽色域）。
 *
 * 这类封面才值得按原格式拷贝 —— 纯 SDR 8bit 封面升到 `RGBA_F16` 只会白白翻倍内存。
 * 状态栏图标 / 封面的 HDR 提亮（见 `HdrLogoShader`）只能在这种素材上得到真实的高光，
 * 否则就只是一次合成提亮。
 */
fun Bitmap.hasExtendedColorInfo(): Boolean {
    if (isRecycled) return false
    if (config == Bitmap.Config.RGBA_F16) return true
    return hasWideGamutColorSpace()
}

/**
 * 保留原始格式（config + [ColorSpace]）的副本；任意边超过 [maxSize] 时先等比缩小。
 *
 * 与 `copy(Bitmap.Config.ARGB_8888, false)` 的区别：宽色域封面不会被降级成 sRGB 8bit。
 * 封面从通知元数据落到磁盘、再到状态栏绘制的整条链路上，只要有一处硬编码 `ARGB_8888`，
 * 后面的 HDR 提亮就没有可用的广色域素材。
 *
 * @return 副本；位图已回收或拷贝失败时返回 null
 */
fun Bitmap.duplicateOrScale(maxSize: Int): Bitmap? {
    if (isRecycled || maxSize <= 0) return null

    return try {
        val targetConfig = preferredDuplicateConfig()
        val needsScale = width > maxSize || height > maxSize
        if (!needsScale) {
            copy(targetConfig, false)
        } else {
            val factor = maxSize.toFloat() / maxOf(width, height)
            val targetWidth = (width * factor).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * factor).roundToInt().coerceAtLeast(1)
            val target = Bitmap.createBitmap(
                targetWidth,
                targetHeight,
                targetConfig,
                true,
                duplicateColorSpace(targetConfig)
            )
            Canvas(target).drawBitmap(
                this,
                null,
                Rect(0, 0, targetWidth, targetHeight),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            target
        }
    } catch (e: Exception) {
        Log.e(TAG, "duplicateOrScale failed", e)
        null
    }
}

/**
 * 拷贝目标格式。
 *
 * `ARGB_8888` 无法承载非 sRGB 色域（[Bitmap.createBitmap] 会直接抛
 * `IllegalArgumentException`），所以宽色域 / 高位深素材一律升到 `RGBA_F16`。
 */
private fun Bitmap.preferredDuplicateConfig(): Bitmap.Config = when {
    config == Bitmap.Config.RGBA_F16 -> Bitmap.Config.RGBA_F16
    config == Bitmap.Config.HARDWARE -> Bitmap.Config.ARGB_8888
    hasWideGamutColorSpace() -> Bitmap.Config.RGBA_F16
    else -> config ?: Bitmap.Config.ARGB_8888
}

private fun Bitmap.hasWideGamutColorSpace(): Boolean {
    val space = colorSpace ?: return false
    return space != ColorSpace.get(ColorSpace.Named.SRGB)
}

private fun Bitmap.duplicateColorSpace(targetConfig: Bitmap.Config): ColorSpace =
    if (targetConfig == Bitmap.Config.RGBA_F16) {
        colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
    } else {
        // ARGB_8888 只接受 sRGB 色域，传非 sRGB 会抛 IllegalArgumentException
        ColorSpace.get(ColorSpace.Named.SRGB)
    }