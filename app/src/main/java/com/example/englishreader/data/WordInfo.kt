package com.example.englishreader.data

data class WordExample(
    val english: String,
    val chinese: String? = null,
)

data class WordPhrase(
    val phrase: String,
    val meaning: String? = null,
)

data class WordInfo(
    val word: String,
    val phoneticUk: String? = null,
    val phoneticUs: String? = null,
    val phoneticGeneral: String? = null,
    val translation: String? = null,
    val examples: List<WordExample> = emptyList(),
    val phrases: List<WordPhrase> = emptyList(),
)
