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

/**
 * AI 歌词翻译管理器
 * * 职责：负责歌词的 AI 翻译，具备内存与 SQLite 双级缓存。
 * 技术栈：Kotlin Coroutines, kotlinx.serialization, Java HttpURLConnection。
 */
object AITranslator {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val MAX_CACHE_SIZE = 1000

    private val dbMutex = Mutex()
    private var dbHelper: DatabaseHelper? = null

    /**
     * 内存 LruCache 容器，存储已翻译的行数据
     */
    private val songLevelCache: MutableMap<String, List<TranslationItem>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, List<TranslationItem>>(MAX_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TranslationItem>>?): Boolean {
                    return size > MAX_CACHE_SIZE
                }
            }
        )

    private val DEFAULT_PROMPT = AiTranslationConfigs.USER_PROMPT

    /**
     * 初始化数据库
     */
    fun init(context: Context) {
        if (dbHelper == null) {
            synchronized(this) {
                if (dbHelper == null) {
                    dbHelper = DatabaseHelper(context.applicationContext)
                }
            }
        }
    }

    /**
     * 同步翻译整首歌（挂起函数）
     */
    suspend fun translateSongSync(
        song: Song,
        configs: AiTranslationConfigs,
    ): Song {
        if (!configs.isUsable || song.lyrics.isNullOrEmpty()) {
            return song
        }

        return runCatching { translateSong(song, configs) }
            .getOrElse {
                it.printStackTrace()
                song
            }
    }

    /**
     * 核心翻译逻辑：查找缓存 -> 发起请求 -> 保存缓存
     */
    private suspend fun translateSong(song: Song, configs: AiTranslationConfigs): Song {
        val currentLyrics = song.lyrics ?: return song
        val originalLines = currentLyrics.map { it.text?.trim() ?: "" }

        // 计算唯一标识，包含配置、歌曲信息及歌词原文，防止配置变更导致错误的缓存命中
        val songContentId = calculateSongId(configs = configs, song = song, lines = originalLines)

        // 1. 内存查找
        val cached = songLevelCache[songContentId]
        if (cached != null) return applyTranslation(song, cached)

        // 2. 数据库查找
        val dbItems = getFromDb(songContentId)
        if (dbItems != null) {
            songLevelCache[songContentId] = dbItems
            return applyTranslation(song, dbItems)
        }

        // 3. 网络请求
        val apiResults = doOpenAiRequest(configs, song, originalLines)
        if (!apiResults.isNullOrEmpty()) {
            songLevelCache[songContentId] = apiResults
            saveToDb(songContentId, apiResults)
            return applyTranslation(song, apiResults)
        }

        return song
    }

    /**
     * 将翻译结果应用回 Song 对象
     */
    private fun applyTranslation(song: Song, transItems: List<TranslationItem>): Song {
        return song.apply {
            lyrics = lyrics?.mapIndexed { index, line ->
                val transItem = transItems.find { it.index == index }
                val transText = transItem?.trans?.trim()

                if (!transText.isNullOrBlank()
                    && line.translation.isNullOrBlank()
                    && transText.lowercase() != line.text?.trim()?.lowercase()
                ) {
                    line.copy(translation = transText, translationWords = null)
                } else line
            }
        }
    }

    /**
     * 生成基于原文和配置的 MD5 ID
     */
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

    /**
     * 使用 Java HttpURLConnection 发起 API 请求
     */
    suspend fun doOpenAiRequest(
        configs: AiTranslationConfigs,
        song: Song? = null,
        texts: List<String>
    ): List<TranslationItem>? = withContext(Dispatchers.IO) {
        if (configs.apiKey.isNullOrBlank()) return@withContext null

        val baseUrl = configs.baseUrl
            ?.removeSuffix("/")
            ?: "https://api.openai.com/v1"
        val apiUrl =
            if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

        val payload = texts.mapIndexed { index, s -> RequestItem(index = index, text = s) }
        val requestIndices = texts.indices.toSet()

        // 构造 OpenAI 协议标准的请求体
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
            val url = URL(apiUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${configs.apiKey}")
            }

            // 发送数据
            OutputStreamWriter(connection.outputStream).use {
                it.write(
                    json.encodeToString(
                        chatRequest
                    )
                )
            }

            // 处理响应
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val responseObj = json.decodeFromString<OpenAiChatResponse>(responseBody)

                val content = responseObj.choices.firstOrNull()?.message?.content?.run {
                    AiTranslationConfigs.cleanLlmOutput(this)
                } ?: return@withContext null

                val result = json.decodeFromString<List<TranslationItem>>(content)
                result.filter { it.index in requestIndices }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildSystemPrompt(configs: AiTranslationConfigs, song: Song?): String {
        val target = configs.targetLanguage?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().displayLanguage
        val title = song?.name ?: "Unknown Track"
        val artist = song?.artist ?: "Unknown Artist"
        val prompt = (configs.prompt.takeIf { it.isNotBlank() } ?: DEFAULT_PROMPT)

        return AiTranslationConfigs.getPrompt(
            target,
            title,
            artist,
            prompt
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
                    callback()
                }
            }
        }
    }

    /**
     * 内部 SQLite 辅助类
     */
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

    // --- 数据模型定义 (Internal DTOs) ---

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