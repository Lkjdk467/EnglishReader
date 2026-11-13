package com.example.englishreader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

class VocabularyStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "word_key TEXT PRIMARY KEY, " +
                "word TEXT NOT NULL, " +
                "phonetic_uk TEXT, " +
                "phonetic_us TEXT, " +
                "phonetic_general TEXT, " +
                "translation TEXT, " +
                "created_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(info: WordInfo) {
        val values = ContentValues().apply {
            put("word_key", info.word.lowercase(Locale.US))
            put("word", info.word)
            put("phonetic_uk", info.phoneticUk)
            put("phonetic_us", info.phoneticUs)
            put("phonetic_general", info.phoneticGeneral)
            put("translation", info.translation)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun remove(word: String) {
        writableDatabase.delete(TABLE, "word_key = ?", arrayOf(word.lowercase(Locale.US)))
    }

    fun contains(word: String): Boolean =
        readableDatabase.query(
            TABLE,
            arrayOf("word_key"),
            "word_key = ?",
            arrayOf(word.lowercase(Locale.US)),
            null,
            null,
            null,
        ).use { it.moveToFirst() }

    fun all(): List<WordInfo> {
        val words = ArrayList<WordInfo>()
        readableDatabase.query(
            TABLE,
            arrayOf("word", "phonetic_uk", "phonetic_us", "phonetic_general", "translation"),
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                words.add(
                    WordInfo(
                        word = cursor.getString(0),
                        phoneticUk = cursor.getString(1),
                        phoneticUs = cursor.getString(2),
                        phoneticGeneral = cursor.getString(3),
                        translation = cursor.getString(4),
                    )
                )
            }
        }
        return words
    }

    private companion object {
        const val DB_NAME = "vocabulary.db"
        const val DB_VERSION = 1
        const val TABLE = "words"
    }
}
