package com.example.englishreader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

enum class TranslateDirection { EN_TO_ZH, ZH_TO_EN }

/** 输入里有汉字就按中译英处理，否则按英译中。 */
fun detectTranslateDirection(text: String): TranslateDirection =
    if (text.any { it in '\u4e00'..'\u9fff' }) TranslateDirection.ZH_TO_EN else TranslateDirection.EN_TO_ZH

class DictionaryRepository {
    private val wordCache = HashMap<String, WordInfo>()
    private val sentenceCache = HashMap<String, String>()

    suspend fun lookup(word: String, settings: ReaderSettings): WordInfo = withContext(Dispatchers.IO) {
        val key = cacheKey(word, settings)
        wordCache[key]?.let { return@withContext it }

        val info = if (settings.engine == TranslateEngine.BAIDU && settings.hasBaidu) {
            runCatching { lookupBaidu(word, settings) }.getOrNull()
        } else {
            null
        } ?: if (ApiConfig.hasYoudao) {
            runCatching { lookupYoudao(word) }.getOrNull()
        } else {
            null
        } ?: runCatching { lookupYoudaoWeb(word) }.getOrNull()
            ?: runCatching { lookupFallbackWeb(word) }.getOrElse { WordInfo(word) }

        wordCache[key] = info
        info
    }

    suspend fun translate(
        text: String,
        settings: ReaderSettings,
        direction: TranslateDirection = TranslateDirection.EN_TO_ZH,
    ): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext null
        val key = "${settings.engine}:${settings.baiduAppId}:$direction:${trimmed.hashCode()}"
        sentenceCache[key]?.let { return@withContext it }

        val result = if (settings.engine == TranslateEngine.BAIDU && settings.hasBaidu) {
            runCatching { translateBaidu(trimmed, settings, direction) }.getOrNull()
        } else {
            null
        } ?: if (ApiConfig.hasYoudao) {
            runCatching { translateYoudao(trimmed, direction) }.getOrNull()
        } else {
            null
        } ?: runCatching { translateYoudaoWeb(trimmed, direction) }.getOrNull()
            ?: runCatching { translateMyMemory(trimmed, direction) }.getOrNull()

