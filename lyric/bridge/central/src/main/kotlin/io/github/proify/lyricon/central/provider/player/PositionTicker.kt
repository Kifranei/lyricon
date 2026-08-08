/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

internal class PositionTicker(
    private val scope: CoroutineScope,
    private val readPosition: () -> Long,
    private val onPosition: (Long) -> Unit
) {

    private val mutex = Mutex()

    @Volatile
    private var job: Job? = null

    fun start(interval: Long) {
        if (job?.isActive == true) return

        scope.launch {
            mutex.withLock {
                if (job?.isActive == true) return@withLock

                job = scope.launch {
                    val safeInterval = interval.coerceAtLeast(MIN_INTERVAL_MS)
                    var nextTick = SystemClock.elapsedRealtime()

                    while (isActive) {
                        onPosition(readPosition())
                        nextTick += safeInterval

                        val remaining = nextTick - SystemClock.elapsedRealtime()
                        if (remaining > 0) {
                            delay(remaining)
                        } else {
                            nextTick = SystemClock.elapsedRealtime()
                            yield()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        val current = job
        current?.cancel()

        if (current != null) {
            scope.launch {
                mutex.withLock {
                    if (job === current) job = null
                }
            }
        }
    }

    fun close() {
        stop()
    }

    private companion object {
        private const val MIN_INTERVAL_MS = 16L
    }
}
