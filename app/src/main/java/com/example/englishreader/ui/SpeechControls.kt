package com.example.englishreader.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.englishreader.tts.SpeechState

@Composable
fun StopSpeechButton(state: SpeechState, onStop: () -> Unit) {
    if (state == SpeechState.IDLE) return
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state == SpeechState.LOADING) "■ 停止朗读（准备中…）" else "■ 停止朗读")
    }
}
