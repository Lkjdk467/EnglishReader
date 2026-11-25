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

    /**
     * 查词只走词典接口。整句翻译接口不参与单词释义，
     * 否则 "had" 会被 MyMemory 译成「对话中。」这类无关内容。
     */
    suspend fun lookup(word: String): WordInfo = withContext(Dispatchers.IO) {
        val cleaned = cleanQuery(word)
        if (cleaned.isEmpty()) return@withContext WordInfo(cleaned)
        val query = queryForm(cleaned)
        wordCache[query]?.let { return@withContext it.copy(word = cleaned) }

        val info = buildWordInfo(query)?.copy(word = cleaned) ?: WordInfo(word = cleaned)
        wordCache[query] = info
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
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val entry = json.optJSONObject("ec")?.optJSONArray("word")?.optJSONObject(0) ?: return null

        val translation = sanitizeTranslation(youdaoLines(entry).joinToString("\n"))
        val uk = entry.optString("ukphone").takeIf { it.isNotBlank() }
        val us = entry.optString("usphone").takeIf { it.isNotBlank() }
        if (translation == null && uk == null && us == null) return null

        val examples = json.optJSONObject("blng_sents_part")
            ?.optJSONArray("sentence-pair")
            ?.let { pairs ->
                (0 until pairs.length()).mapNotNull { index ->
                    val pair = pairs.optJSONObject(index) ?: return@mapNotNull null
                    val english = pair.optString("sentence").trim()
                    if (english.isEmpty()) return@mapNotNull null
                    val chinese = pair.optString("sentence-translation").trim()
                    WordExample(english, chinese.takeIf { it.isNotEmpty() })
                }
            }
            .orEmpty()
            .take(3)

        val phrases = json.optJSONObject("phrs")
            ?.optJSONArray("phrs")
            ?.let { list ->
                (0 until list.length()).mapNotNull { index ->
                    val phr = list.optJSONObject(index)?.optJSONObject("phr") ?: return@mapNotNull null
                    val phrase = phr.optJSONObject("headword")
                        ?.optJSONObject("l")
                        ?.optString("i")
                        ?.trim()
                        .orEmpty()
                    if (phrase.isEmpty()) return@mapNotNull null
                    val meaning = phr.optJSONArray("trs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("tr")
                        ?.optJSONObject("l")
                        ?.optString("i")
                        ?.trim()
                    WordPhrase(phrase, meaning?.takeIf { it.isNotEmpty() })
                }
            }
            .orEmpty()
            .take(6)

        return WordInfo(
            word = word,
            phoneticUk = uk,
            phoneticUs = us,
            translation = translation,
            examples = examples,
            phrases = phrases,
        )
    }

    /** 有道的轻量词典接口，作为 jsonapi 失败时的第二个来源，避免回退到机器翻译。 */
    private fun lookupYoudaoSuggest(word: String): WordInfo? {
        val url = "https://dict.youdao.com/suggest?num=5&ver=3.0&doctype=json&cache=false&le=eng&q=" +
            URLEncoder.encode(word, "UTF-8")
        val body = httpGet(url) ?: return null
        val entries = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("data")
            ?.optJSONArray("entries")
            ?: return null
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (!entry.optString("entry").equals(word, ignoreCase = true)) continue
            val explain = entry.optString("explain").removeSuffix("...").removeSuffix("…")
            val translation = sanitizeTranslation(explain) ?: continue
            return WordInfo(word = word, translation = translation)
        }
        return null
    }

    /**
     * 依次尝试：有道 jsonapi → 有道 suggest → 词形还原后的原形。
     * 返回 null 表示没有拿到可信的词典释义，调用方会显示「未查到」。
     */
    private fun buildWordInfo(query: String): WordInfo? {
        val direct = runCatching { lookupYoudaoWeb(query) }.getOrNull()
        if (direct?.translation != null) return fillPhonetics(direct, query)

        val brief = runCatching { lookupYoudaoSuggest(query) }.getOrNull()
        if (brief?.translation != null) {
            val merged = direct?.copy(translation = brief.translation) ?: brief
            return fillPhonetics(merged, query)
        }

        for (lemma in lemmas(query)) {
            val base = runCatching { lookupYoudaoWeb(lemma) }.getOrNull()?.takeIf { it.translation != null }
                ?: runCatching { lookupYoudaoSuggest(lemma) }.getOrNull()?.takeIf { it.translation != null }
            if (base != null) return fillPhonetics(base, lemma)
        }

        return (direct ?: brief)?.let { fillPhonetics(it, query) }
    }

    /** 有道没给音标时，用 dictionaryapi.dev 补齐；查不到原形时再试一次词形还原。 */
    private fun fillPhonetics(info: WordInfo, query: String): WordInfo {
        if (info.phoneticUk != null || info.phoneticUs != null || info.phoneticGeneral != null) return info
        val phonetics = runCatching { fetchPhonetics(query) }.getOrNull()
            ?: lemmas(query).firstNotNullOfOrNull { lemma -> runCatching { fetchPhonetics(lemma) }.getOrNull() }
            ?: return info
        return info.copy(
            phoneticUk = phonetics.uk,
            phoneticUs = phonetics.us,
            phoneticGeneral = phonetics.general,
        )
    }

    /**
     * 首字母大写的普通单词按小写查询，避免 "One" 命中人名条目；
     * 全大写单词同样降为小写，只有 "I" 保持原样。
     */
    private fun queryForm(word: String): String {
        if (word == "I") return word
        val lower = word.lowercase(Locale.US)
        val allCaps = word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() }
        val capitalized = lower.replaceFirstChar { it.uppercase(Locale.US) }
        return if (word == capitalized || allCaps) lower else word
    }

    /** 去掉选区里可能带上的引号、标点，避免 "had," 之类的查询打出空结果。 */
    private fun cleanQuery(word: String): String = word.trim()
        .trim('"', '\'', '“', '”', '‘', '’', '.', ',', '!', '?', ';', ':', '(', ')', '[', ']', '…', '—', ' ', '\n', '\t')

    /** 常见不规则变化 + 简单后缀规则；有道本身会标注多数词形，这里只作为兜底。 */
    private fun lemmas(word: String): List<String> {
        val lower = word.lowercase(Locale.US)
        val result = LinkedHashSet<String>()
        irregularLemmas[lower]?.let { result.add(it) }

        fun add(stem: String) {
            if (stem.length >= 2 && stem != lower) result.add(stem)
        }

        if (lower.endsWith("ies") && lower.length > 4) add(lower.dropLast(3) + "y")
        if (lower.endsWith("ied") && lower.length > 4) add(lower.dropLast(3) + "y")
        if (lower.endsWith("ing") && lower.length > 5) {
            val stem = lower.dropLast(3)
            if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
            add(stem)
            add(stem + "e")
        }
        if (lower.endsWith("ed") && lower.length > 4) {
            val stem = lower.dropLast(2)
            if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
            add(stem)
            add(stem + "e")
        }
        if (lower.endsWith("es") && lower.length > 3) {
            add(lower.dropLast(2))
            add(lower.dropLast(1))
        }
        if (lower.endsWith("s") && lower.length > 3 && !lower.endsWith("ss")) add(lower.dropLast(1))
        if (lower.endsWith("er") && lower.length > 4) {
            add(lower.dropLast(2))
            add(lower.dropLast(2) + "e")
        }
        if (lower.endsWith("est") && lower.length > 5) {
            add(lower.dropLast(3))
            add(lower.dropLast(3) + "e")
        }
        return result.take(4)
    }

    /** 词典释义行：丢弃整句翻译和明显的占位内容。 */
    private fun sanitizeTranslation(raw: String?): String? {
        val lines = raw?.lines().orEmpty()
            .map { it.trim().trim(';', '；', ' ') }
            .filter { it.isNotEmpty() }
            .filter { line ->
                line !in junkTranslations &&
                    !(sentenceEndings.any { line.endsWith(it) } &&
                        partOfSpeechPrefixes.none { line.startsWith(it) })
            }
            .distinct()
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun youdaoLines(entry: JSONObject): List<String> {
        val trs = entry.optJSONArray("trs") ?: return emptyList()
        return (0 until trs.length()).flatMap trsLoop@ { index ->
            val trList = trs.optJSONObject(index)?.optJSONArray("tr") ?: return@trsLoop emptyList()
            (0 until trList.length()).flatMap { trIndex ->
                val line = trList.optJSONObject(trIndex)?.optJSONObject("l") ?: return@flatMap emptyList()
                val items = line.optJSONArray("i")
                if (items != null) {
                    (0 until items.length()).mapNotNull { item ->
                        items.optString(item).trim().takeIf { it.isNotEmpty() }
                    }
                } else {
                    listOfNotNull(line.optString("i").trim().takeIf { it.isNotEmpty() })
                }
            }
        }
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

        val sentenceEndings = listOf("。", "！", "？", "!")
        val partOfSpeechPrefixes = listOf(
            "n.", "v.", "vt.", "vi.", "adj.", "adv.", "prep.", "pron.", "conj.", "num.",
            "art.", "int.", "aux.", "abbr.", "det.", "modal", "phr.", "【",
        )
        val junkTranslations = setOf("对话中", "对话中。", "翻译中", "翻译中。", "正在翻译")

        val irregularLemmas = mapOf(
            "had" to "have", "has" to "have", "having" to "have",
            "was" to "be", "were" to "be", "been" to "be", "is" to "be", "are" to "be", "am" to "be",
            "did" to "do", "does" to "do", "done" to "do",
            "went" to "go", "gone" to "go",
            "said" to "say", "made" to "make", "took" to "take", "taken" to "take",
            "came" to "come", "saw" to "see", "seen" to "see",
            "got" to "get", "gotten" to "get", "gave" to "give", "given" to "give",
            "knew" to "know", "known" to "know", "thought" to "think",
            "found" to "find", "told" to "tell", "became" to "become",
            "left" to "leave", "felt" to "feel", "brought" to "bring",
            "began" to "begin", "begun" to "begin", "kept" to "keep",
            "held" to "hold", "wrote" to "write", "written" to "write",
            "stood" to "stand", "heard" to "hear", "meant" to "mean",
            "met" to "meet", "ran" to "run", "paid" to "pay", "sat" to "sit",
            "spoke" to "speak", "spoken" to "speak",
            "led" to "lead", "grew" to "grow", "grown" to "grow",
            "lost" to "lose", "fell" to "fall", "fallen" to "fall",
            "sent" to "send", "built" to "build", "understood" to "understand",
            "drew" to "draw", "drawn" to "draw", "broke" to "break", "broken" to "break",
            "spent" to "spend", "rose" to "rise", "risen" to "rise",
            "drove" to "drive", "driven" to "drive", "bought" to "buy",
            "wore" to "wear", "worn" to "wear", "chose" to "choose", "chosen" to "choose",
            "children" to "child", "men" to "man", "women" to "woman",
            "feet" to "foot", "teeth" to "tooth", "mice" to "mouse", "geese" to "goose",
        )
    }
}
