/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

object AITranslator {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val MAX_CACHE_SIZE = 1000

    private val dbMutex = Mutex()
    private var dbHelper: DatabaseHelper? = null

    private val songLevelCache: MutableMap<String, List<TranslationItem>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, List<TranslationItem>>(MAX_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TranslationItem>>?): Boolean {
                    return size > MAX_CACHE_SIZE
                }
            }
        )

    private val defaultPrompt = AiTranslationConfigs.USER_PROMPT

    fun init(context: Context) {
        if (dbHelper == null) {
            synchronized(this) {
                if (dbHelper == null) {
                    dbHelper = DatabaseHelper(context.applicationContext)
                }
            }
        }
    }

    suspend fun translateSongSync(song: Song, configs: AiTranslationConfigs): Song {
        if (!configs.isUsable || song.lyrics.isNullOrEmpty()) return song
        return runCatching { translateSong(song, configs) }
            .getOrElse {
                it.printStackTrace()
                song
            }
    }

    private suspend fun translateSong(song: Song, configs: AiTranslationConfigs): Song {
        val originalLines = song.lyrics?.map { it.text?.trim().orEmpty() } ?: return song
        val songContentId = calculateSongId(configs, song, originalLines)

        songLevelCache[songContentId]?.let { return applyTranslation(song, it) }

        val dbItems = getFromDb(songContentId)
        if (dbItems != null) {
            songLevelCache[songContentId] = dbItems
            return applyTranslation(song, dbItems)
        }

        val apiResults = doOpenAiRequest(configs, song, originalLines)
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
                val transText = transItems.firstOrNull { it.index == index }?.trans?.trim()
                if (!transText.isNullOrBlank()
                    && line.translation.isNullOrBlank()
                    && normalizeTextForComparison(transText) != normalizeTextForComparison(line.text)
                ) {
                    line.copy(translation = transText, translationWords = null)
                } else {
                    line
                }
            }
        }
    }

    private fun calculateSongId(configs: AiTranslationConfigs, song: Song, lines: List<String>): String {
        val md = MessageDigest.getInstance("MD5")
        md.update((configs.model ?: "default").toByteArray())
        md.update((configs.targetLanguage ?: "default").toByteArray())
        md.update((song.name ?: "unknown").toByteArray())
        md.update((song.artist ?: "unknown").toByteArray())
        lines.forEach { md.update(it.toByteArray()) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun doOpenAiRequest(
        configs: AiTranslationConfigs,
        song: Song? = null,
        texts: List<String>
    ): List<TranslationItem>? = withContext(Dispatchers.IO) {
        if (configs.apiKey.isNullOrBlank()) return@withContext null

        val payload = texts.mapIndexedNotNull { index, text ->
            val normalized = text.trim()
            if (normalized.isBlank()) null else RequestItem(index = index, text = normalized)
        }
        if (payload.isEmpty()) return@withContext null

        val baseUrl = configs.baseUrl?.removeSuffix("/") ?: "https://api.openai.com/v1"
        val apiUrl = "$baseUrl/chat/completions"
        val requestIndices = payload.map { it.index }.toSet()

        val chatRequest = OpenAiChatRequest(
            model = configs.model.orEmpty(),
            messages = listOf(
                ChatMessage("system", buildSystemPrompt(configs, song)),
                ChatMessage("user", json.encodeToString(payload))
            ),
            responseFormat = ResponseFormat("json_object")
        )

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${configs.apiKey}")
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(json.encodeToString(chatRequest))
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val responseObj = json.decodeFromString<OpenAiChatResponse>(responseBody)
            val content = responseObj.choices.firstOrNull()?.message?.content
                ?.let(AiTranslationConfigs::cleanLlmOutput)
                ?: return@withContext null

            json.decodeFromString<List<TranslationItem>>(content)
                .filter { it.index in requestIndices }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildSystemPrompt(configs: AiTranslationConfigs, song: Song?): String {
        val target = configs.targetLanguage?.takeIf { it.isNotBlank() }
            ?: AiTranslationConfigs.defaultTargetLanguage(Locale.getDefault())
        val title = song?.name ?: "Unknown Track"
        val artist = song?.artist ?: "Unknown Artist"
        val prompt = configs.prompt.takeIf { it.isNotBlank() } ?: defaultPrompt
        return AiTranslationConfigs.getPrompt(target, title, artist, userPrompt = prompt)
    }

    private fun normalizeTextForComparison(text: String?): String {
        return text.orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), "")
            .replace(Regex("[\\p{Punct}，。！？；：“”‘’、·…—～（）《》〈〉【】『』「」]"), "")
    }

    private suspend fun getFromDb(key: String): List<TranslationItem>? = dbMutex.withLock {
        val db = dbHelper?.readableDatabase ?: return@withLock null
        runCatching {
            db.query(
                DatabaseHelper.TABLE_NAME,
                arrayOf(DatabaseHelper.COLUMN_DATA),
                "${DatabaseHelper.COLUMN_ID} = ?",
                arrayOf(key),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val jsonData = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATA))
                    json.decodeFromString<List<TranslationItem>>(jsonData)
                } else {
                    null
                }
            }
        }.getOrNull()
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

    fun clearCache(callback: () -> Unit) {
        songLevelCache.clear()
        scope.launch {
            dbMutex.withLock {
                runCatching {
                    dbHelper?.writableDatabase?.delete(DatabaseHelper.TABLE_NAME, null, null)
                }
                callback()
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

    @Serializable
    private data class OpenAiChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @SerialName("response_format") val responseFormat: ResponseFormat? = null
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ResponseFormat(
        val type: String
    )

    @Serializable
    private data class OpenAiChatResponse(
        val choices: List<Choice>
    )

    @Serializable
    private data class Choice(
        val message: ChatMessage
    )
}
