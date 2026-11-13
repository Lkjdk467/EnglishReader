package com.example.englishreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishreader.R
import com.example.englishreader.data.DictionaryRepository
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.data.VocabularyStore
import com.example.englishreader.data.WordInfo
import com.example.englishreader.data.detectTranslateDirection
import com.example.englishreader.tts.Speaker
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface LookupUi {
    data object Loading : LookupUi
    data class Done(val info: WordInfo) : LookupUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(
    word: String,
    sentence: String?,
    settings: ReaderSettings,
    repository: DictionaryRepository,
    speaker: Speaker,
    vocabulary: VocabularyStore,
    onDismiss: () -> Unit,
    onAdjustSelection: (() -> Unit)? = null,
    autoTranslate: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.72f
    var ui by remember(word) { mutableStateOf<LookupUi>(LookupUi.Loading) }
    var favorited by remember(word) { mutableStateOf(vocabulary.contains(word)) }
    var translating by remember(word, sentence) { mutableStateOf(false) }
    var sentenceTranslation by remember(word, sentence) { mutableStateOf<String?>(null) }
    val speechState by speaker.state.collectAsState()

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copy(text: String, toast: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("english-reader", text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    fun read(text: String, locale: Locale) {
        val started = speaker.speak(text, locale, settings) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        if (!started && settings.ttsEngine == TtsEngine.SYSTEM) {
            Toast.makeText(context, "语音引擎还没准备好，请稍后再试", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { speaker.stop() }
    }

    val showSentence = sentence != null && sentence.trim().length > word.length + 2

    suspend fun translateSentence(text: String) {
        translating = true
        val result = repository.translate(text, settings, detectTranslateDirection(text))
        translating = false
        if (result == null) {
            Toast.makeText(context, "翻译失败，请检查网络", Toast.LENGTH_SHORT).show()
        } else {
            sentenceTranslation = result
        }
    }

    LaunchedEffect(word) {
        ui = LookupUi.Done(
            if (word.any { it in '\u4e00'..'\u9fff' }) {
                WordInfo(
                    word = word,
                    translation = repository.translate(word, settings, detectTranslateDirection(word)),
                )
            } else {
                repository.lookup(word, settings)
            },
        )
    }

    LaunchedEffect(word, sentence) {
        if (autoTranslate && showSentence) translateSentence(sentence)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.3f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { copy(word, "已复制") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = "复制单词",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        val info = (ui as? LookupUi.Done)?.info
                        if (favorited) {
                            vocabulary.remove(word)
                            favorited = false
                            Toast.makeText(context, "已从生词本移除", Toast.LENGTH_SHORT).show()
                        } else if (info != null) {
                            vocabulary.save(info.copy(word = word))
                            favorited = true
                            Toast.makeText(context, "已收藏到生词本", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = ui is LookupUi.Done,
                ) {
                    Text(
                        text = if (favorited) "★" else "☆",
                        fontSize = 20.sp,
                        color = if (favorited) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            when (val state = ui) {
                LookupUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("查询中…", style = MaterialTheme.typography.bodyMedium)
                }

                is LookupUi.Done -> {
                    val info = state.info
                    val translation = info.translation?.trim()?.takeIf { it.isNotEmpty() }
                    val phonetics = listOfNotNull(
                        info.phoneticUk?.let { "英" to it },
                        info.phoneticUs?.let { "美" to it },
                    ).ifEmpty {
                        listOfNotNull(info.phoneticGeneral?.let { "音标" to it })
                    }
                    if (phonetics.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            phonetics.forEach { (label, value) -> PhoneticRow(label, value) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeakChip("朗读英文") { read(word, Locale.US) }
                        SpeakChip(
                            text = "朗读中文",
                            enabled = translation != null,
                            onClick = { translation?.let { read(it, Locale.CHINA) } },
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    if (translation == null) {
                        Text(
                            text = "未查到中文释义，请检查网络后重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            translation.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodyLarge)
                                }
                        }
                    }
                }
            }

            StopSpeechButton(speechState) { speaker.stop() }

            if (showSentence) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "选中内容",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(sentence, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { copy(sentence, "已复制选中文本") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("复制选中文本")
                    }
                    if (onAdjustSelection != null) {
                        OutlinedButton(
                            onClick = onAdjustSelection,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("调整选区")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { scope.launch { translateSentence(sentence) } },
                    enabled = !translating && sentenceTranslation == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (translating) "翻译中…" else "翻译整句")
                }

                val translation = sentenceTranslation
                if (translation != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "译文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(translation, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeakChip("朗读原文") { read(sentence, Locale.US) }
                        SpeakChip("朗读译文") { read(translation, Locale.CHINA) }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { copy(translation, "已复制译文") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("复制译文")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneticRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = "/${value.trim('/')}/",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SpeakChip(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_volume_up),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}