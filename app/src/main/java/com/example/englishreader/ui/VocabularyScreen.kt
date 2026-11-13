package com.example.englishreader.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.data.VocabularyStore
import com.example.englishreader.data.WordInfo
import com.example.englishreader.tts.Speaker
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    vocabulary: VocabularyStore,
    settings: ReaderSettings,
    speaker: Speaker,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var words by remember { mutableStateOf(vocabulary.all()) }

    BackHandler(onBack = onBack)

    DisposableEffect(Unit) {
        onDispose { speaker.stop() }
    }

    fun speak(word: String) {
        val started = speaker.speak(word, Locale.US, settings) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        if (!started && settings.ttsEngine == TtsEngine.SYSTEM) {
            Toast.makeText(context, "语音引擎还没准备好，请稍后再试", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("我的生词本") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (words.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "生词本还是空的\n阅读时点开单词，点“☆ 收藏”就会出现在这里",
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(words, key = { it.word.lowercase() }) { info ->
                    VocabularyCard(
                        info = info,
                        onSpeak = { speak(info.word) },
                        onRemove = {
                            vocabulary.remove(info.word)
                            words = vocabulary.all()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VocabularyCard(info: WordInfo, onSpeak: () -> Unit, onRemove: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = info.word,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSpeak) {
                    Text("🔊", fontSize = 18.sp)
                }
                TextButton(onClick = onRemove) {
                    Text("删除")
                }
            }
            val phonetic = listOfNotNull(
                info.phoneticUk?.let { "英 /${it.trim('/')}/" },
                info.phoneticUs?.let { "美 /${it.trim('/')}/" },
            ).ifEmpty {
                listOfNotNull(info.phoneticGeneral?.let { "/${it.trim('/')}/" })
            }
            if (phonetic.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = phonetic.joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!info.translation.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = info.translation,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
