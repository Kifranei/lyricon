package io.github.proify.lyricon.app.bridge.history

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class ListeningTrack(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val source: String = "",
    val durationMs: Long = 0,
) {
    val key: String get() = listOf(source, title, artist, album).joinToString("\u0000")
}

data class ListeningSession(
    val id: String,
    val track: ListeningTrack,
    val startedAt: Long,
    val updatedAt: Long,
    val listenedMs: Long = 0,
    val finished: Boolean = false,
    val dailyMs: Map<String, Long> = emptyMap(),
) {
    val qualified: Boolean get() = listenedMs >= qualifyingThreshold(track.durationMs)
}

/**
 * 计入统计的自定义规则（App 进程内、读取时求值，因此修改后已有历史会按新规则重新计算）。
 * 默认值保持原行为：听满半首或 4 分钟（取较短者）。
 */
object ListeningRules {
    const val KEY_RATIO_PERCENT = "qualify_ratio_percent"
    const val KEY_CAP_MINUTES = "qualify_cap_minutes"
    const val DEFAULT_RATIO_PERCENT = 50
    const val DEFAULT_CAP_MINUTES = 4

    @Volatile
    var ratioPercent: Int = DEFAULT_RATIO_PERCENT

    /** 最长分钟数；0 = 不设上限。 */
    @Volatile
    var capMinutes: Int = DEFAULT_CAP_MINUTES

    val isDefault: Boolean
        get() = ratioPercent == DEFAULT_RATIO_PERCENT && capMinutes == DEFAULT_CAP_MINUTES

    fun load(prefs: android.content.SharedPreferences) {
        ratioPercent = prefs.getInt(KEY_RATIO_PERCENT, DEFAULT_RATIO_PERCENT).coerceIn(1, 100)
        capMinutes = prefs.getInt(KEY_CAP_MINUTES, DEFAULT_CAP_MINUTES).coerceIn(0, 120)
    }
}

fun qualifyingThreshold(durationMs: Long): Long {
    val cap = if (ListeningRules.capMinutes > 0) ListeningRules.capMinutes * 60_000L else Long.MAX_VALUE
    return if (durationMs > 0) {
        minOf(durationMs * ListeningRules.ratioPercent / 100, cap).coerceAtLeast(1)
    } else if (cap == Long.MAX_VALUE) 240_000L else cap
}

/** Only monotonic elapsed time while playing is counted; seeks never add listening time. */
class ListeningSessionTracker(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val emit: (ListeningSession) -> Unit,
) {
    private var enabled = false
    private var playing = false
    private var elapsed = 0L
    private var position: Long? = null
    var session: ListeningSession? = null
        private set

    fun enable(value: Boolean, now: Long, monotonic: Long) {
        if (enabled == value) return
        tick(now, monotonic)
        finish(now)
        enabled = value
        elapsed = monotonic
    }

    fun track(track: ListeningTrack?, now: Long, monotonic: Long) {
        tick(now, monotonic)
        if (!enabled) return
        if (track == null || track.title.isBlank()) {
            finish(now)
            return
        }
        val old = session
        if (old != null && old.track.source == track.source && old.track.title == track.title &&
            (old.track.artist == track.artist || old.track.artist.isBlank() || track.artist.isBlank())) {
            session = old.copy(track = track.copy(
                artist = track.artist.ifBlank { old.track.artist },
                album = track.album.ifBlank { old.track.album },
                durationMs = track.durationMs.takeIf { it > 0 } ?: old.track.durationMs,
            ), updatedAt = now)
            return
        }
        finish(now)
        session = ListeningSession(UUID.randomUUID().toString(), track, now, now)
        elapsed = monotonic
        position = null
        emit(session!!)
    }

    fun playback(value: Boolean, now: Long, monotonic: Long) {
        tick(now, monotonic)
        playing = value
        elapsed = monotonic
        session?.let(emit)
    }

    fun position(value: Long, seek: Boolean, now: Long, monotonic: Long) {
        tick(now, monotonic)
        val old = session
        val previous = position
        if (!seek && playing && old != null && previous != null &&
            old.track.durationMs > 10_000 && previous >= old.track.durationMs - 5_000 &&
            value < 5_000 && previous - value > 10_000) {
            val track = old.track
            finish(now)
            session = ListeningSession(UUID.randomUUID().toString(), track, now, now)
            emit(session!!)
        }
        position = value
    }

    fun tick(now: Long, monotonic: Long) {
        val delta = (monotonic - elapsed).coerceAtLeast(0)
        elapsed = monotonic
        val old = session ?: return
        if (!enabled || !playing || delta == 0L) return
        val daily = old.dailyMs.toMutableMap()
        var cursor = now - delta
        while (cursor < now) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val end = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            daily[date.toString()] = (daily[date.toString()] ?: 0) + (end - cursor)
            cursor = end
        }
        session = old.copy(updatedAt = now, listenedMs = old.listenedMs + delta, dailyMs = daily)
    }

    fun flush() { session?.let(emit) }

    private fun finish(now: Long) {
        session?.copy(updatedAt = now, finished = true)?.let(emit)
        session = null
        position = null
    }
}
