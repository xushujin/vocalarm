package com.vocalarm.app.domain

/**
 * Android TTS engines reject very long utterances. Text is packed into sentence-sized chunks so the
 * alarm still speaks the whole message without truncation.
 */
object TtsTextSplitter {

    private const val DEFAULT_LIMIT = 2800
    private const val SOFT_BREAK_CHARS = "。！？!?；;\n"

    fun split(text: String, maxChars: Int = DEFAULT_LIMIT): List<String> {
        val limit = maxChars.coerceAtLeast(80)
        val cleaned = text.replace("\r\n", "\n").trim()
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length <= limit) return listOf(cleaned)

        val sentences = mutableListOf<String>()
        val buffer = StringBuilder()
        for (char in cleaned) {
            buffer.append(char)
            if (SOFT_BREAK_CHARS.indexOf(char) >= 0) {
                sentences += buffer.toString().trim()
                buffer.clear()
            }
        }
        if (buffer.isNotBlank()) sentences += buffer.toString().trim()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (sentence.isEmpty()) continue
            if (sentence.length > limit) {
                if (current.isNotEmpty()) {
                    chunks += current.toString().trim()
                    current.clear()
                }
                chunks += hardSplit(sentence, limit)
                continue
            }
            if (current.length + sentence.length > limit && current.isNotEmpty()) {
                chunks += current.toString().trim()
                current.clear()
            }
            current.append(sentence)
        }
        if (current.isNotBlank()) chunks += current.toString().trim()
        return chunks.filter { it.isNotBlank() }
    }

    private fun hardSplit(text: String, limit: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val remaining = text.length - start
            if (remaining <= limit) {
                result += text.substring(start).trim()
                break
            }
            var end = start + limit
            val window = text.substring(start, end)
            val softIndex = window.indexOfLast { it == '，' || it == ',' || it == ' ' || it == '、' }
            if (softIndex > limit / 3) {
                end = start + softIndex + 1
            }
            result += text.substring(start, end).trim()
            start = end
        }
        return result.filter { it.isNotBlank() }
    }
}
