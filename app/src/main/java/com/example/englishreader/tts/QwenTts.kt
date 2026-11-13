package com.example.englishreader.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class QwenVoice(
    val id: String,
    val name: String,
    val description: String,
) {
    val label: String
        get() = "$name · $description"
}

object QwenTts {
    val voices = listOf(
        QwenVoice("Serena", "Serena", "温柔女声"),
        QwenVoice("Cherry", "Cherry", "亲切女声"),
        QwenVoice("Ethan", "Ethan", "标准男声"),
        QwenVoice("Chelsie", "Chelsie", "清甜女声"),
    )

    fun voiceLabel(id: String): String = voices.firstOrNull { it.id == id }?.label ?: id

    /**
     * 调用阿里云百炼 Qwen3-TTS（北京地域），返回 wav 音频地址。
     * 单次最多 600 字符，长文本由 [splitText] 分段后依次合成。
     */
    suspend fun synthesize(
        text: String,
        voice: String,
        languageType: String,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", MODEL)
            put(
                "input",
                JSONObject().apply {
                    put("text", text)
                    put("voice", voice)
                    put("language_type", languageType)
                },
            )
        }
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "EnglishReader/1.0")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) throw IOException(errorMessage(body, code))

            val url = JSONObject(body)
                .optJSONObject("output")
                ?.optJSONObject("audio")
                ?.optString("url")
                ?.takeIf { it.isNotBlank() }
            url ?: throw IOException(errorMessage(body, code))
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val message = json?.optString("message").orEmpty()
        val errorCode = json?.optString("code").orEmpty()
        return when {
            message.isNotBlank() && errorCode.isNotBlank() -> "$message（$errorCode）"
            message.isNotBlank() -> message
            else -> "HTTP $code"
        }
    }

    private const val MODEL = "qwen3-tts-flash"
    private const val ENDPOINT =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
}
