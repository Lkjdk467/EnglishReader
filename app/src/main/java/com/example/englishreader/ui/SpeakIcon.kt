package com.example.englishreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.englishreader.R

/** 小喇叭：只负责朗读，点击事件在这里被消费，不会传给下方的点词手势。 */
@Composable
internal fun SpeakIcon(onClick: () -> Unit) {
    Icon(
        painter = painterResource(R.drawable.ic_volume_up),
        contentDescription = "朗读",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .size(18.dp),
    )
}
