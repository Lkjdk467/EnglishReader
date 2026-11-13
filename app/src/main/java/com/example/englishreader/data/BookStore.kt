package com.example.englishreader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class BookFormat(val label: String) {
    TXT("TXT"),
    EPUB("EPUB"),
    HTML("HTML"),
    ;

    companion object {
        fun fromFileName(name: String): BookFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "txt" -> TXT
            "epub" -> EPUB
            "html", "htm" -> HTML
            else -> null
        }
    }
}

data class RecentBook(
    val uri: String,
    val title: String,
    val position: Int,
    val progress: Float = 0f,
    val format: BookFormat = BookFormat.TXT,
)

class BookStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("english_reader", Context.MODE_PRIVATE)

    fun recentBooks(): List<RecentBook> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val books = ArrayList<RecentBook>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: continue
            books.add(
                RecentBook(
                    uri = uri,
                    title = item.optString("title").ifBlank { "未命名" },
                    position = item.optInt("position", 0),
                    progress = item.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                    format = runCatching {
                        BookFormat.valueOf(item.optString("format", BookFormat.TXT.name))
                    }.getOrDefault(BookFormat.TXT),
                )
            )
        }
        return books
    }

    fun touch(book: RecentBook) {
        val books = recentBooks().filterNot { it.uri == book.uri }
        write((listOf(book) + books).take(MAX_BOOKS))
    }

    fun updatePosition(uri: String, position: Int, progress: Float) {
        val books = recentBooks()
        if (books.none { it.uri == uri }) return
        write(
            books.map {
                if (it.uri == uri) {
                    it.copy(position = position, progress = progress.coerceIn(0f, 1f))
                } else {
                    it
                }
            }
        )
    }

    fun remove(uri: String) {
        val books = recentBooks()
        if (books.none { it.uri == uri }) return
        write(books.filterNot { it.uri == uri })
    }

    private fun write(books: List<RecentBook>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject()
                    .put("uri", book.uri)
                    .put("title", book.title)
                    .put("position", book.position)
                    .put("progress", book.progress.toDouble())
                    .put("format", book.format.name)
            )
        }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    private companion object {
        const val KEY_RECENT = "recent_books"
        const val MAX_BOOKS = 10
    }
}
