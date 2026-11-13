package com.example.englishreader.tts

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.ReaderSettingsStore
import com.example.englishreader.data.TtsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class SpeechState { IDLE, LOADING, SPEAKING }

data class TtsEngineOption(val packageName: String, val label: String)

class Speaker(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var ready = false
    /** 传给 TextToSpeech 构造函数的引擎包名，null 表示系统默认引擎。 */
    private var boundEngine: String? = null
    private val failedEngines = mutableSetOf<String>()
    private var voiceParamWarningShown = false
    private var utterancePrefix: String? = null
    private var lastUtterance: String? = null
    private var awaitDone: CompletableDeferred<Boolean>? = null
    private var playJob: Job? = null
    private var player: MediaPlayer? = null
    /** 引擎正在重新初始化时暂存一次朗读，初始化完成后自动补播。 */
    private var retryAfterInit: (() -> Unit)? = null

    private val _state = MutableStateFlow(SpeechState.IDLE)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _systemEngines = MutableStateFlow<List<TtsEngineOption>>(emptyList())
    val systemEngines: StateFlow<List<TtsEngineOption>> = _systemEngines.asStateFlow()

    private val _engineLabel = MutableStateFlow("正在初始化")
    val engineLabel: StateFlow<String> = _engineLabel.asStateFlow()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (isActive(utteranceId)) _state.value = SpeechState.SPEAKING
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId != null && utteranceId == lastUtterance) {
                clearUtterance()
                _state.value = SpeechState.IDLE
                completeAwait(true)
            }
        }

        override fun onError(utteranceId: String?) {
            if (isActive(utteranceId)) {
                clearUtterance()
                tts?.stop()
                _state.value = SpeechState.IDLE
                completeAwait(false)
            }
        }
    }

    init {
        bindEngine(resolveEngine(ReaderSettingsStore(appContext).load().systemTtsPackage))
    }

    fun speak(
        text: String,
        locale: Locale,
        settings: ReaderSettings,
        onMessage: (String) -> Unit,
    ): Boolean {
        val content = text.trim()
        if (content.isEmpty()) return false
        stop()

        if (settings.ttsEngine == TtsEngine.QWEN) {
            if (settings.qwenApiKey.isBlank()) {
                val started = speakSystem(content, locale, settings, onMessage)
                onMessage(
                    if (started) {
                        "Qwen3-TTS 未填写 API Key，已改用系统朗读"
                    } else {
                        "Qwen3-TTS 未填写 API Key，系统朗读也不可用"
                    },
                )
                return started
            }

            _state.value = SpeechState.LOADING
            playJob = scope.launch {
                try {
                    playQwen(content, locale, settings)
                    _state.value = SpeechState.IDLE
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = SpeechState.IDLE
                    val detail = e.message?.take(80) ?: "未知错误"
                    val started = speakSystem(content, locale, settings, onMessage)
                    onMessage(
                        if (started) {
                            "Qwen3-TTS 失败：$detail；已改用系统朗读"
                        } else {
                            "Qwen3-TTS 失败：$detail；系统朗读也不可用"
                        },
                    )
                }
            }
            return true
        }

        return speakSystem(content, locale, settings, onMessage)
    }

    /** 朗读并等待播放结束，供全文朗读逐段排队使用；返回 false 表示没有可用的朗读引擎。 */
    suspend fun speakAwait(
        text: String,
        locale: Locale,
        settings: ReaderSettings,
        onMessage: (String) -> Unit,
    ): Boolean {
        val content = text.trim()
        if (content.isEmpty()) return false
        stop()

        if (settings.ttsEngine == TtsEngine.QWEN) {
            if (settings.qwenApiKey.isBlank()) {
                val started = speakSystemAwait(content, locale, settings, onMessage)
                onMessage(
                    if (started) {
                        "Qwen3-TTS 未填写 API Key，已改用系统朗读"
                    } else {
                        "Qwen3-TTS 未填写 API Key，系统朗读也不可用"
                    },
                )
                return started
            }

            _state.value = SpeechState.LOADING
            try {
                playQwen(content, locale, settings)
                _state.value = SpeechState.IDLE
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SpeechState.IDLE
                val detail = e.message?.take(80) ?: "未知错误"
                val started = speakSystemAwait(content, locale, settings, onMessage)
                onMessage(
                    if (started) {
                        "Qwen3-TTS 失败：$detail；已改用系统朗读"
                    } else {
                        "Qwen3-TTS 失败：$detail；系统朗读也不可用"
                    },
                )
                return started
            }
        }

        return speakSystemAwait(content, locale, settings, onMessage)
    }

    /** 立刻停止系统朗读和 Qwen 音频播放，并取消还在请求中的分段。 */
    fun stop() {
        playJob?.cancel()
        playJob = null
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        retryAfterInit = null
        clearUtterance()
        if (ready) tts?.stop()
        _state.value = SpeechState.IDLE
        completeAwait(false)
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
        scope.cancel()
    }

    /** 设置页切换引擎后立即重新初始化，避免首次试听落在旧引擎上。 */
    fun prepare(settings: ReaderSettings) {
        if (settings.ttsEngine == TtsEngine.SYSTEM) ensureEngine(settings)
    }

    private suspend fun playQwen(content: String, locale: Locale, settings: ReaderSettings) {
        val languageType = if (locale.language == "zh") "Chinese" else "English"
        val chunks = TtsText.split(content, QWEN_CHUNK_LIMIT)
        chunks.forEachIndexed { index, chunk ->
            val audioUrl = QwenTts.synthesize(
                text = chunk,
                voice = settings.qwenVoice,
                languageType = languageType,
                apiKey = settings.qwenApiKey,
            )
            currentCoroutineContext().ensureActive()
            _state.value = SpeechState.SPEAKING
            playAudio(audioUrl)
            currentCoroutineContext().ensureActive()
            if (index < chunks.lastIndex) _state.value = SpeechState.LOADING
        }
    }

    private fun speakSystem(
        text: String,
        locale: Locale,
        settings: ReaderSettings,
        onMessage: (String) -> Unit,
    ): Boolean {
        if (!ready || !ensureEngine(settings)) {
            retryAfterInit = { speakSystem(text, locale, settings, onMessage) }
            return true
        }
        val engine = tts ?: return false
        applyVoiceParams(engine, settings, onMessage)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return false
        }
        // 系统 TTS 单次输入有长度上限，长段落按标点分段后用 QUEUE_ADD 连续朗读。
        val limit = (TextToSpeech.getMaxSpeechInputLength() - SYSTEM_CHUNK_MARGIN).coerceAtLeast(200)
        val chunks = TtsText.split(text, limit)
        if (chunks.isEmpty()) return false

        val prefix = "utt-${System.nanoTime()}-"
        utterancePrefix = prefix
        lastUtterance = null
        chunks.forEachIndexed { index, chunk ->
            val utteranceId = prefix + index
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(chunk, mode, null, utteranceId) != TextToSpeech.SUCCESS) {
                if (index == 0) {
                    clearUtterance()
                    _state.value = SpeechState.IDLE
                    return false
                }
                return true
            }
            lastUtterance = utteranceId
        }
        _state.value = SpeechState.SPEAKING
        return true
    }

    /** 排队朗读系统 TTS 并等待最后一段结束，用于全文朗读。 */
    private suspend fun speakSystemAwait(
        text: String,
        locale: Locale,
        settings: ReaderSettings,
        onMessage: (String) -> Unit,
    ): Boolean {
        if (!ready) return false
        if (!ensureEngine(settings)) return false
        val engine = tts ?: return false
        applyVoiceParams(engine, settings, onMessage)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return false
        }
        val limit = (TextToSpeech.getMaxSpeechInputLength() - SYSTEM_CHUNK_MARGIN).coerceAtLeast(200)
        val chunks = TtsText.split(text, limit)
        if (chunks.isEmpty()) return false

        val done = CompletableDeferred<Boolean>()
        awaitDone = done
        val prefix = "utt-${System.nanoTime()}-"
        utterancePrefix = prefix
        lastUtterance = null
        var lastQueued: String? = null
        chunks.forEachIndexed { index, chunk ->
            val utteranceId = prefix + index
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val queued = engine.speak(chunk, mode, null, utteranceId) == TextToSpeech.SUCCESS
            if (!queued && index == 0) {
                clearUtterance()
                awaitDone = null
                _state.value = SpeechState.IDLE
                return false
            }
            if (queued) lastQueued = utteranceId
        }
        if (lastQueued == null) {
            clearUtterance()
            awaitDone = null
            return false
        }
        lastUtterance = lastQueued
        _state.value = SpeechState.SPEAKING
        return try {
            done.await()
        } finally {
            if (awaitDone === done) awaitDone = null
        }
    }

    private fun isActive(utteranceId: String?): Boolean {
        val prefix = utterancePrefix ?: return false
        return utteranceId != null && utteranceId.startsWith(prefix)
    }

    /** 用构造函数直接绑定引擎包名；packageName 为 null 时使用系统默认引擎。 */
    private fun bindEngine(packageName: String?) {
        tts?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        ready = false
        boundEngine = packageName
        _engineLabel.value = if (packageName == null) "系统默认引擎" else "正在连接 $packageName"
        val listener = TextToSpeech.OnInitListener { status -> onInit(status) }
        val instance = if (packageName == null) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, packageName)
        }
        tts = instance
        instance.setOnUtteranceProgressListener(progressListener)
    }

    private fun onInit(status: Int) {
        val instance = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            ready = false
            val failed = boundEngine
            if (failed != null) {
                failedEngines += failed
                _engineLabel.value = "指定引擎不可用，已回退系统默认"
                bindEngine(null)
            } else {
                _engineLabel.value = "语音引擎初始化失败"
            }
            return
        }
        ready = true
        instance.setLanguage(Locale.US)
        loadEngines(instance)

        // PackageManager 查不到引擎时（个别系统限制包可见性），用初始化后的引擎列表补一次 MultiTTS 绑定。
        val multitts = if (boundEngine == null) {
            _systemEngines.value
                .firstOrNull { it.packageName.contains("multitts", ignoreCase = true) }
                ?.packageName
                ?.takeIf { it !in failedEngines }
        } else {
            null
        }
        if (multitts != null) {
            bindEngine(multitts)
            return
        }
        updateEngineLabel()
        val retry = retryAfterInit
        retryAfterInit = null
        retry?.invoke()
    }

    private fun loadEngines(instance: TextToSpeech) {
        _systemEngines.value = instance.engines.orEmpty().map { engine ->
            TtsEngineOption(engine.name, engine.label.orEmpty().ifBlank { engine.name })
        }
    }

    /** 手动选择的包优先，否则优先包名含 multitts 的引擎，再退到其它第三方引擎。 */
    private fun resolveEngine(preferred: String): String? {
        val installed = installedEnginePackages()
        if (preferred.isNotBlank() && (installed.isEmpty() || preferred in installed)) return preferred
        installed.firstOrNull { it.contains("multitts", ignoreCase = true) }?.let { return it }
        val default = Settings.Secure.getString(appContext.contentResolver, "tts_default_synth")
        return installed.firstOrNull {
            it != default && !it.startsWith("com.google.android.tts")
        }
    }

    private fun installedEnginePackages(): List<String> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        return appContext.packageManager.queryIntentServices(intent, 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
    }

    /** 绑定与设置不一致时重新初始化；返回 false 表示正在切换，本次朗读先跳过。 */
    private fun ensureEngine(settings: ReaderSettings): Boolean {
        if (!ready) return false
        val target = resolveEngine(settings.systemTtsPackage)
        if (target == boundEngine || target in failedEngines) return true
        bindEngine(target)
        return false
    }

    private fun updateEngineLabel() {
        val instance = tts ?: return
        val packageName = boundEngine ?: instance.defaultEngine
        if (packageName == null) {
            _engineLabel.value = "系统默认引擎"
            return
        }
        val label = _systemEngines.value.firstOrNull { it.packageName == packageName }?.label
        _engineLabel.value = if (boundEngine == null) {
            "系统默认 · ${label ?: packageName}（$packageName）"
        } else {
            "${label ?: packageName}（$packageName）"
        }
    }

    private fun applyVoiceParams(
        engine: TextToSpeech,
        settings: ReaderSettings,
        onMessage: (String) -> Unit,
    ) {
        val rate = engine.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2f))
        val pitch = engine.setPitch(settings.speechPitch.coerceIn(0.5f, 2f))
        if (!voiceParamWarningShown && (rate == TextToSpeech.ERROR || pitch == TextToSpeech.ERROR)) {
            voiceParamWarningShown = true
            onMessage("当前语音引擎不支持调节语速或音调，已使用默认值")
        }
    }

    private fun clearUtterance() {
        utterancePrefix = null
        lastUtterance = null
    }

    private fun completeAwait(result: Boolean) {
        val done = awaitDone ?: return
        awaitDone = null
        done.complete(result)
    }

    private suspend fun playAudio(url: String) {
        val file = downloadAudio(url)
        try {
            playFile(file)
        } finally {
            file.delete()
        }
    }

    private suspend fun downloadAudio(url: String): File = withContext(Dispatchers.IO) {
        // 接口返回的 OSS 地址偶尔是 http，统一走 https 避免被系统明文流量策略拦截。
        val safeUrl = if (url.startsWith("http://")) {
            "https://" + url.removePrefix("http://")
        } else {
            url
        }
        val connection = URL(safeUrl).openConnection() as HttpURLConnection
        val file = File(appContext.cacheDir, "qwen-tts-${System.nanoTime()}.wav")
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "EnglishReader/1.0")
            if (connection.responseCode !in 200..299) {
                throw IOException("音频下载失败（${connection.responseCode}）")
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            ensureActive()
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun playFile(file: File) = suspendCancellableCoroutine<Unit> { continuation ->
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        var released = false

        fun release() {
            if (released) return
            released = true
            runCatching { mediaPlayer.release() }
            if (player === mediaPlayer) player = null
        }

        continuation.invokeOnCancellation {
            runCatching { mediaPlayer.stop() }
            release()
        }
        mediaPlayer.setOnPreparedListener {
            if (continuation.isActive) {
                it.start()
            } else {
                release()
            }
        }
        mediaPlayer.setOnCompletionListener {
            release()
            if (continuation.isActive) continuation.resume(Unit)
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            release()
            if (continuation.isActive) continuation.resumeWithException(IOException("音频播放失败"))
            true
        }

        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            release()
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    }

    companion object {
        const val PREVIEW_TEXT = "Hello, this is a test of the text to speech engine."

        /** Qwen3-TTS 单次最多 600 字符，留出标点余量。 */
        private const val QWEN_CHUNK_LIMIT = 550
        private const val SYSTEM_CHUNK_MARGIN = 500
    }
}
