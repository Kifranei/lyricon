/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import io.github.proify.lyricon.provider.ProviderInfo
import java.nio.ByteBuffer

internal class PositionMemoryBridge(
    private val providerInfo: ProviderInfo
) {

    private var readBuffer: ByteBuffer? = null

    var sharedMemory: SharedMemory? = null
        private set

    init {
        initialize()
    }

    fun readPosition(): Long = try {
        readBuffer?.getLong(POSITION_OFFSET)?.coerceAtLeast(0L) ?: 0L
    } catch (_: Throwable) {
        0L
    }

    fun close() {
        readBuffer?.let { runCatching { SharedMemory.unmap(it) } }
        sharedMemory?.close()
        readBuffer = null
        sharedMemory = null
    }

    private fun initialize() {
        try {
            val hashHex = Integer.toHexString(
                "${providerInfo.providerPackageName}/${providerInfo.playerPackageName}/${providerInfo.processName}".hashCode()
            )
            sharedMemory = SharedMemory.create("lyricon_pos_$hashHex", Long.SIZE_BYTES).apply {
                setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
                readBuffer = mapReadOnly()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "SharedMemory init failed", t)
        }
    }

    private companion object {
        private const val TAG = "PositionMemoryBridge"
        private const val POSITION_OFFSET = 0
    }
}
