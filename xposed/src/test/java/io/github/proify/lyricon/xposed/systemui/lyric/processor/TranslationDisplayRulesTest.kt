package io.github.proify.lyricon.xposed.systemui.lyric.processor

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationDisplayRulesTest {

    private fun line(begin: Long, text: String, secondary: String? = null, translation: String? = null) =
        RichLyricLine(begin = begin, end = begin + 1000, text = text, secondary = secondary, translation = translation)

    @Test
    fun `hide strips structured translation but keeps harmony`() {
        val song = Song(lyrics = listOf(
            line(0, "a", secondary = "ooh", translation = "甲"),
            line(1000, "b", translation = "乙"),
        ))
        val out = TranslationDisplayRules.hideTranslation(song).lyrics!!
        assertNull(out[0].translation)
        assertEquals("ooh", out[0].secondary)
        assertNull(out[1].translation)
    }

    @Test
    fun `bilingual lrc secondary is treated as translation`() {
        val song = Song(lyrics = listOf(
            line(0, "hello", secondary = "你好"),
            line(1000, "world", secondary = "世界"),
            line(2000, "bye"),
        ))
        assertTrue(TranslationDisplayRules.isBilingualSecondary(song))
        val out = TranslationDisplayRules.hideTranslation(song).lyrics!!
        assertTrue(out.all { it.secondary == null })
        assertEquals("hello", out[0].text)

        val only = TranslationDisplayRules.translationOnly(song).lyrics!!
        assertEquals("你好", only[0].text)
        assertEquals("bye", only[2].text)
    }

    @Test
    fun `sparse or word-timed secondary is harmony`() {
        val sparse = Song(lyrics = listOf(
            line(0, "a", secondary = "ooh"), line(1000, "b"), line(2000, "c"),
        ))
        assertFalse(TranslationDisplayRules.isBilingualSecondary(sparse))

        val timed = Song(lyrics = listOf(
            line(0, "a").copy(secondary = "ooh", secondaryWords = listOf(LyricWord(text = "ooh", begin = 0, end = 500))),
            line(1000, "b", secondary = "aah"),
        ))
        assertFalse(TranslationDisplayRules.isBilingualSecondary(timed))
        assertEquals("aah", TranslationDisplayRules.hideTranslation(timed).lyrics!![1].secondary)
    }

    @Test
    fun `text mode second line follows switches`() {
        val text = "原文\n译文"
        assertEquals("原文", TranslationDisplayRules.applyToText(text, hideTranslation = true, translationOnly = false))
        assertEquals("译文", TranslationDisplayRules.applyToText(text, hideTranslation = false, translationOnly = true))
        assertEquals("原文", TranslationDisplayRules.applyToText(text, hideTranslation = true, translationOnly = true))
        assertEquals(text, TranslationDisplayRules.applyToText(text, hideTranslation = false, translationOnly = false))
        assertEquals("single", TranslationDisplayRules.applyToText("single", hideTranslation = true, translationOnly = false))
    }
}
