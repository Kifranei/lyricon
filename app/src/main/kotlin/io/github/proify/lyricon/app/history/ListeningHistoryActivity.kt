package io.github.proify.lyricon.app.history

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.BaseActivity
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.bridge.history.HistoryContract
import io.github.proify.lyricon.app.bridge.history.ListeningRules
import io.github.proify.lyricon.app.compose.preference.IntInputPreference
import io.github.proify.lyricon.app.compose.AppToolBarContainer
import io.github.proify.lyricon.app.compose.OpaqueDropdownPopupTheme
import io.github.proify.lyricon.app.compose.custom.miuix.extra.WindowDialog
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.common.PackageNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.ZoomOut
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

class ListeningHistoryActivity : BaseActivity() {
    private val model: HistoryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HistoryScreen(model) }
    }
}

@Composable
private fun HistoryScreen(model: HistoryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(HistoryContract.PREFS, 0) }
    var enabled by rememberBooleanPreference(prefs, HistoryContract.ENABLED, false)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var clearDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var poster by remember { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(poster) {
        val image = poster
        onDispose { image?.recycle() }
    }
    val periodLabels = listOf(stringResource(R.string.history_all_time), stringResource(R.string.history_today),
        stringResource(R.string.history_week), stringResource(R.string.history_month), stringResource(R.string.history_year))
    val periodValues = listOf(0, 1, 7, 30, 365)
    val period = periodLabels[periodValues.indexOf(model.days).coerceAtLeast(0)]
    val posterPeriodLabels = listOf(stringResource(R.string.history_last_12_hours), stringResource(R.string.history_last_1_day),
        stringResource(R.string.history_last_2_days), stringResource(R.string.history_last_3_days),
        stringResource(R.string.history_all_time))
    val posterPeriodIndex = POSTER_WALL_HOURS.indexOf(model.posterHours).coerceAtLeast(0)
    val posterPeriod = if (model.days == 0) posterPeriodLabels[posterPeriodIndex] else "$period · ${posterPeriodLabels[posterPeriodIndex]}"
    val stats = model.wallStats
    val wallTitle = stringResource(R.string.history_wall)
    val playsSummary = stringResource(R.string.history_wall_summary, posterPeriod, stats.plays, stats.listenedMs / 60000)
    val exportTitle = stringResource(R.string.history_share)

    suspend fun renderPoster(): Bitmap {
        var rendered: Bitmap? = null
        try {
            withContext(Dispatchers.IO) {
                rendered = PosterWallRenderer.render(context, stats.posters.take(96), wallTitle, playsSummary)
            }
            return requireNotNull(rendered).also { rendered = null }
        } finally { rendered?.recycle() }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bitmap = renderPoster()
                try { withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { PosterWallRenderer.png(bitmap, it) }
                        ?: error(context.getString(R.string.history_export_error))
                } } finally { bitmap.recycle() }
                Toast.makeText(context, R.string.history_saved, Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(context, R.string.history_export_error, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    LaunchedEffect(tab) {
        if (tab == 2) while (true) {
            model.refresh()
            delay(60_000)
        }
    }
    val posterInputs = stats.posters.take(96).map { it.entry.session.track.key to it.entry.cover }
    LaunchedEffect(tab, model.loading, stats.posters.isEmpty()) {
        if (tab != 2 || model.loading || stats.posters.isEmpty()) fullscreen = false
    }
    LaunchedEffect(tab, model.loading, posterInputs, playsSummary) {
        if (tab == 2 && !model.loading && stats.posters.isNotEmpty()) {
            try { poster = renderPoster() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                poster = null
                Toast.makeText(context, R.string.history_export_error, Toast.LENGTH_LONG).show()
            }
        } else { poster = null }
    }

    AppToolBarContainer(title = stringResource(R.string.history_title), canBack = true, actions = {
        HistoryFilter(model, periodLabels, periodValues)
        IconButton(onClick = { clearDialog = true }, enabled = !busy && model.sources.isNotEmpty(),
            modifier = Modifier.padding(start = 8.dp, end = 12.dp)) {
            Icon(MiuixIcons.Delete, contentDescription = stringResource(R.string.history_clear),
                tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                SwitchPreference(title = stringResource(R.string.history_enable),
                    summary = stringResource(R.string.history_enable_summary), checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        LyriconBridge.with(context).to(PackageNames.SYSTEM_UI).key(HistoryContract.REFRESH).send()
                    })
                IntRuleInput(prefs, ListeningRules.KEY_RATIO_PERCENT, ListeningRules.DEFAULT_RATIO_PERCENT,
                    stringResource(R.string.history_rule_ratio), stringResource(R.string.history_rule_ratio_summary),
                    1..100) { model.refresh() }
                IntRuleInput(prefs, ListeningRules.KEY_CAP_MINUTES, ListeningRules.DEFAULT_CAP_MINUTES,
                    stringResource(R.string.history_rule_cap), stringResource(R.string.history_rule_cap_summary),
                    0..120) { model.refresh() }
            }
            TabRow(
                tabs = listOf(stringResource(R.string.history_stats), stringResource(R.string.history_recent), wallTitle),
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent,
                    selectedBackgroundColor = MiuixTheme.colorScheme.surface)
            )
            Card(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                if (tab == 2) {
                    TabRowWithContour(
                        tabs = posterPeriodLabels,
                        selectedTabIndex = posterPeriodIndex,
                        onTabSelected = { model.filter(posterHours = POSTER_WALL_HOURS[it]) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        minWidth = 110.dp,
                        maxWidth = 160.dp
                    )
                }
                if (model.error != null) Text(model.error.orEmpty(), Modifier.padding(16.dp), color = MiuixTheme.colorScheme.error)
                if (model.loading || model.snapshot.entries.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (model.loading) R.string.history_loading else if (!enabled) R.string.history_disabled_empty else R.string.history_empty),
                            Modifier.padding(32.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                } else when (tab) {
                    0 -> StatsContent(model, Modifier.weight(1f), onEntry = { selected = it })
                    1 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(model.snapshot.entries, key = { it.session.id }) { entry ->
                            HistoryRow(entry.session.track.title, entry.session.track.artist,
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.session.startedAt)) + " · " +
                                    stringResource(if (entry.session.qualified) R.string.history_qualified else R.string.history_not_qualified) + " · " + formatMinutes(entry.session.listenedMs)
                            ) { selected = entry }
                        }
                    }
                    2 -> {
                        if (stats.posters.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.history_wall_empty), Modifier.padding(32.dp),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        } else {
                            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0C0E14))) {
                                PosterPreview(poster, Modifier.fillMaxSize().padding(bottom = 88.dp))
                                Row(Modifier.align(Alignment.BottomCenter).padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    PosterActionButton(MiuixIcons.ZoomOut, stringResource(R.string.history_fullscreen),
                                        enabled = poster != null, onClick = { fullscreen = true })
                                    PosterActionButton(MiuixIcons.Download, stringResource(R.string.history_save),
                                        enabled = !busy && poster != null,
                                        onClick = { save.launch("lyricon-poster-${LocalDate.now()}.png") })
                                    PosterActionButton(MiuixIcons.Share,
                                        stringResource(if (busy) R.string.history_exporting else R.string.history_share),
                                        enabled = !busy && poster != null, onClick = {
                                            scope.launch {
                                                busy = true
                                                try {
                                                    val file = withContext(Dispatchers.IO) {
                                                        val bitmap = renderPoster()
                                                        try {
                                                            val dir = File(context.cacheDir, "listening-posters").apply { mkdirs() }
                                                            // Retain a few prior shares so another app can finish reading its grant.
                                                            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86400000 }?.forEach { it.delete() }
                                                            File(dir, "poster-${System.currentTimeMillis()}.png").also { target -> target.outputStream().use { PosterWallRenderer.png(bitmap, it) } }
                                                        } finally { bitmap.recycle() }
                                                    }
                                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                                        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                                                        clipData = ClipData.newUri(context.contentResolver, exportTitle, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }, exportTitle))
                                                } catch (e: CancellationException) { throw e }
                                                catch (e: Exception) { Toast.makeText(context, R.string.history_export_error, Toast.LENGTH_LONG).show() }
                                                finally { busy = false }
                                            }
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
        WindowDialog(show = clearDialog, onDismissRequest = { clearDialog = false },
            title = stringResource(R.string.history_clear), summary = stringResource(R.string.history_clear_confirm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(android.R.string.cancel), modifier = Modifier.weight(1f),
                    onClick = { clearDialog = false })
                TextButton(text = stringResource(R.string.history_clear), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.error,
                        textColor = MiuixTheme.colorScheme.onError), onClick = {
                        clearDialog = false
                        scope.launch(Dispatchers.IO) {
                            synchronized(ListeningHistoryProvider.LOCK) {
                                prefs.edit().putLong(HistoryContract.GENERATION, prefs.getLong(HistoryContract.GENERATION, 0) + 1).commit()
                                HistoryDatabase.get(context).clear()
                                File(context.filesDir, "listening-covers").listFiles()?.forEach { it.delete() }
                            }
                            context.contentResolver.notifyChange(ListeningHistoryProvider.uri(context.packageName), null)
                            LyriconBridge.with(context).to(PackageNames.SYSTEM_UI).key(HistoryContract.REFRESH).send()
                        }
                    })
            }
        }
        selected?.let { entry ->
            val s = entry.session
            WindowDialog(show = true, onDismissRequest = { selected = null }, title = s.track.title,
                summary = listOf(s.track.artist, s.track.album, packageLabel(context, s.track.source),
                    DateFormat.getDateTimeInstance().format(Date(s.startedAt)), formatMinutes(s.listenedMs),
                    stringResource(if (s.qualified) R.string.history_qualified else R.string.history_not_qualified)).filter { it.isNotBlank() }.joinToString("\n")) {
                TextButton(text = stringResource(android.R.string.ok), onClick = { selected = null },
                    colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.fillMaxWidth())
            }
        }
        if (fullscreen && poster != null) {
            FullscreenPoster(requireNotNull(poster), onDismiss = { fullscreen = false })
        }
    }
}

