package com.example.englishreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min

/** 墨色固定为深灰，写完也不变色。 */
private val NanInk = Color(0xFF333333)

/**
 * “楠”字的手写笔顺。坐标基于 1000×1000 画布，按木 4 笔、南 9 笔的标准笔顺排列。
 * 每笔都是中线路径，采样成折线后按压力曲线加粗，写出来自带起笔、行笔、收笔的粗细变化。
 */
private class NanStroke(
    val width: Float,
    val length: Float,
    val points: List<Offset>,
    val pressure: (Float) -> Float,
)

private fun nanStroke(
    width: Float,
    taper: Boolean = false,
    build: Path.() -> Unit,
): NanStroke {
    val path = Path().apply(build)
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    val count = (length / 16f).toInt().coerceIn(12, 96)
    val points = List(count + 1) { index -> measure.getPosition(length * index / count) }
    return NanStroke(width, length, points, if (taper) ::taperPressure else ::brushPressure)
}

/** 起笔略细、行笔最重、收笔出锋。 */
private fun brushPressure(fraction: Float): Float = when {
    fraction < 0.14f -> 0.55f + fraction / 0.14f * 0.45f
    fraction < 0.7f -> 1f
    else -> 1f - (fraction - 0.7f) / 0.3f * 0.45f
}

/** 撇、点这类笔画起笔最重，收笔出锋。 */
private fun taperPressure(fraction: Float): Float = 1f - 0.65f * fraction

private val NanStrokes: List<NanStroke> = listOf(
    // 木：横、竖、撇、点
    nanStroke(34f) { moveTo(84f, 434f); quadraticTo(256f, 416f, 430f, 404f) },
    nanStroke(38f) { moveTo(262f, 238f); lineTo(262f, 716f) },
    nanStroke(42f, taper = true) {
        moveTo(262f, 452f)
        cubicTo(230f, 546f, 182f, 626f, 118f, 700f)
    },
    nanStroke(46f, taper = true) { moveTo(298f, 552f); quadraticTo(346f, 612f, 404f, 692f) },

    // 南：横、竖、竖、横折钩、点、撇、横、横、竖
    nanStroke(32f) { moveTo(522f, 248f); quadraticTo(726f, 226f, 930f, 214f) },
    nanStroke(38f) { moveTo(716f, 138f); lineTo(716f, 332f) },
    nanStroke(38f) { moveTo(512f, 346f); lineTo(518f, 798f) },
    nanStroke(34f) {
        moveTo(492f, 350f)
        quadraticTo(714f, 330f, 926f, 318f)
        lineTo(922f, 772f)
        cubicTo(921f, 802f, 900f, 816f, 866f, 798f)
    },
    nanStroke(44f, taper = true) { moveTo(610f, 452f); quadraticTo(634f, 500f, 658f, 548f) },
    nanStroke(44f, taper = true) { moveTo(824f, 444f); quadraticTo(792f, 500f, 766f, 548f) },
    nanStroke(32f) { moveTo(528f, 650f); quadraticTo(720f, 638f, 918f, 634f) },
    nanStroke(32f) { moveTo(518f, 810f); quadraticTo(720f, 798f, 922f, 794f) },
    nanStroke(38f) { moveTo(712f, 638f); lineTo(712f, 900f) },
)

private val NanTotalLength: Float = NanStrokes.fold(0f) { sum, stroke -> sum + stroke.length }

/** 0 → 1 的书写进度，写满后保持全字。 */
@Composable
fun NanWritingGlyph(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        if (side <= 0f) return@Canvas
        val glyphScale = side / 1000f
        translate(
            left = (size.width - side) / 2f,
            top = (size.height - side) / 2f,
        ) {
            scale(glyphScale, glyphScale, pivot = Offset.Zero) {
                var remaining = NanTotalLength * progress.coerceIn(0f, 1f)
                for (stroke in NanStrokes) {
                    if (remaining <= 0f) return@scale
                    val drawn = min(remaining, stroke.length)
                    remaining -= drawn
                    drawStroke(stroke, drawn, NanInk)
                }
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: NanStroke, drawnLength: Float, color: Color) {
    if (drawnLength <= 0f) return
    val points = stroke.points
    val last = points.lastIndex
    val step = stroke.length / last
    val full = (drawnLength / step).toInt().coerceAtMost(last)
    var previous = points[0]
    for (index in 1..full) {
        val current = points[index]
        val fraction = (index - 0.5f) * step / stroke.length
        drawLine(
            color = color,
            start = previous,
            end = current,
            strokeWidth = stroke.width * stroke.pressure(fraction),
            cap = StrokeCap.Round,
        )
        previous = current
    }
    if (full < last) {
        val t = (drawnLength - full * step) / step
        val current = lerp(points[full], points[full + 1], t.coerceIn(0f, 1f))
        drawLine(
            color = color,
            start = previous,
            end = current,
            strokeWidth = stroke.width * stroke.pressure(drawnLength / stroke.length),
            cap = StrokeCap.Round,
        )
    }
}

private enum class SplashPhase { Writing, Fading, Done }

/**
 * 启动页：1 秒写完“楠”，停留后淡出。
 * onStart 在动画开始时触发（用于播放配乐），onDismiss 在写完、跳过或销毁时触发（用于释放音频）。
 */
@Composable
fun NanSplashOverlay(
    onStart: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var phase by remember { mutableStateOf(SplashPhase.Writing) }
    val fade by animateFloatAsState(
        targetValue = if (phase == SplashPhase.Writing) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "nan-splash-fade",
    )

    fun finish() {
        if (phase != SplashPhase.Writing) return
        phase = SplashPhase.Fading
        onDismiss()
    }

    LaunchedEffect(Unit) { onStart() }

    LaunchedEffect(phase) {
        if (phase == SplashPhase.Fading) {
            delay(300)
            phase = SplashPhase.Done
        }
    }

    DisposableEffect(Unit) {
        onDispose { onDismiss() }
    }

    if (phase == SplashPhase.Done) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fade),
    ) {
        NanSplashScreen(
            onFinish = { finish() },
            onSkip = { finish() },
        )
    }
}

@Composable
private fun NanSplashScreen(onFinish: () -> Unit, onSkip: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val write by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "nan-splash-write",
    )

    LaunchedEffect(Unit) {
        started = true
        delay(1250)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NanWritingGlyph(
                progress = write,
                modifier = Modifier.size(260.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "English Reader",
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(write),
            )
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Text(
                text = "跳过",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
