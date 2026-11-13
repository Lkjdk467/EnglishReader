package com.example.englishreader.data

import android.content.Context

enum class ReaderTheme { BEIGE, WHITE, DARK }

enum class ReaderPageMode { SCROLL, SLIDE, COVER }

enum class TranslateEngine { YOUDAO, BAIDU }

enum class TtsEngine { SYSTEM, QWEN }

data class ReaderSettings(
    val fontSizeSp: Int = 20,
    val theme: ReaderTheme = ReaderTheme.BEIGE,
    val pageMode: ReaderPageMode = ReaderPageMode.SCROLL,
    val engine: TranslateEngine = TranslateEngine.YOUDAO,
    val baiduAppId: String = "",
    val baiduAppKey: String = "",
    val ttsEngine: TtsEngine = TtsEngine.SYSTEM,
    val qwenApiKey: String = "",
    val qwenVoice: String = "Serena",
    /** 为空表示自动挑选系统里安装的语音引擎（优先第三方 / MultiTTS）。 */
    val systemTtsPackage: String = "",
    val speechRate: Float = 1f,
    val speechPitch: Float = 1f,
) {
    val hasBaidu: Boolean
        get() = baiduAppId.isNotBlank() && baiduAppKey.isNotBlank()

    val hasQwen: Boolean
        get() = qwenApiKey.isNotBlank()
}

class ReaderSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    fun load(): ReaderSettings = ReaderSettings(
        fontSizeSp = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
        theme = prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
            ?: ReaderTheme.BEIGE,
        pageMode = prefs.getString(KEY_PAGE_MODE, null)
            ?.let { runCatching { ReaderPageMode.valueOf(it) }.getOrNull() }
            ?: ReaderPageMode.SCROLL,
        engine = prefs.getString(KEY_ENGINE, null)
            ?.let { runCatching { TranslateEngine.valueOf(it) }.getOrNull() }
            ?: TranslateEngine.YOUDAO,
        baiduAppId = prefs.getString(KEY_BAIDU_APP_ID, "").orEmpty(),
        baiduAppKey = prefs.getString(KEY_BAIDU_APP_KEY, "").orEmpty(),
        ttsEngine = prefs.getString(KEY_TTS_ENGINE, null)
            ?.let { runCatching { TtsEngine.valueOf(it) }.getOrNull() }
            ?: TtsEngine.SYSTEM,
        qwenApiKey = prefs.getString(KEY_QWEN_API_KEY, "").orEmpty(),
        qwenVoice = prefs.getString(KEY_QWEN_VOICE, DEFAULT_QWEN_VOICE).orEmpty()
            .ifBlank { DEFAULT_QWEN_VOICE },
        systemTtsPackage = prefs.getString(KEY_TTS_PACKAGE, "").orEmpty(),
        speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1f).coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
        speechPitch = prefs.getFloat(KEY_SPEECH_PITCH, 1f).coerceIn(MIN_SPEECH_PITCH, MAX_SPEECH_PITCH),
    )

    fun save(settings: ReaderSettings) {
        prefs.edit()
            .putInt(KEY_FONT_SIZE, settings.fontSizeSp)
            .putString(KEY_THEME, settings.theme.name)
            .putString(KEY_PAGE_MODE, settings.pageMode.name)
            .putString(KEY_ENGINE, settings.engine.name)
            .putString(KEY_BAIDU_APP_ID, settings.baiduAppId)
            .putString(KEY_BAIDU_APP_KEY, settings.baiduAppKey)
            .putString(KEY_TTS_ENGINE, settings.ttsEngine.name)
            .putString(KEY_QWEN_API_KEY, settings.qwenApiKey)
            .putString(KEY_QWEN_VOICE, settings.qwenVoice)
            .putString(KEY_TTS_PACKAGE, settings.systemTtsPackage)
            .putFloat(KEY_SPEECH_RATE, settings.speechRate)
            .putFloat(KEY_SPEECH_PITCH, settings.speechPitch)
            .apply()
    }

    companion object {
        const val MIN_FONT_SIZE = 16
        const val MAX_FONT_SIZE = 32
        const val DEFAULT_FONT_SIZE = 20
        const val DEFAULT_QWEN_VOICE = "Serena"
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2f
        const val MIN_SPEECH_PITCH = 0.5f
        const val MAX_SPEECH_PITCH = 2f

        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme"
        private const val KEY_PAGE_MODE = "page_mode"
        private const val KEY_ENGINE = "engine"
        private const val KEY_BAIDU_APP_ID = "baidu_app_id"
        private const val KEY_BAIDU_APP_KEY = "baidu_app_key"
        private const val KEY_TTS_ENGINE = "tts_engine"
        private const val KEY_QWEN_API_KEY = "qwen_api_key"
        private const val KEY_QWEN_VOICE = "qwen_voice"
        private const val KEY_TTS_PACKAGE = "tts_package"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
    }
}