@Composable private fun HistoryFilter(model: HistoryViewModel, periodLabels: List<String>, periodValues: List<Int>) {
    val context = LocalContext.current
    val selectedPeriod = periodValues.indexOf(model.days).coerceAtLeast(0)
    val playerSources = listOf<String?>(null) + model.sources
    val playerLabels = listOf(stringResource(R.string.history_all_players)) + model.sources.map { packageLabel(context, it) }
    val selectedPlayer = playerSources.indexOf(model.source).coerceAtLeast(0)
    val filterDescription = stringResource(R.string.history_filter_summary, periodLabels[selectedPeriod], playerLabels[selectedPlayer])
    val color = if (selectedPeriod == 0 && model.source == null) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.primary
    val entries = listOf(
        DropdownEntry(items = periodLabels.mapIndexed { index, label ->
            DropdownItem(text = label, selected = index == selectedPeriod,
                onClick = { model.filter(days = periodValues[index]) })
        }),
        DropdownEntry(items = playerLabels.mapIndexed { index, label ->
            DropdownItem(text = label, selected = index == selectedPlayer,
                onClick = { model.filter(source = playerSources[index]) })
        })
    )
    OpaqueDropdownPopupTheme {
        OverlayIconDropdownMenu(entries = entries, collapseOnSelection = false, minHeight = 48.dp,
            maxHeight = 480.dp,
            modifier = Modifier.semantics { contentDescription = filterDescription }) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(periodLabels[selectedPeriod], fontSize = 13.sp, maxLines = 1, color = color)
                Icon(MiuixIcons.Filter, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable private fun StatsContent(model: HistoryViewModel, modifier: Modifier, onEntry: (HistoryEntry) -> Unit) {
    val stats = model.stats
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.history_total_plays, stats.plays), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.history_totals, stats.listenedMs / 60000, stats.uniqueTracks, stats.received), fontSize = 13.sp)
            Text(thresholdText(), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp))
        } } }
        item { DailyChart(model.snapshot.dailyMs) }
        val sections = listOf(R.string.history_top_tracks to stats.tracks, R.string.history_top_artists to stats.artists, R.string.history_top_albums to stats.albums)
        sections.forEach { (label, ranking) ->
            item { Text(stringResource(label), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp)) }
            if (ranking.isEmpty()) item { Text(stringResource(R.string.history_no_qualified), fontSize = 13.sp) }
            items(ranking.take(20)) { row -> HistoryRow(row.label, row.subtitle,
                stringResource(R.string.history_rank_count, row.plays, row.listenedMs / 60000),
                cover = row.entry.cover, showCover = label == R.string.history_top_tracks || label == R.string.history_top_albums) { onEntry(row.entry) } }
        }
    }
}

