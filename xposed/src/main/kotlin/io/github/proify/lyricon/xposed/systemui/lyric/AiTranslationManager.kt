/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import io.github.proify.android.extensions.json
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.AiTranslationConfigs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * AI 歌词翻译管理器，支持内存与 SQLite 数据库二级缓存
 */
object AiTranslationManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val MAX_CACHE_SIZE = 1000

    private val dbMutex = Mutex()
    private var dbHelper: DatabaseHelper? = null
    private val clientMutex = Mutex()

    @Volatile
    private var cachedClient: Pair<String, OpenAI>? = null

    private val songLevelCache: MutableMap<String, List<TranslationItem>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, List<TranslationItem>>(MAX_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TranslationItem>>?): Boolean {
                    return size > MAX_CACHE_SIZE
                }
            }
        )

    private val DEFAULT_PROMPT = AiTranslationConfigs.USER_PROMPT

    fun init(context: Context) {
        if (dbHelper == null) {
            synchronized(this) {
                if (dbHelper == null) {
                    dbHelper = DatabaseHelper(context.applicationContext)
                }
            }
        }
    }

    fun translateSongIfNeededAsync(
        song: Song,
        settings: AiTranslationConfigs,
        callback: (Song?) -> Unit
    ) {
        if (!settings.isUsable || song.lyrics.isNullOrEmpty()) {
            callback(song)
            return
        }

        scope.launch {
            val result = runCatching { translateSong(song, settings) }
                .getOrElse {
                    it.printStackTrace()
                    song
                }
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }

    private suspend fun translateSong(song: Song, settings: AiTranslationConfigs): Song {
        val currentLyrics = song.lyrics ?: return song
        val originalLines = currentLyrics.map { it.text?.trim() ?: "" }

        val songContentId = calculateSongId(configs = settings, song = song, lines = originalLines)

        val cached = songLevelCache[songContentId]
        if (cached != null) return applyTranslation(song, cached)

        val dbItems = getFromDb(songContentId)
        if (dbItems != null) {
            songLevelCache[songContentId] = dbItems
            return applyTranslation(song, dbItems)
        }

        val apiResults = doOpenAiRequest(settings, song, originalLines)
        if (!apiResults.isNullOrEmpty()) {
            songLevelCache[songContentId] = apiResults
            saveToDb(songContentId, apiResults)
            return applyTranslation(song, apiResults)
        }

        return song
    }

    private fun applyTranslation(song: Song, transItems: List<TranslationItem>): Song {
        return song.apply {
            lyrics = lyrics?.mapIndexed { index, line ->
                val transItem = transItems.find {
                    it.index == index
                }
                val transText = transItem?.trans?.trim()

                if (!transText.isNullOrBlank()
                    && line.translation.isNullOrBlank()
                    && !isEquivalentTranslation(line.text, transText)
                    && !isSameLanguageDuplicate(line.text, transText)
                ) {
                    line.copy(translation = transText, translationWords = null)
                } else line
            }
        }
    }

    private fun calculateSongId(
        configs: AiTranslationConfigs,
        song: Song,
        lines: List<String>
    ): String {
        val md = MessageDigest.getInstance("MD5")
        md.update((configs.model ?: "default").toByteArray())
        md.update((configs.targetLanguage ?: "default").toByteArray())
        md.update((song.name ?: "unknown").toByteArray())
        md.update((song.artist ?: "unknown").toByteArray())
        lines.forEach { md.update(it.toByteArray()) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun getOpenAIClient(configs: AiTranslationConfigs): OpenAI =
        clientMutex.withLock {
            val key = "${configs.apiKey}_${configs.baseUrl}"
            val existing = cachedClient
            if (existing != null && existing.first == key) {
                return@withLock existing.second
            }

            val newClient = OpenAI(
                OpenAIConfig(
                    token = configs.apiKey.orEmpty(),
                    timeout = Timeout(socket = 60.seconds),
                    host = configs.baseUrl?.takeIf { it.isNotBlank() }?.let { OpenAIHost(it) }
                        ?: OpenAIHost.OpenAI
                )
            )
            cachedClient = key to newClient
            return@withLock newClient
        }

    suspend fun doOpenAiRequest(
        configs: AiTranslationConfigs,
        song: Song? = null,
        texts: List<String>
    ): List<TranslationItem>? {
        if (configs.apiKey.isNullOrBlank()) return null

        val targetLanguage = configs.targetLanguage?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: defaultTranslationTargetLanguage()
        val filteredPayload = texts.mapIndexedNotNull { index, text ->
            val normalized = text.trim()
            if (shouldSkipTranslation(normalized, targetLanguage)) null
            else RequestItem(index = index, text = normalized)
        }
        if (filteredPayload.isEmpty()) return null

        val client = getOpenAIClient(configs)
        val requestIndices = filteredPayload.map { it.index }.toSet()

        val request = ChatCompletionRequest(
            model = ModelId(configs.model.orEmpty()),
            messages = listOf(
                ChatMessage(ChatRole.System, buildSystemPrompt(configs, song)),
                ChatMessage(ChatRole.User, json.encodeToString(filteredPayload))
            ),
            responseFormat = ChatResponseFormat.JsonObject
        )

        return try {
            val response = client.chatCompletion(request)
            val content = response.choices.firstOrNull()?.message?.content?.run {
                AiTranslationConfigs.cleanLlmOutput(this)
            } ?: return null
            val result = json.decodeFromString<List<TranslationItem>>(content)

            result.filter { it.index in requestIndices }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildSystemPrompt(configs: AiTranslationConfigs, song: Song?): String {
        val target = configs.targetLanguage?.takeIf { it.isNotBlank() }
            ?: defaultTranslationTargetLanguage()
        val title = song?.name ?: "Unknown Track"
        val artist = song?.artist ?: "Unknown Artist"
        val prompt = (configs.prompt.takeIf { it.isNotBlank() } ?: DEFAULT_PROMPT)

        return AiTranslationConfigs.getPrompt(
            target,
            title,
            artist,
            userPrompt = prompt
        )
    }

    private suspend fun getFromDb(key: String): List<TranslationItem>? = dbMutex.withLock {
        val db = dbHelper?.readableDatabase ?: return null
        return runCatching {
            db.query(
                DatabaseHelper.TABLE_NAME,
                arrayOf(DatabaseHelper.COLUMN_DATA),
                "${DatabaseHelper.COLUMN_ID} = ?",
                arrayOf(key),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val jsonData =
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATA))
                    json.decodeFromString<List<TranslationItem>>(jsonData)
                } else null
            }
        }.getOrNull()
    }

    private fun defaultTranslationTargetLanguage(): String =
        AiTranslationConfigs.defaultTargetLanguage(Locale.getDefault())

    private fun shouldSkipTranslation(source: String, targetLanguage: String): Boolean {
        if (source.isBlank()) return true
        val compact = normalizeTextForComparison(source)
        if (compact.isBlank()) return true
        if (compact.matches(Regex("^[\\d\\p{Punct}\\s]+$"))) return true

        return when {
            isChineseTarget(targetLanguage) && isLikelyChinese(source) -> true
            isJapaneseTarget(targetLanguage) && isLikelyJapanese(source) -> true
            isKoreanTarget(targetLanguage) && isLikelyKorean(source) -> true
            isEnglishTarget(targetLanguage) && isLikelyEnglish(source) -> true
            else -> false
        }
    }

    private fun isEquivalentTranslation(source: String?, translated: String): Boolean {
        val normalizedSource = normalizeTextForComparison(source)
        val normalizedTranslated = normalizeTextForComparison(translated)
        return normalizedSource.isNotBlank() && normalizedSource == normalizedTranslated
    }

    private fun isSameLanguageDuplicate(source: String?, translated: String): Boolean {
        if (source.isNullOrBlank()) return false
        return when {
            isLikelyChinese(source) && isLikelyChinese(translated) ->
                normalizeTextForComparison(source) == normalizeTextForComparison(translated)

            isLikelyJapanese(source) && isLikelyJapanese(translated) ->
                normalizeTextForComparison(source) == normalizeTextForComparison(translated)

            isLikelyKorean(source) && isLikelyKorean(translated) ->
                normalizeTextForComparison(source) == normalizeTextForComparison(translated)

            isLikelyEnglish(source) && isLikelyEnglish(translated) ->
                normalizeTextForComparison(source) == normalizeTextForComparison(translated)

            else -> false
        }
    }

    private fun normalizeTextForComparison(text: String?): String {
        return text
            .orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), "")
            .replace(Regex("[\\p{Punct}，。！？；：“”‘’、·…—～（）《》〈〉【】『』「」]"), "")
    }

    private fun isChineseTarget(targetLanguage: String): Boolean {
        val normalized = targetLanguage.lowercase(Locale.ROOT)
        return normalized.contains("中文")
                || normalized.contains("汉语")
                || normalized.contains("漢語")
                || normalized.contains("chinese")
                || normalized.startsWith("zh-")
                || normalized == "zh"
    }

    private fun isJapaneseTarget(targetLanguage: String): Boolean {
        val normalized = targetLanguage.lowercase(Locale.ROOT)
        return normalized.contains("日本語") || normalized.contains("日语") || normalized.contains("japanese") || normalized.startsWith("ja")
    }

    private fun isKoreanTarget(targetLanguage: String): Boolean {
        val normalized = targetLanguage.lowercase(Locale.ROOT)
        return normalized.contains("한국어") || normalized.contains("韩语") || normalized.contains("korean") || normalized.startsWith("ko")
    }

    private fun isEnglishTarget(targetLanguage: String): Boolean {
        val normalized = targetLanguage.lowercase(Locale.ROOT)
        return normalized.contains("english") || normalized.contains("英语") || normalized.startsWith("en")
    }

    private fun isLikelyChinese(text: String): Boolean =
        Regex("[\\u4E00-\\u9FFF]").containsMatchIn(text)

    private fun isLikelyJapanese(text: String): Boolean =
        Regex("[\\u3040-\\u30FF]").containsMatchIn(text)

    private fun isLikelyKorean(text: String): Boolean =
        Regex("[\\uAC00-\\uD7AF]").containsMatchIn(text)

    private fun isLikelyEnglish(text: String): Boolean {
        val latin = Regex("[A-Za-z]").findAll(text).count()
        return latin >= 3 && latin >= text.count { !it.isWhitespace() } / 2
    }

    private suspend fun saveToDb(key: String, items: List<TranslationItem>) {
        val jsonData = json.encodeToString(items)
        dbMutex.withLock {
            val db = dbHelper?.writableDatabase ?: return@withLock
            runCatching {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COLUMN_ID, key)
                    put(DatabaseHelper.COLUMN_DATA, jsonData)
                    put(DatabaseHelper.COLUMN_TIMESTAMP, System.currentTimeMillis())
                }
                db.insertWithOnConflict(
                    DatabaseHelper.TABLE_NAME,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun clearCache() {
        songLevelCache.clear()
        scope.launch {
            dbMutex.withLock {
                runCatching {
                    dbHelper?.writableDatabase?.delete(DatabaseHelper.TABLE_NAME, null, null)
                }
            }
        }
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        companion object {
            const val DATABASE_NAME = "lyricon_translation.db"
            const val DATABASE_VERSION = 1
            const val TABLE_NAME = "ai_cache"
            const val COLUMN_ID = "song_id"
            const val COLUMN_DATA = "translation_json"
            const val COLUMN_TIMESTAMP = "created_at"
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NAME (
                    $COLUMN_ID TEXT PRIMARY KEY,
                    $COLUMN_DATA TEXT,
                    $COLUMN_TIMESTAMP INTEGER
                )
            """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON $TABLE_NAME($COLUMN_TIMESTAMP)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    @Serializable
    private data class RequestItem(val index: Int, val text: String)

    @Serializable
    data class TranslationItem(val index: Int, val trans: String)
}
