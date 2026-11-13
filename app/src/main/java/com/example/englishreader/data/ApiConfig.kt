package com.example.englishreader.data

/**
 * 有道智云文本翻译 API：https://ai.youdao.com
 * 申请应用后，把应用的 App Key / App Secret 填到这里，查询就会自动走有道。
 * 留空时使用免费的 dictionaryapi.dev（音标）+ MyMemory（中文释义）。
 */
object ApiConfig {
    const val YOUDAO_APP_KEY = ""
    const val YOUDAO_APP_SECRET = ""

    val hasYoudao: Boolean
        get() = YOUDAO_APP_KEY.isNotBlank() && YOUDAO_APP_SECRET.isNotBlank()
}