@Composable private fun HistoryRow(
    title: String, subtitle: String, value: String,
    cover: String? = null, showCover: Boolean = false, click: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), onClick = click) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showCover) {
            HistoryCover(cover)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }
    } }
}

@Composable private fun HistoryCover(cover: String?) {
    val context = LocalContext.current
    val targetSize = with(LocalDensity.current) { 56.dp.roundToPx().coerceAtLeast(1) }
    val file = remember(context.filesDir, cover) { cover?.let { File(context.filesDir, "listening-covers/$it.jpg") } }
    var bitmap by remember(file, targetSize) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file, targetSize) {
        bitmap = withContext(Dispatchers.IO) {
            if (file == null) null else runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSize) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MiuixTheme.colorScheme.surface),
        contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = remember(image) { image.asImageBitmap() }, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(MiuixIcons.Music, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(24.dp))
        }
    }
}

@Composable private fun DailyChart(values: Map<String, Long>) {
    val recent = remember(values) {
        (29 downTo 0).map { LocalDate.now().minusDays(it.toLong()).toString() }.map { it to (values[it] ?: 0L) }
    }
    val color = MiuixTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
        Text(stringResource(R.string.history_daily), fontWeight = FontWeight.Medium)
        Canvas(Modifier.fillMaxWidth().height(90.dp).padding(top = 12.dp)) {
            val max = recent.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            val step = size.width / recent.size
            recent.forEachIndexed { index, (_, ms) ->
                val height = (size.height * ms.toFloat() / max).coerceAtLeast(2f)
                drawRoundRect(color.copy(alpha = if (ms > 0) 1f else .15f), Offset(index * step, size.height - height), Size(step * .68f, height))
            }
        }
        Text("${recent.first().first} — ${recent.last().first}", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    } }
}