        result?.let { sentenceCache[key] = it }
        result
    }

    private fun lookupBaidu(word: String, settings: ReaderSettings): WordInfo? {
        val baidu = translateBaidu(word, settings, TranslateDirection.EN_TO_ZH) ?: return null
        val youdao = runCatching { lookupYoudaoWeb(word) }.getOrNull()
        val phonetics = youdao ?: runCatching { fetchPhonetics(word) }.getOrNull()?.let {
            WordInfo(word, it.uk, it.us, it.general)
        }
        return WordInfo(
            word = word,
            phoneticUk = phonetics?.phoneticUk,
            phoneticUs = phonetics?.phoneticUs,
            phoneticGeneral = phonetics?.phoneticGeneral,
            translation = youdao?.translation ?: baidu,
        )
    }

    private fun translateBaidu(
        text: String,
        settings: ReaderSettings,
        direction: TranslateDirection,
    ): String? {
        val salt = System.currentTimeMillis().toString()
        val sign = md5(settings.baiduAppId + text + salt + settings.baiduAppKey)
        val (from, to) = baiduLang(direction)
        val form = listOf(
            "q" to text,
            "from" to from,
            "to" to to,
            "appid" to settings.baiduAppId,
            "salt" to salt,
            "sign" to sign,
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }

        val body = httpPost(BAIDU_API, form) ?: return null
        val json = JSONObject(body)
        if (json.has("error_code")) return null
        return json.optJSONArray("trans_result")
            ?.optJSONObject(0)
            ?.optString("dst")
            ?.takeIf { it.isNotBlank() }
    }

    private fun lookupYoudao(word: String): WordInfo? {
        val json = youdaoRequest(word, TranslateDirection.EN_TO_ZH) ?: return null
        if (json.optString("errorCode") != "0") return null

        val basic = json.optJSONObject("basic")
        val explains = basic?.optJSONArray("explains")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).trim().takeIf { it.isNotEmpty() }
            }
        }
        val translation = explains?.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: json.optJSONArray("translation")?.optString(0)?.takeIf { it.isNotBlank() }
        return WordInfo(
            word = word,
            phoneticUk = basic?.optString("uk-phonetic")?.takeIf { it.isNotBlank() },
            phoneticUs = basic?.optString("us-phonetic")?.takeIf { it.isNotBlank() },
            phoneticGeneral = basic?.optString("phonetic")?.takeIf { it.isNotBlank() },
            translation = translation,
        )
    }

    private fun translateYoudao(text: String, direction: TranslateDirection): String? {
        val json = youdaoRequest(text, direction) ?: return null
        if (json.optString("errorCode") != "0") return null
        return json.optJSONArray("translation")?.optString(0)?.takeIf { it.isNotBlank() }
    }

    /** 有道整句翻译的网页接口，无需 Key，比 MyMemory 质量好很多。 */
    private fun translateYoudaoWeb(text: String, direction: TranslateDirection): String? {
        val (from, to) = youdaoLang(direction)
        val url = "https://aidemo.youdao.com/trans?q=${URLEncoder.encode(text, "UTF-8")}&from=$from&to=$to"
        val body = httpGet(url) ?: return null
        val json = JSONObject(body)
        if (json.optString("errorCode") != "0") return null
        return json.optJSONArray("translation")?.optString(0)?.takeIf { it.isNotBlank() }
    }

    private fun youdaoRequest(q: String, direction: TranslateDirection): JSONObject? {
        val salt = UUID.randomUUID().toString()
        val curtime = (System.currentTimeMillis() / 1000).toString()
        val sign = sha256(ApiConfig.YOUDAO_APP_KEY + truncateQuery(q) + salt + curtime + ApiConfig.YOUDAO_APP_SECRET)
        val (from, to) = youdaoLang(direction)
        val form = listOf(
            "q" to q,
            "from" to from,
            "to" to to,
            "appKey" to ApiConfig.YOUDAO_APP_KEY,
            "salt" to salt,
            "sign" to sign,
            "signType" to "v3",
            "curtime" to curtime,
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }

        val body = httpPost(YOUDAO_API, form) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    /** 有道网页词典的公开接口，无需 Key；结构变化或失败时回退到其他免费接口。 */
    private fun lookupYoudaoWeb(word: String): WordInfo? {
        val url = "https://dict.youdao.com/jsonapi?q=${URLEncoder.encode(word, "UTF-8")}"
        val body = httpGet(url) ?: return null
        val entry = JSONObject(body).optJSONObject("ec")?.optJSONArray("word")?.optJSONObject(0) ?: return null

        val translation = entry.optJSONArray("trs")?.let { trs ->
            (0 until trs.length()).flatMap trsLoop@ { index ->
                val trList = trs.optJSONObject(index)?.optJSONArray("tr")
                    ?: return@trsLoop emptyList()
                (0 until trList.length()).flatMap { trIndex ->
                    val lines = trList.optJSONObject(trIndex)
                        ?.optJSONObject("l")
                        ?.optJSONArray("i")
                        ?: return@flatMap emptyList()
                    (0 until lines.length()).mapNotNull { line ->
                        lines.optString(line).trim().takeIf { it.isNotEmpty() }
                    }
                }
            }
        }?.distinct()?.takeIf { it.isNotEmpty() }?.joinToString("\n")
        val uk = entry.optString("ukphone").takeIf { it.isNotBlank() }
        val us = entry.optString("usphone").takeIf { it.isNotBlank() }
        if (translation == null && uk == null && us == null) return null
        return WordInfo(word = word, phoneticUk = uk, phoneticUs = us, translation = translation)
    }

    private fun lookupFallbackWeb(word: String): WordInfo {
        val phonetics = runCatching { fetchPhonetics(word) }.getOrNull()
        val translation = runCatching { fetchMyMemory(word, TranslateDirection.EN_TO_ZH) }.getOrNull()
        return WordInfo(
            word = word,
            phoneticUk = phonetics?.uk,
            phoneticUs = phonetics?.us,
            phoneticGeneral = phonetics?.general,
            translation = translation,
        )
    }

    private fun translateMyMemory(text: String, direction: TranslateDirection): String? {
        val chunks = splitForMyMemory(text)
        val parts = chunks.mapNotNull { chunk -> runCatching { fetchMyMemory(chunk, direction) }.getOrNull() }
        if (parts.size != chunks.size || parts.isEmpty()) return null
        return parts.joinToString("")
    }

    private fun splitForMyMemory(text: String, limit: Int = 450): List<String> {
        if (text.length <= limit) return listOf(text)
        val chunks = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + limit).coerceAtMost(text.length)
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end)
                if (space > start) end = space
            }
            chunks.add(text.substring(start, end).trim())
            start = end
            while (start < text.length && text[start] == ' ') start++
        }
        return chunks
    }

    private fun fetchPhonetics(word: String): Phonetics? {
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${URLEncoder.encode(word, "UTF-8")}"
        val body = httpGet(url) ?: return null
        val entries = JSONArray(body)
        if (entries.length() == 0) return null
        val entry = entries.getJSONObject(0)
        var general = entry.optString("phonetic").takeIf { it.isNotBlank() }
        var uk: String? = null
        var us: String? = null
        val list = entry.optJSONArray("phonetics")
        if (list != null) {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val text = item.optString("text").takeIf { it.isNotBlank() } ?: continue
                val audio = item.optString("audio").lowercase(Locale.US)
                when {
                    uk == null && ("-uk." in audio || "_uk" in audio) -> uk = text
                    us == null && ("-us." in audio || "_us" in audio) -> us = text
                    else -> if (general == null) general = text
                }
            }
        }
        if (uk == null && us == null && general == null) return null
        return Phonetics(uk, us, general)
    }

    private fun fetchMyMemory(text: String, direction: TranslateDirection): String? {
        val langPair = if (direction == TranslateDirection.EN_TO_ZH) "en%7Czh-CN" else "zh-CN%7Cen"
        val url = "https://api.mymemory.translated.net/get?q=" +
            URLEncoder.encode(text, "UTF-8") + "&langpair=$langPair"
        val body = httpGet(url) ?: return null
        val translated = JSONObject(body)
            .optJSONObject("responseData")
            ?.optString("translatedText")
            ?.replace("&quot;", "\"")
            ?.replace("&#39;", "'")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val lower = translated.lowercase(Locale.US)
        if (lower.contains("mymemory warning") || lower == text.lowercase(Locale.US)) return null
        return translated
    }

    private fun httpGet(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "EnglishReader/1.0")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPost(url: String, form: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("User-Agent", "EnglishReader/1.0")
            connection.outputStream.use { it.write(form.toByteArray()) }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun baiduLang(direction: TranslateDirection): Pair<String, String> =
        if (direction == TranslateDirection.EN_TO_ZH) "en" to "zh" else "zh" to "en"

    private fun youdaoLang(direction: TranslateDirection): Pair<String, String> =
        if (direction == TranslateDirection.EN_TO_ZH) "en" to "zh-CHS" else "zh-CHS" to "en"

    private fun cacheKey(word: String, settings: ReaderSettings): String =
        "${settings.engine}:${settings.baiduAppId}:${settings.baiduAppKey.hashCode()}:${word.lowercase(Locale.US)}"

    private fun truncateQuery(text: String): String =
        if (text.length <= 20) text else text.take(10) + text.length + text.takeLast(10)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private data class Phonetics(val uk: String?, val us: String?, val general: String?)

    private companion object {
        const val TIMEOUT_MS = 8000
        const val YOUDAO_API = "https://openapi.youdao.com/api"
        const val BAIDU_API = "https://fanyi-api.baidu.com/api/trans/vip/translate"
    }
}
