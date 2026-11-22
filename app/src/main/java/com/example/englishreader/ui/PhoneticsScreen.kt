package com.example.englishreader.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.englishreader.data.Phonetic
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.data.consonantPhonetics
import com.example.englishreader.data.vowelPhonetics
import com.example.englishreader.tts.Speaker
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneticsScreen(settings: ReaderSettings, speaker: Speaker, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    fun read(word: String) {
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
                title = { Text("英语音标大全") },
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            phoneticSection("元音 · ${vowelPhonetics.size} 个", vowelPhonetics, ::read)
            phoneticSection("辅音 · ${consonantPhonetics.size} 个", consonantPhonetics, ::read)
        }
    }
}

private fun LazyGridScope.phoneticSection(
    title: String,
    phonetics: List<Phonetic>,
    onSpeak: (String) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
    }
    items(phonetics, key = { it.symbol }) { phonetic ->
        PhoneticCard(phonetic, onSpeak)
    }
}

@Composable
private fun PhoneticCard(phonetic: Phonetic, onSpeak: (String) -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = phonetic.symbol,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(phonetic.word, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = phonetic.meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SpeakIcon { onSpeak(phonetic.word) }
        }
    }
}
