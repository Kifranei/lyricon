package io

import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming

/**
 * 方案 A: 线性查找 (无优化)
 */
class NaiveNavigator<T : ILyricTiming>(val source: Array<T>) {
    fun first(position: Long): T? {
        // O(N) 复杂度，每次都从头遍历
        return source.find { position >= it.begin && position <= it.end }
    }
}

/**
 * 方案 B: 纯二分查找 (无状态缓存)
 */
class BinaryNavigator<T : ILyricTiming>(val source: Array<T>) {
    fun first(position: Long): T? {
        // O(log N) 复杂度，不利用顺序播放的特性
        var low = 0
        var high = source.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val entry = source[mid]
            when {
                position < entry.begin -> high = mid - 1
                position > entry.end -> low = mid + 1
                else -> return entry
            }
        }
        return null
    }
}