package com.example.englishreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishreader.data.DictionaryRepository
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.TranslateDirection
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.data.VocabularyStore
import com.example.englishreader.data.WordInfo
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
    onOpenMore: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechState by speaker.state.collectAsState()

    var inputField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val input = inputField.text
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var wordQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var wordInfo by remember { mutableStateOf<WordInfo?>(null) }
    var lookupSeq by remember { mutableStateOf(0) }
    var direction by rememberSaveable { mutableStateOf(TranslateDirection.EN_TO_ZH) }
    var translating by remember { mutableStateOf(false) }
    var pickedWord by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(true) }
    var pendingFocus by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }

    val selection = inputField.selection
    val selectedText = input.substring(
        selection.min.coerceIn(0, input.length),
        selection.max.coerceIn(0, input.length),
    ).trim()

    LaunchedEffect(wordQuery, lookupSeq) {
        val query = wordQuery
        if (query == null) {
            wordInfo = null
            return@LaunchedEffect
        }
        wordInfo = repository.lookup(query)
    }

    LaunchedEffect(editing, pendingFocus) {
        if (editing && pendingFocus) {
            inputFocus.requestFocus()
            pendingFocus = false
        }
    }

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

    /** 依次朗读单词、每条例句的英文和中文。 */
    fun speakAll(info: WordInfo) {
        val onMessage: (String) -> Unit = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        scope.launch {
            speaker.speakAwait(info.word, Locale.US, settings, onMessage)
            info.examples.forEach { example ->
                speaker.speakAwait(example.english, Locale.US, settings, onMessage)
                example.chinese?.let { speaker.speakAwait(it, Locale.CHINA, settings, onMessage) }
            }
        }
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("译文", text))
        Toast.makeText(context, "已复制译文", Toast.LENGTH_SHORT).show()
    }

    fun translate(text: String) {
        if (text.isEmpty()) return
        wordQuery = null
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
                editing = false
            }
        }
    }

    /** 单个英文单词走词典查词，其余文本走双向翻译。 */
    fun submit(text: String) {
        if (text.isEmpty()) return
        if (singleWordRegex.matches(text)) {
            result = null
            wordInfo = null
            wordQuery = text
            lookupSeq++
            editing = false
        } else {
            translate(text)
        }
    }

    fun clearSelection() {
        inputField = inputField.copy(selection = TextRange(input.length))
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
                when (tab) {
                    HomeTab.SHELF -> onOpenShelf()
                    HomeTab.MORE -> onOpenMore()
                    HomeTab.TRANSLATE -> Unit
                }
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
            if (editing) {
                // 关掉 Compose 默认的绿色光标手柄，保持输入框干净。
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Color.Transparent,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ),
                ) {
                    OutlinedTextField(
                        value = inputField,
                        onValueChange = { inputField = it },
                        label = { Text("输入内容") },
                        placeholder = { Text("输入中文自动译成英文，输入英文自动译成中文") },
                        minLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocus),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "输入内容 · 点击单词可查词",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            editing = true
                            pendingFocus = true
                            clearSelection()
                        },
                    ) {
                        Text("编辑")
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.padding(14.dp)) {
                        LookupText(text = input, onWordClick = { pickedWord = it })
                    }
                }
            }
            if (editing && selectedText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val singleWord = singleWordRegex.matches(selectedText)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                if (singleWord) {
                                    pickedWord = selectedText
                                    clearSelection()
                                } else {
                                    translate(selectedText)
                                }
                            },
                        ) {
                            Text(if (singleWord) "查词" else "翻译")
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { submit(input.trim()) },
                enabled = input.isNotBlank() && !translating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (translating) "翻译中…" else "翻译")
            }

            val word = wordInfo
            val translated = result
            if (wordQuery != null && word == null) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("查询中…", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (word != null || translated != null) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = when {
                        word != null -> "查词 · 单词释义"
                        direction == TranslateDirection.EN_TO_ZH -> "译文 · 英 → 中"
                        else -> "译文 · 中 → 英 · 点击单词可查词"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ResultCard {
                    if (word != null) {
                        WordCard(word, onSpeak = { text, locale -> speak(text, locale) })
                    } else {
                        LookupText(text = translated.orEmpty(), onWordClick = { pickedWord = it })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { copy(word?.translation ?: word?.word ?: translated.orEmpty()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("复制译文")
                    }
                    FilledTonalButton(
                        onClick = {
                            if (word != null) {
                                speakAll(word)
                            } else {
                                speak(
                                    text = translated.orEmpty(),
                                    locale = if (direction == TranslateDirection.EN_TO_ZH) Locale.CHINA else Locale.US,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (word != null) "全部朗读" else "朗读译文")
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

private val singleWordRegex = Regex("[A-Za-z][A-Za-z'’\\-]*")
private val posTagRegex = Regex("^([A-Za-z]{1,6}\\.)\\s*(.+)$")

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun WordCard(info: WordInfo, onSpeak: (String, Locale) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(info.word, style = MaterialTheme.typography.headlineSmall)
            SpeakIcon { onSpeak(info.word, Locale.US) }
        }

        val phonetics = listOfNotNull(
            info.phoneticUk?.let { "英" to it },
            info.phoneticUs?.let { "美" to it },
        ).ifEmpty {
            listOfNotNull(info.phoneticGeneral?.let { "音标" to it })
        }
        if (phonetics.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                phonetics.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "/${value.trim('/')}/",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val definitions = info.translation?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (definitions.isEmpty()) {
            Text(
                text = "未查到中文释义，请检查网络后重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            definitions.forEach { DefinitionLine(it) }
        }

        if (info.examples.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "例句",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            info.examples.forEach { example ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = example.english,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        SpeakIcon { onSpeak(example.english, Locale.US) }
                    }
                    example.chinese?.let { chinese ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chinese,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            SpeakIcon { onSpeak(chinese, Locale.CHINA) }
                        }
                    }
                }
            }
        }

        if (info.phrases.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "常用短语",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            info.phrases.forEach { phrase ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = phrase.phrase,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = phrase.meaning.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                    SpeakIcon { onSpeak(phrase.phrase, Locale.US) }
                }
            }
        }
    }
}

@Composable
private fun DefinitionLine(line: String) {
    val match = posTagRegex.find(line)
    if (match == null) {
        Text(line, style = MaterialTheme.typography.bodyLarge)
        return
    }
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                text = match.groupValues[1],
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = match.groupValues[2],
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LookupText(
    text: String,
    onWordClick: (String) -> Unit,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 26.sp,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        // 长按交给 SelectionContainer 做选择复制，这里只处理轻点。
                        if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                            return@awaitEachGesture
                        }
                        up.consume()
                        val result = layout ?: return@awaitEachGesture
                        val offset = result.getOffsetForPosition(down.position)
                        val range = wordRangeAt(text, offset) ?: return@awaitEachGesture
                        onWordClick(text.substring(range.first, range.last + 1))
                    }
                },
            onTextLayout = { layout = it },
        )
    }
}
