package io.github.proify.lyricon.app.history

import java.util.Locale

data class RankedHistory(val label: String, val subtitle: String, val plays: Int, val listenedMs: Long, val entry: HistoryEntry)
data class HistoryStats(
    val received: Int,
    val plays: Int,
    val listenedMs: Long,
    val uniqueTracks: Int,
    val tracks: List<RankedHistory>,
    val artists: List<RankedHistory>,
    val albums: List<RankedHistory>,
    val posters: List<RankedHistory>,
)

internal val POSTER_WALL_HOURS = listOf(12, 24, 48, 72, 0) // 0 = 全部时间

/** The rolling window selects plays started in this interval, within the active history filters. */
fun posterHistoryStats(entries: List<HistoryEntry>, hours: Int, now: Long = System.currentTimeMillis()): HistoryStats {
    require(hours in POSTER_WALL_HOURS)
    // hours == 0：全部时间；海报数量由渲染端限制为最多 96 张
    val since = if (hours == 0) 0L else (now - hours * 3_600_000L).coerceAtLeast(0)
    return historyStats(entries.filter { it.countPlay && it.session.startedAt in since..now })
}

fun historyStats(entries: List<HistoryEntry>): HistoryStats {
    fun key(entry: HistoryEntry) = listOf(entry.session.track.title, entry.session.track.artist, entry.session.track.album)
        .joinToString("\u0000").lowercase(Locale.ROOT)
    fun rank(grouped: Map<String, List<HistoryEntry>>, label: (HistoryEntry) -> String,
             subtitle: (HistoryEntry) -> String): List<RankedHistory> = grouped.values.map { group ->
        val example = group.firstOrNull { it.cover != null } ?: group.first()
        RankedHistory(label(example), subtitle(example), group.count { it.countPlay && it.session.qualified },
            group.sumOf { it.rangeListenedMs }, example)
    }.sortedWith(compareByDescending<RankedHistory> { it.plays }.thenByDescending { it.listenedMs }.thenBy { it.label })
    val tracks = rank(entries.groupBy(::key), { it.session.track.title }, { it.session.track.artist })
    val artists = rank(entries.filter { it.session.track.artist.isNotBlank() }.groupBy { it.session.track.artist.lowercase(Locale.ROOT) },
        { it.session.track.artist }, { "" })
    val albums = rank(entries.filter { it.session.track.album.isNotBlank() }.groupBy { (it.session.track.album + "\u0000" + it.session.track.artist).lowercase(Locale.ROOT) },
        { it.session.track.album }, { it.session.track.artist })
    val qualifiedTracks = tracks.filter { it.plays > 0 }
    return HistoryStats(entries.size, entries.count { it.countPlay && it.session.qualified }, entries.sumOf { it.rangeListenedMs }, tracks.size,
        qualifiedTracks, artists.filter { it.plays > 0 }, albums.filter { it.plays > 0 }, qualifiedTracks)
}
