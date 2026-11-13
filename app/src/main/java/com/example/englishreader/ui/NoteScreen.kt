package com.example.englishreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishreader.data.DictionaryRepository
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.VocabularyStore
import com.example.englishreader.tts.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    settings: ReaderSettings,
    repository: DictionaryRepository,
    speaker: Speaker,
    vocabulary: VocabularyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val text = value.text
    var picked by remember { mutableStateOf<NoteLookup?>(null) }
    var pendingText by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    val selection = value.selection
    val selectedText = if (!selection.collapsed && selection.max <= text.length) {
        text.substring(selection.min, selection.max)
    } else {
        ""
    }
    val trimmedSelection = selectedText.trim()
    val selectionIsWord = trimmedSelection.none { it.isWhitespace() } &&
        trimmedSelection.none { it in '\u4e00'..'\u9fff' }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val content = pendingText
        pendingText = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入文件")
        }.onSuccess {
            Toast.makeText(context, "TXT 已导出", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun clearSelection() {
        value = value.copy(
            text = value.text,
            selection = TextRange(selection.max.coerceIn(0, value.text.length)),
            composition = null,
        )
    }

    fun copySelection() {
        if (selectedText.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("选中文本", selectedText))
        Toast.makeText(context, "已复制选中文本", Toast.LENGTH_SHORT).show()
    }

    fun lookupSelection() {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val raw = text.substring(start, end)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val chinese = trimmed.any { it in '\u4e00'..'\u9fff' }
        val anchor = start + raw.indexOfFirst { !it.isWhitespace() }
        picked = NoteLookup(
            word = if (chinese) trimmed else wordAt(text, anchor) ?: trimmed,
            sentence = if (!chinese && trimmed.any { it.isWhitespace() }) raw else null,
        )
        keyboard?.hide()
    }

    fun requestBack() {
        if (text.isBlank()) onBack() else showDiscard = true
    }

    fun export() {
        if (text.isBlank()) {
            Toast.makeText(context, "还没有内容可以导出", Toast.LENGTH_SHORT).show()
            return
        }
        val content = text
        val fileName = defaultFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { saveToDownloads(context, fileName, content) }
                if (saved) {
                    Toast.makeText(
                        context,
                        "已保存到 Download/帧慧读/$fileName",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    pendingText = content
                    saver.launch(fileName)
                }
            }
        } else {
            pendingText = content
            saver.launch(fileName)
        }
    }

    BackHandler {
        if (text.isBlank()) onBack() else showDiscard = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("TXT 书写") },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { export() }) {
                        Text("导出 TXT")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (text.isEmpty()) {
                            Text(
                                text = "在这里输入文字，中英文都可以…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            if (selectedText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                SelectionBar(
                    count = trimmedSelection.length,
                    actionLabel = if (selectionIsWord) "查词" else "翻译",
                    onCancel = ::clearSelection,
                    onCopy = ::copySelection,
                    onLookup = ::lookupSelection,
                )
            }
            Text(
                text = "${text.length} 字 · 导出后保存在 Download/帧慧读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃这段文字？") },
            text = { Text("返回后这段文字不会保留，需要的话请先导出。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        onBack()
                    },
                ) {
                    Text("放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text("继续写")
                }
            },
        )
    }

    val current = picked
    if (current != null) {
        WordSheet(
            word = current.word,
            sentence = current.sentence,
            settings = settings,
            repository = repository,
            speaker = speaker,
            vocabulary = vocabulary,
            autoTranslate = true,
            onDismiss = { picked = null },
        )
    }
}

private data class NoteLookup(val word: String, val sentence: String?)

@Composable
private fun SelectionBar(
    count: Int,
    actionLabel: String,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onLookup: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "已选中 $count 字",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("取消") }
            TextButton(onClick = onCopy) { Text("复制") }
            FilledTonalButton(onClick = onLookup) { Text(actionLabel) }
        }
    }
}

private fun defaultFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    return "帧慧读_$stamp.txt"
}

/** Android 10+ 直接写入公共 Download/帧慧读，无需存储权限。 */
private fun saveToDownloads(context: Context, fileName: String, content: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        put(
            MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/帧慧读",
        )
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = runCatching {
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }.getOrNull() ?: return false

    return runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入文件")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }.onFailure {
        resolver.delete(uri, null, null)
    }.isSuccess
}
