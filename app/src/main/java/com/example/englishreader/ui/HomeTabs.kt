package com.example.englishreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

enum class HomeTab { SHELF, TRANSLATE }

@Composable
fun HomeBottomBar(selected: HomeTab, hazeState: HazeState?, onSelect: (HomeTab) -> Unit) {
    val barColor = Color.White.copy(alpha = 0.58f)
    val barModifier = if (hazeState != null) {
        Modifier.glass(
            state = hazeState,
            shape = RectangleShape,
            color = barColor,
            blurRadius = 28.dp,
        )
    } else {
        Modifier.background(barColor)
    }
    Column(barModifier) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = selected == HomeTab.SHELF,
                onClick = { onSelect(HomeTab.SHELF) },
                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                label = { Text("书架") },
            )
            NavigationBarItem(
                selected = selected == HomeTab.TRANSLATE,
                onClick = { onSelect(HomeTab.TRANSLATE) },
                icon = { Icon(Icons.Filled.Create, contentDescription = null) },
                label = { Text("翻译") },
            )
        }
    }
}
