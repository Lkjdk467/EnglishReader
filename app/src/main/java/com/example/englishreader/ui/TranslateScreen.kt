package com.example.englishreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishreader.data.DictionaryRepository
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.TranslateDirection
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.data.VocabularyStore
import com.example.englishreader.data.detectTranslateDirection
import com.example.englishreader.tts.Speaker
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    settings: ReaderSettings,
    repository: DictionaryRepository,
    speaker: Speaker,
    vocabulary: VocabularyStore,
    onOpenShelf: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechState by speaker.state.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var direction by rememberSaveable { mutableStateOf(TranslateDirection.EN_TO_ZH) }
    var translating by remember { mutableStateOf(false) }
    var pickedWord by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onOpenShelf)

    DisposableEffect(Unit) {
        onDispose { speaker.stop() }
    }

    fun speak(text: String, locale: Locale) {
        val started = speaker.speak(text, locale, settings) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        if (!started && settings.ttsEngine == TtsEngine.SYSTEM) {
            Toast.makeText(context, "语音引擎还没准备好，请稍后再试", Toast.LENGTH_SHORT).show()
        }
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("译文", text))
        Toast.makeText(context, "已复制译文", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("翻译工具") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            HomeBottomBar(selected = HomeTab.TRANSLATE, hazeState = null) { tab ->
                if (tab == HomeTab.SHELF) onOpenShelf()
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("输入内容") },
                placeholder = { Text("输入中文自动译成英文，输入英文自动译成中文") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty()) return@Button
                    val detected = detectTranslateDirection(text)
                    translating = true
                    scope.launch {
                        val translated = repository.translate(text, settings, detected)
                        translating = false
                        if (translated == null) {
                            Toast.makeText(context, "翻译失败，请检查网络", Toast.LENGTH_SHORT).show()
                        } else {
                            direction = detected
                            result = translated
                        }
                    }
                },
                enabled = input.isNotBlank() && !translating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (translating) "翻译中…" else "翻译")
            }

            val translated = result
            if (translated != null) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = if (direction == TranslateDirection.EN_TO_ZH) {
                        "译文 · 英 → 中"
                    } else {
                        "译文 · 中 → 英 · 点击单词可查词"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.padding(14.dp)) {
                        TranslationText(
                            text = translated,
                            clickable = direction == TranslateDirection.ZH_TO_EN,
                            onWordClick = { pickedWord = it },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { copy(translated) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("复制译文")
                    }
                    FilledTonalButton(
                        onClick = {
                            speak(
                                text = translated,
                                locale = if (direction == TranslateDirection.EN_TO_ZH) Locale.CHINA else Locale.US,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("朗读译文")
                    }
                }
                StopSpeechButton(speechState) { speaker.stop() }
            }
        }
    }

    val picked = pickedWord
    if (picked != null) {
        WordSheet(
            word = picked,
            sentence = null,
            settings = settings,
            repository = repository,
            speaker = speaker,
            vocabulary = vocabulary,
            onDismiss = { pickedWord = null },
        )
    }
}

@Composable
private fun TranslationText(
    text: String,
    clickable: Boolean,
    onWordClick: (String) -> Unit,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 26.sp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier.pointerInput(text) {
                        detectTapGestures { position ->
                            val result = layout ?: return@detectTapGestures
                            val offset = result.getOffsetForPosition(position)
                            val range = wordRangeAt(text, offset) ?: return@detectTapGestures
                            onWordClick(text.substring(range.first, range.last + 1))
                        }
                    }
                } else {
                    Modifier
                }
            ),
        onTextLayout = { layout = it },
    )
}
