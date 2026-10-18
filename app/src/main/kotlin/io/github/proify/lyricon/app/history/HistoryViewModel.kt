package io.github.proify.lyricon.app.history

import io.github.proify.lyricon.app.bridge.history.ListeningRules
import io.github.proify.lyricon.app.bridge.history.HistoryContract
import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private data class HistoryLoadResult(
    val snapshot: HistorySnapshot,
    val stats: HistoryStats,
    val wallStats: HistoryStats,
    val sources: List<String>,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    var snapshot by mutableStateOf(HistorySnapshot(emptyList(), emptyMap()))
        private set
    var stats by mutableStateOf(historyStats(emptyList()))
        private set
    var wallStats by mutableStateOf(historyStats(emptyList()))
        private set
    var sources by mutableStateOf<List<String>>(emptyList())
        private set
    var days by mutableIntStateOf(0)
        private set
    var posterHours by mutableIntStateOf(24)
        private set
    var source by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    init {
        application.contentResolver.registerContentObserver(ListeningHistoryProvider.uri(application.packageName), false, observer)
        viewModelScope.launch {
            for (ignored in updates) {
                val selectedDays = days
                val selectedSource = source
                val selectedPosterHours = posterHours
                try {
                    val result = withContext(Dispatchers.IO) {
                        ListeningRules.load(application.getSharedPreferences(HistoryContract.PREFS, 0))
                        val db = HistoryDatabase.get(application)
                        val since = if (selectedDays == 0) 0 else LocalDate.now().minusDays((selectedDays - 1).toLong())
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val data = db.snapshot(since, selectedSource)
                        HistoryLoadResult(data, historyStats(data.entries),
                            posterHistoryStats(data.entries, selectedPosterHours), db.sources())
                    }
                    if (days != selectedDays || source != selectedSource || posterHours != selectedPosterHours) continue
                    snapshot = result.snapshot; stats = result.stats; wallStats = result.wallStats
                    sources = result.sources; error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) { error = e.localizedMessage }
                loading = false
                delay(250)
            }
        }
        refresh()
    }
    fun filter(days: Int = this.days, source: String? = this.source, posterHours: Int = this.posterHours) {
        require(posterHours in POSTER_WALL_HOURS)
        if (this.days == days && this.source == source && this.posterHours == posterHours) return
        loading = true
        this.days = days; this.source = source; this.posterHours = posterHours; refresh()
    }
    fun refresh() { updates.trySend(Unit) }
    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        updates.close()
    }
}
