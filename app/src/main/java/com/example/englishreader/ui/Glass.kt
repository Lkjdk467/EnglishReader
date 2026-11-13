package com.example.englishreader.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** 半透明白色 + 实时背景模糊，做出毛玻璃卡片。 */
fun Modifier.glass(
    state: HazeState,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = Color.White.copy(alpha = 0.62f),
    accent: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    blurRadius: Dp = 24.dp,
): Modifier {
    val tints = buildList {
        add(HazeTint(color))
        if (accent != Color.Unspecified) add(HazeTint(accent))
    }
    val glass = clip(shape).hazeEffect(
        state = state,
        style = HazeStyle(
            backgroundColor = Color.Transparent,
            tints = tints,
            blurRadius = blurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(color),
        ),
    )
    return if (borderColor == Color.Unspecified) glass else glass.border(1.dp, borderColor, shape)
}
