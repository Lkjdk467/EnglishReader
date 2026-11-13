package com.example.englishreader.data

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

private const val MAX_PARAGRAPH_LENGTH = 1200

private val HtmlCommentRegex = Regex("(?is)<!--.*?-->")
private val HtmlHeadRegex = Regex("(?is)<head\\b.*?</head>")
private val HtmlScriptStyleRegex = Regex("(?is)<(script|style)\\b.*?</\\1>")
private val HtmlSpacesRegex = Regex("[ \\t\\u3000]+")
private val HtmlLineSpacesRegex = Regex(" *\\n *")
private val HtmlBlankLinesRegex = Regex("\\n{3,}")

suspend fun loadBookText(context: Context, uri: Uri, format: BookFormat): String = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("无法读取文件，请重新导入")
    when (format) {
        BookFormat.TXT -> decodeText(bytes)
        BookFormat.HTML -> htmlToText(decodeText(bytes))
            .ifBlank { throw IllegalStateException("没有从 HTML 中提取到文字") }
        BookFormat.EPUB -> readEpub(bytes)
    }
}

/** 空行分段，段内单个换行视为空格合并，避免硬换行的 TXT 被切成半句话。 */
fun toParagraphs(text: String): List<String> {
    val paragraphs = ArrayList<String>()
    val buffer = StringBuilder()

    fun flush() {
        if (buffer.isEmpty()) return
        appendChunks(buffer.toString(), paragraphs)
        buffer.setLength(0)
    }

    for (rawLine in text.split('\n')) {
        val line = rawLine.trim()
        if (line.isEmpty()) {
            flush()
        } else {
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(line)
        }
    }
    flush()
    return paragraphs
}

/** 超长段落按空格切块，避免单个 Text 过大卡顿。 */
private fun appendChunks(line: String, paragraphs: MutableList<String>) {
    if (line.length <= MAX_PARAGRAPH_LENGTH) {
        paragraphs.add(line)
        return
    }
    var start = 0
    while (start < line.length) {
        var end = (start + MAX_PARAGRAPH_LENGTH).coerceAtMost(line.length)
        if (end < line.length) {
            val space = line.lastIndexOf(' ', end)
            if (space > start) end = space
        }
        paragraphs.add(line.substring(start, end))
        start = end
        while (start < line.length && line[start] == ' ') start++
    }
}

private fun decodeText(bytes: ByteArray): String {
    val offset = if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) 3 else 0
    val text = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()
    } catch (e: CharacterCodingException) {
        Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
    }
    return text.replace("\r\n", "\n").replace('\r', '\n')
}

/** EPUB 按 OPF spine 顺序抽正文，转成和 TXT 一样的段落文本，阅读器无需区分格式。 */
private fun readEpub(bytes: ByteArray): String {
    val entries = readEpubEntries(bytes)
    if (entries.isEmpty()) throw IllegalStateException("EPUB 文件已损坏或没有正文")

    val opfPath = findOpfPath(entries)
    val spine = if (opfPath != null) readSpine(entries, opfPath) else emptyList()
    val docs = spine.filter { entries.containsKey(it) }
        .ifEmpty { entries.keys.filter { it.isHtmlFile() }.sorted() }
    if (docs.isEmpty()) throw IllegalStateException("EPUB 中没有找到可阅读的正文")

    val text = docs
        .joinToString("\n\n") { path -> htmlToText(decodeText(entries.getValue(path))) }
        .trim()
    if (text.isBlank()) throw IllegalStateException("EPUB 中没有提取到文字内容")
    return text
}

private fun readEpubEntries(bytes: ByteArray): Map<String, ByteArray> {
    val entries = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val name = decodePath(entry.name.replace('\\', '/').removePrefix("./"))
            if (!entry.isDirectory && name.isEpubTextFile()) {
                entries[name] = zip.readBytes()
            }
            entry = zip.nextEntry
        }
    }
    return entries
}

private fun String.isHtmlFile(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
}

private fun String.isEpubTextFile(): Boolean {
    val lower = lowercase()
    return isHtmlFile() || lower.endsWith(".opf") || lower.endsWith(".xml") || lower.endsWith(".ncx")
}

private fun findOpfPath(entries: Map<String, ByteArray>): String? {
    val containerKey = entries.keys.firstOrNull { it.equals("META-INF/container.xml", ignoreCase = true) }
    val declared = containerKey?.let { key ->
        parseXml(entries.getValue(key)) { parser ->
            var path: String? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.localName() == "rootfile") {
                    path = parser.getAttributeValue(null, "full-path")?.let(::decodePath)
                    if (path != null) break
                }
            }
            path
        }
    }
    if (declared != null && entries.containsKey(declared)) return declared
    return entries.keys.firstOrNull { it.lowercase().endsWith(".opf") }
}

private fun readSpine(entries: Map<String, ByteArray>, opfPath: String): List<String> {
    val opf = entries[opfPath] ?: return emptyList()
    val baseDir = opfPath.substringBeforeLast('/', "")
    val manifest = HashMap<String, String>()
    val order = ArrayList<String>()

    parseXml(opf) { parser ->
        var inSpine = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.localName()) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val mediaType = parser.getAttributeValue(null, "media-type")
                        if (id != null && href != null &&
                            (mediaType == null || mediaType.contains("html", ignoreCase = true))
                        ) {
                            manifest[id] = resolvePath(baseDir, href)
                        }
                    }
                    "spine" -> inSpine = true
                    "itemref" -> if (inSpine) {
                        parser.getAttributeValue(null, "idref")?.let { order.add(it) }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.localName() == "spine") inSpine = false
            }
        }
        null
    }

    return order.mapNotNull { manifest[it] }
}

private fun XmlPullParser.localName(): String = name.substringAfterLast(':')

private fun <T> parseXml(bytes: ByteArray, block: (XmlPullParser) -> T): T? = runCatching {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(ByteArrayInputStream(bytes), null)
    block(parser)
}.getOrNull()

private fun resolvePath(baseDir: String, href: String): String {
    val clean = decodePath(href.substringBefore('#').substringBefore('?'))
    val joined = if (baseDir.isBlank()) clean else "$baseDir/$clean"
    val parts = ArrayDeque<String>()
    for (part in joined.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeLast()
            else -> parts.addLast(part)
        }
    }
    return parts.joinToString("/")
}

private fun decodePath(value: String): String =
    if ('%' in value) runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value) else value

private fun htmlToText(html: String): String {
    val cleaned = html
        .replace(HtmlCommentRegex, " ")
        .replace(HtmlHeadRegex, " ")
        .replace(HtmlScriptStyleRegex, " ")
    return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .replace('\u200B', ' ')
        .replace('\uFFFC', ' ')
        .replace(HtmlSpacesRegex, " ")
        .replace(HtmlLineSpacesRegex, "\n")
        .replace(HtmlBlankLinesRegex, "\n\n")
        .trim()
}
