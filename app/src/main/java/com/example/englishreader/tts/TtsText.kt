package com.example.englishreader.tts

import kotlin.math.max

/** 朗读文本分段：优先在句末标点切分，超长句再退回空格。 */
object TtsText {
    private const val SentenceMarks = "。！？；.!?;\n"

    fun split(text: String, limit: Int): List<String> {
        val content = text.trim()
        if (content.isEmpty()) return emptyList()
        if (content.length <= limit) return listOf(content)

        val floor = max(limit / 2, 1)
        val chunks = ArrayList<String>()
        var start = 0
        while (start < content.length) {
            var end = (start + limit).coerceAtMost(content.length)
            if (end < content.length) {
                var cut = -1
                for (i in end downTo start + floor) {
                    if (content[i - 1] in SentenceMarks) {
                        cut = i
                        break
                    }
                }
                if (cut <= start) {
                    val space = content.lastIndexOf(' ', end)
                    cut = if (space > start) space else end
                }
                end = cut
            }
            val chunk = content.substring(start, end).trim()
            if (chunk.isNotEmpty()) chunks.add(chunk)
            start = end
            while (start < content.length && content[start].isWhitespace()) start++
        }
        return chunks
    }
}
