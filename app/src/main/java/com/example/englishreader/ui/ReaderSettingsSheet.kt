package com.example.englishreader.ui

import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishreader.R
import com.example.englishreader.data.ReaderPageMode
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.ReaderSettingsStore
import com.example.englishreader.data.ReaderTheme
import com.example.englishreader.data.TranslateEngine
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.tts.QwenTts
import com.example.englishreader.tts.Speaker
import com.example.englishreader.tts.TtsEngineOption
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    speaker: Speaker,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val engines by speaker.systemEngines.collectAsState()
    val engineLabel by speaker.engineLabel.collectAsState()

    LaunchedEffect(settings.ttsEngine, settings.systemTtsPackage) {
        speaker.prepare(settings)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            ReaderSettingsContent(
                settings = settings,
                onChange = onChange,
                engines = engines,
                engineLabel = engineLabel,
                onPreview = {
                    val started = speaker.speak(
                        Speaker.PREVIEW_TEXT,
                        Locale.US,
                        settings,
                    ) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    if (!started && settings.ttsEngine == TtsEngine.SYSTEM) {
                        Toast.makeText(context, "语音引擎还没准备好，请稍后再试", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

/** 阅读设置的全部内容，阅读页弹层和书架全局设置页共用。 */
@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    engines: List<TtsEngineOption> = emptyList(),
    engineLabel: String = "",
    onPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var pageModeMenu by remember { mutableStateOf(false) }
    var engineMenu by remember { mutableStateOf(false) }
    var ttsMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    var systemEngineMenu by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(
            text = "字体大小 ${settings.fontSizeSp}sp",
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = settings.fontSizeSp.toFloat(),
            onValueChange = { value ->
                val size = value.roundToInt()
                if (size != settings.fontSizeSp) onChange(settings.copy(fontSizeSp = size))
            },
            valueRange = ReaderSettingsStore.MIN_FONT_SIZE.toFloat()..ReaderSettingsStore.MAX_FONT_SIZE.toFloat(),
            steps = ReaderSettingsStore.MAX_FONT_SIZE - ReaderSettingsStore.MIN_FONT_SIZE - 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "正文预览：The quick brown fox jumps over the lazy dog.",
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * 1.6f).sp,
        )

        Spacer(Modifier.height(20.dp))
        Text("媒体音量", style = MaterialTheme.typography.titleSmall)
        ReaderVolumeSlider()

        Spacer(Modifier.height(24.dp))
        Text("翻页方式", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { pageModeMenu = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = settings.pageMode.label,
                    modifier = Modifier.weight(1f),
                )
                Text("▾")
            }
            DropdownMenu(expanded = pageModeMenu, onDismissRequest = { pageModeMenu = false }) {
                ReaderPageMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            pageModeMenu = false
                            onChange(settings.copy(pageMode = mode))
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("阅读背景", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { onChange(settings.copy(theme = theme)) },
                    label = { Text(theme.label) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("翻译引擎", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { engineMenu = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = settings.engine.label,
                    modifier = Modifier.weight(1f),
                )
                Text("▾")
            }
            DropdownMenu(expanded = engineMenu, onDismissRequest = { engineMenu = false }) {
                TranslateEngine.entries.forEach { engine ->
                    DropdownMenuItem(
                        text = { Text(engine.label) },
                        onClick = {
                            engineMenu = false
                            onChange(settings.copy(engine = engine))
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "百度翻译（可选）",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = settings.baiduAppId,
            onValueChange = { onChange(settings.copy(baiduAppId = it.trim())) },
            label = { Text("百度 APP ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = settings.baiduAppKey,
            onValueChange = { onChange(settings.copy(baiduAppKey = it.trim())) },
            label = { Text("百度密钥") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (settings.engine == TranslateEngine.BAIDU && !settings.hasBaidu) {
                "百度未配置 APP ID / 密钥，将自动回退到有道翻译。"
            } else {
                "未配置百度 Key 时会自动使用有道翻译。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Text("朗读引擎", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { ttsMenu = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = settings.ttsEngine.label,
                    modifier = Modifier.weight(1f),
                )
                Text("▾")
            }
            DropdownMenu(expanded = ttsMenu, onDismissRequest = { ttsMenu = false }) {
                TtsEngine.entries.forEach { engine ->
                    DropdownMenuItem(
                        text = { Text(engine.label) },
                        onClick = {
                            ttsMenu = false
                            onChange(settings.copy(ttsEngine = engine))
                        },
                    )
                }
            }
        }

        if (settings.ttsEngine == TtsEngine.SYSTEM) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "系统语音引擎",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val selected = engines.firstOrNull { it.packageName == settings.systemTtsPackage }
            Box {
                OutlinedButton(
                    onClick = { systemEngineMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selected?.label ?: "自动（优先 MultiTTS / 第三方）",
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾")
                }
                DropdownMenu(
                    expanded = systemEngineMenu,
                    onDismissRequest = { systemEngineMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("自动（优先 MultiTTS / 第三方）") },
                        onClick = {
                            systemEngineMenu = false
                            onChange(settings.copy(systemTtsPackage = ""))
                        },
                    )
                    engines.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.label)
                                    Text(
                                        text = option.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                systemEngineMenu = false
                                onChange(settings.copy(systemTtsPackage = option.packageName))
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "当前使用：${engineLabel.ifBlank { "未知" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("语速", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(settings.speechRate * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = settings.speechRate,
                onValueChange = { onChange(settings.copy(speechRate = it)) },
                valueRange = ReaderSettingsStore.MIN_SPEECH_RATE..ReaderSettingsStore.MAX_SPEECH_RATE,
                steps = 14,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("音调", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(settings.speechPitch * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = settings.speechPitch,
                onValueChange = { onChange(settings.copy(speechPitch = it)) },
                valueRange = ReaderSettingsStore.MIN_SPEECH_PITCH..ReaderSettingsStore.MAX_SPEECH_PITCH,
                steps = 14,
            )
            Text(
                text = if (engines.isEmpty()) {
                    "未读取到额外的系统语音引擎，将使用系统默认引擎。"
                } else {
                    "语速和音调对系统引擎生效；部分引擎不支持时自动使用默认值。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onPreview != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("▶ 试听当前发音")
            }
        }

        if (settings.ttsEngine == TtsEngine.QWEN) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.qwenApiKey,
                onValueChange = { onChange(settings.copy(qwenApiKey = it.trim())) },
                label = { Text("Qwen3-TTS API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Box {
                OutlinedButton(
                    onClick = { voiceMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = QwenTts.voiceLabel(settings.qwenVoice),
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾")
                }
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                    QwenTts.voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.label) },
                            onClick = {
                                voiceMenu = false
                                onChange(settings.copy(qwenVoice = voice.id))
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (settings.hasQwen) {
                    "长文本会自动分段合成；播放失败时回退系统朗读。"
                } else {
                    "请填写阿里云百炼（北京地域）API Key，未配置时自动使用系统朗读。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "使用手机系统语音引擎，可在系统设置中安装中英文语音包。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 系统媒体音量滑块。直接读写 STREAM_MUSIC，物理音量键改动会通过
 * Settings.System 的内容观察者同步回来，拖动滑块时 TTS 的音量立即变化。
 */
@Composable
private fun ReaderVolumeSlider() {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember(audio) {
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember(audio) {
        mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    DisposableEffect(context, audio) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_volume_up),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Slider(
            value = volume.toFloat(),
            onValueChange = { value ->
                val target = value.roundToInt()
                if (target == volume) return@Slider
                volume = target
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = maxVolume - 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${(volume * 100f / maxVolume).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ReaderTheme.label: String
    get() = when (this) {
        ReaderTheme.BEIGE -> "护眼米色"
        ReaderTheme.WHITE -> "纯白"
        ReaderTheme.DARK -> "夜间"
    }

private val ReaderPageMode.label: String
    get() = when (this) {
        ReaderPageMode.SCROLL -> "瀑布流（上下滑动）"
        ReaderPageMode.SLIDE -> "平移翻页（左右滑动）"
        ReaderPageMode.COVER -> "覆盖翻页（卡片叠放）"
    }

private val TranslateEngine.label: String
    get() = when (this) {
        TranslateEngine.YOUDAO -> "有道翻译"
        TranslateEngine.BAIDU -> "百度翻译"
    }

private val TtsEngine.label: String
    get() = when (this) {
        TtsEngine.SYSTEM -> "系统自带 TTS"
        TtsEngine.QWEN -> "Qwen3-TTS（阿里云）"
    }
