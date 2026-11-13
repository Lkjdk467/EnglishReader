package com.example.englishreader.data

data class WordInfo(
    val word: String,
    val phoneticUk: String? = null,
    val phoneticUs: String? = null,
    val phoneticGeneral: String? = null,
    val translation: String? = null,
)
