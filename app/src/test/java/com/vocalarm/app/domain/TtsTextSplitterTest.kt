package com.vocalarm.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextSplitterTest {

    @Test
    fun shortTextStaysInOneChunk() {
        val text = "该起床了。"
        assertEquals(listOf(text), TtsTextSplitter.split(text))
    }

    @Test
    fun emptyTextProducesNoChunks() {
        assertEquals(emptyList<String>(), TtsTextSplitter.split("   "))
    }

    @Test
    fun longChineseTextIsSplitWithinLimit() {
        val sentence = "今天九点有晨会，记得带上电脑和门禁卡。"
        val text = sentence.repeat(400)
        val chunks = TtsTextSplitter.split(text, maxChars = 300)
        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue("chunk too long: ${chunk.length}", chunk.length <= 300)
        }
        val joined = chunks.joinToString("")
        assertEquals(text.length, joined.length)
    }

    @Test
    fun textWithoutPunctuationIsHardSplit() {
        val text = "a".repeat(1_000)
        val chunks = TtsTextSplitter.split(text, maxChars = 200)
        assertEquals(5, chunks.size)
        assertTrue(chunks.all { it.length <= 200 })
    }

    @Test
    fun newlinesActAsSentenceBreaks() {
        val text = "第一行\n第二行\n第三行"
        val chunks = TtsTextSplitter.split(text, maxChars = 80)
        // Short text is returned verbatim; newlines are preserved for natural TTS pauses.
        assertEquals(listOf(text), chunks)
    }

    @Test
    fun tinyLimitIsRaisedToSafetyFloor() {
        val chunks = TtsTextSplitter.split("abc".repeat(100), maxChars = 1)
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { assertTrue(it.length <= 80) }
    }
}