@Composable private fun PosterActionButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(52.dp).shadow(4.dp, CircleShape).semantics { if (!enabled) disabled() },
        backgroundColor = MiuixTheme.colorScheme.primary, cornerRadius = 26.dp) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable private fun PosterPreview(bitmap: Bitmap?, modifier: Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    Box(modifier.fillMaxWidth().clipToBounds().background(Color(0xFF0C0E14)), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Canvas(Modifier.fillMaxSize().onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }.pointerInput(bitmap) {
                detectTransformGestures { centroid, delta, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 4f)
                    val relative = centroid - Offset(viewport.width / 2, viewport.height / 2)
                    pan = (pan - relative) * (next / scale) + relative + delta
                    scale = next
                    val fit = minOf(viewport.width / bitmap.width, viewport.height / bitmap.height) * scale
                    val maxX = ((bitmap.width * fit - viewport.width) / 2).coerceAtLeast(0f)
                    val maxY = ((bitmap.height * fit - viewport.height) / 2).coerceAtLeast(0f)
                    pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
                }
            }) {
                val fit = minOf(size.width / bitmap.width, size.height / bitmap.height) * scale
                val maxX = ((bitmap.width * fit - size.width) / 2).coerceAtLeast(0f)
                val maxY = ((bitmap.height * fit - size.height) / 2).coerceAtLeast(0f)
                withTransform({ translate((size.width - bitmap.width * fit) / 2 + pan.x.coerceIn(-maxX, maxX), (size.height - bitmap.height * fit) / 2 + pan.y.coerceIn(-maxY, maxY)); scale(fit, fit, Offset.Zero) }) {
                    drawImage(image)
                }
            }
        }
    }
}

@Composable private fun FullscreenPoster(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        DisposableEffect(window) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PosterPreview(bitmap, Modifier.fillMaxSize())
            IconButton(onClick = onDismiss, backgroundColor = Color.Black.copy(alpha = .65f),
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.displayCutout).padding(16.dp)) {
                Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.history_exit_fullscreen),
                    tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun packageLabel(context: android.content.Context, name: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(name, 0)).toString()
}.getOrDefault(name)

private fun formatMinutes(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

@Composable
private fun thresholdText(): String = when {
    ListeningRules.isDefault -> stringResource(R.string.history_threshold)
    ListeningRules.capMinutes > 0 -> stringResource(R.string.history_threshold_custom,
        ListeningRules.ratioPercent, ListeningRules.capMinutes)
    else -> stringResource(R.string.history_threshold_custom_nocap, ListeningRules.ratioPercent)
}

/** 整数规则输入；写入后立即触发统计重算。 */
@Composable
private fun IntRuleInput(
    prefs: android.content.SharedPreferences, key: String, default: Int,
    title: String, summary: String, range: IntRange, onChanged: () -> Unit,
) {
    var current by remember { mutableIntStateOf(prefs.getInt(key, default)) }
    IntInputPreference(
        preferences = prefs,
        key = key,
        title = title,
        dialogSummary = summary,
        defaultValue = default,
        range = range,
        value = current,
        onValueChange = { v ->
            val value = (v ?: default).coerceIn(range.first, range.last)
            current = value
            prefs.edit().putInt(key, value).apply()
            ListeningRules.load(prefs)
            onChanged()
        },
    )
}
