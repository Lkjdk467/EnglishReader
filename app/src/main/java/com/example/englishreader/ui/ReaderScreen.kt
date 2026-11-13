package com.example.englishreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.englishreader.data.DictionaryRepository
import com.example.englishreader.data.ReaderPageMode
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.ReaderTheme
import com.example.englishreader.data.RecentBook
import com.example.englishreader.data.VocabularyStore
import com.example.englishreader.data.loadBookText
import com.example.englishreader.data.toParagraphs
import com.example.englishreader.tts.Speaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val SentenceEndings = charArrayOf('.', '!', '?', '…', '\n')

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val highlight: Color,
    val divider: Color,
    val accent: Color,
)

fun readerPalette(theme: ReaderTheme): ReaderPalette = when (theme) {
    ReaderTheme.BEIGE -> ReaderPalette(
        background = Color(0xFFF7F0DE),
        text = Color(0xFF2E2A22),
        secondaryText = Color(0xFF7A7466),
        highlight = Color(0x66EBC98A),
        divider = Color(0x33A08C5A),
        accent = Color(0xFF7B8F4E),
    )
    ReaderTheme.WHITE -> ReaderPalette(
        background = Color(0xFFFFFFFF),
        text = Color(0xFF1B1B1B),
        secondaryText = Color(0xFF6A6A6A),
        highlight = Color(0x66FFE08A),
        divider = Color(0x33000000),
        accent = Color(0xFF4C7A5A),
    )
    ReaderTheme.DARK -> ReaderPalette(
        background = Color(0xFF14161A),
        text = Color(0xFFE4E1D8),
        secondaryText = Color(0xFF9A9A94),
        highlight = Color(0x66B08B3A),
        divider = Color(0x33FFFFFF),
        accent = Color(0xFF9DBF7A),
    )
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val text: String) : LoadState
    data class Failed(val message: String) : LoadState
}

private data class PickedText(
    val paragraph: Int,
    val word: String,
    val sentence: String?,
    val range: IntRange,
)

/** 自定义选区，范围是 [start, end)，仅限同一段落。 */
private data class TextSelection(
    val paragraph: Int,
    val start: Int,
    val end: Int,
    val word: String,
)

/** 全文朗读队列中的一段，范围是段落内的 [start, end)。 */
private data class SpeechSegment(
    val paragraph: Int,
    val start: Int,
    val end: Int,
    val text: String,
) {
    val range: IntRange get() = start until end
}

private const val SPEECH_SEGMENT_LIMIT = 400
private val SpeechSentenceMarks = charArrayOf('.', '!', '?', ';', '…')

/** 按段落切分朗读队列，超长段落再按句末标点切段，避免单次 TTS 输入过长。 */
private fun buildSpeechSegments(paragraphs: List<String>): List<SpeechSegment> {
    val segments = ArrayList<SpeechSegment>()
    paragraphs.forEachIndexed { index, paragraph ->
        var start = 0
        while (start < paragraph.length) {
            while (start < paragraph.length && paragraph[start].isWhitespace()) start++
            if (start >= paragraph.length) break

            var end = (start + SPEECH_SEGMENT_LIMIT).coerceAtMost(paragraph.length)
            if (end < paragraph.length) {
                val floor = start + SPEECH_SEGMENT_LIMIT / 2
                var cut = -1
                for (i in end - 1 downTo floor) {
                    if (paragraph[i] in SpeechSentenceMarks) {
                        cut = i + 1
                        break
                    }
                }
                if (cut <= start) {
                    val space = paragraph.lastIndexOf(' ', end)
                    cut = if (space > start) space else end
                }
                end = cut
            }

            var trimmed = end
            while (trimmed > start && paragraph[trimmed - 1].isWhitespace()) trimmed--
            if (trimmed > start) {
                segments.add(SpeechSegment(index, start, trimmed, paragraph.substring(start, trimmed)))
            }
            start = end.coerceAtLeast(start + 1)
        }
    }
    return segments
}

private fun copyText(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: RecentBook,
    repository: DictionaryRepository,
    speaker: Speaker,
    settings: ReaderSettings,
    vocabulary: VocabularyStore,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
    onSavePosition: (Int, Float) -> Unit,
) {
    val context = LocalContext.current
    val palette = readerPalette(settings.theme)
    var state by remember(book.uri) { mutableStateOf<LoadState>(LoadState.Loading) }
    var picked by remember { mutableStateOf<PickedText?>(null) }
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var position by remember(book.uri) { mutableStateOf(book.position) }

    val scope = rememberCoroutineScope()
    val readyText = (state as? LoadState.Ready)?.text
    val paragraphs = remember(readyText) { readyText?.let(::toParagraphs).orEmpty() }
    val segments = remember(paragraphs) { buildSpeechSegments(paragraphs) }
    var listening by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var speakIndex by remember { mutableStateOf(0) }
    var listenJob by remember { mutableStateOf<Job?>(null) }

    fun segmentForParagraph(paragraph: Int): Int {
        val found = segments.indexOfFirst { it.paragraph >= paragraph }
        return if (found >= 0) found else segments.lastIndex
    }

    fun startListening(from: Int) {
        if (segments.isEmpty()) return
        val target = from.coerceIn(0, segments.lastIndex)
        listenJob?.cancel()
        speaker.stop()
        speakIndex = target
        listening = true
        paused = false
        var notified = false
        listenJob = scope.launch {
            var index = target
            while (index < segments.size && listening) {
                speakIndex = index
                val started = speaker.speakAwait(
                    text = segments[index].text,
                    locale = Locale.US,
                    settings = settings,
                ) { message ->
                    if (!notified) {
                        notified = true
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
                if (!started) {
                    if (!notified) {
                        Toast.makeText(context, "语音引擎不可用，请检查系统朗读设置", Toast.LENGTH_LONG).show()
                    }
                    listening = false
                    paused = false
                    break
                }
                if (!listening) break
                index++
            }
            if (listening) {
                listening = false
                paused = false
            }
            listenJob = null
        }
    }

    fun pauseListening() {
        if (!listening || paused) return
        paused = true
        listenJob?.cancel()
        listenJob = null
        speaker.stop()
    }

    fun stopListening() {
        if (!listening && listenJob == null) return
        listening = false
        paused = false
        speakIndex = 0
        listenJob?.cancel()
        listenJob = null
        speaker.stop()
    }

    fun skipSegment(step: Int) {
        if (!listening || segments.isEmpty()) return
        val target = (speakIndex + step).coerceIn(0, segments.lastIndex)
        if (target == speakIndex) return
        if (paused) {
            speakIndex = target
        } else {
            startListening(target)
        }
    }

    fun toggleListening() {
        when {
            !listening -> startListening(segmentForParagraph(position))
            paused -> startListening(speakIndex)
            else -> pauseListening()
        }
    }

    LaunchedEffect(book.uri) {
        state = runCatching { loadBookText(context, Uri.parse(book.uri), book.format) }
            .fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Failed(it.message ?: "无法打开这本电子书") },
            )
    }

    DisposableEffect(Unit) {
        onDispose {
            listenJob?.cancel()
            speaker.stop()
        }
    }

    BackHandler {
        when {
            listening -> stopListening()
            selection != null -> selection = null
            else -> onBack()
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        stopListening()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { toggleListening() },
                        enabled = segments.isNotEmpty(),
                    ) {
                        Text("朗读")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "阅读设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.text,
                    navigationIconContentColor = palette.text,
                    actionIconContentColor = palette.text,
                ),
            )
        },
        bottomBar = {
            if (listening) {
                ListenBar(
                    index = speakIndex,
                    total = segments.size,
                    paused = paused,
                    palette = palette,
                    onPrevious = { skipSegment(-1) },
                    onToggle = { if (paused) startListening(speakIndex) else pauseListening() },
                    onNext = { skipSegment(1) },
                    onStop = { stopListening() },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                LoadState.Loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp),
                    color = palette.accent,
                    strokeWidth = 3.dp,
                )

                is LoadState.Failed -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(current.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) { Text("返回书架") }
                }

                is LoadState.Ready -> ReaderBody(
                    paragraphs = paragraphs,
                    initialPosition = position,
                    settings = settings,
                    palette = palette,
                    picked = picked,
                    selection = selection,
                    listening = listening,
                    spoken = if (listening) segments.getOrNull(speakIndex) else null,
                    onPositionChange = { position = it },
                    onSavePosition = { index, progress ->
                        position = index
                        onSavePosition(index, progress)
                    },
                    onWord = { paragraph, word, range ->
                        stopListening()
                        picked = PickedText(paragraph, word, null, range)
                    },
                    onSentence = { paragraph, word, sentence, range ->
                        stopListening()
                        picked = PickedText(paragraph, word, sentence, range)
                    },
                    onSelectionChange = {
                        stopListening()
                        selection = it
                    },
                    onSelectionCancel = { selection = null },
                    onSelectionCopy = { text ->
                        copyText(context, text, "选中文本")
                        selection = null
                    },
                    onSelectionDone = { currentSelection, selectedText, word ->
                        selection = null
                        picked = PickedText(
                            paragraph = currentSelection.paragraph,
                            word = word,
                            sentence = selectedText,
                            range = currentSelection.start until currentSelection.end,
                        )
                    },
                )
            }
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            onChange = onSettingsChange,
            speaker = speaker,
            onDismiss = { showSettings = false },
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
            onDismiss = { picked = null },
            onAdjustSelection = if (current.sentence != null) {
                {
                    selection = TextSelection(
                        paragraph = current.paragraph,
                        start = current.range.first,
                        end = current.range.last + 1,
                        word = current.word,
                    )
                    picked = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun ReaderBody(
    paragraphs: List<String>,
    initialPosition: Int,
    settings: ReaderSettings,
    palette: ReaderPalette,
    picked: PickedText?,
    selection: TextSelection?,
    listening: Boolean,
    spoken: SpeechSegment?,
    onPositionChange: (Int) -> Unit,
    onSavePosition: (Int, Float) -> Unit,
    onWord: (Int, String, IntRange) -> Unit,
    onSentence: (Int, String, String, IntRange) -> Unit,
    onSelectionChange: (TextSelection) -> Unit,
    onSelectionCancel: () -> Unit,
    onSelectionCopy: (String) -> Unit,
    onSelectionDone: (TextSelection, String, String) -> Unit,
) {
    if (paragraphs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(palette.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("这个文件是空的", color = palette.text)
        }
        return
    }

    when (settings.pageMode) {
        ReaderPageMode.SCROLL -> ScrollReader(
            paragraphs = paragraphs,
            initialPosition = initialPosition,
            settings = settings,
            palette = palette,
            picked = picked,
            selection = selection,
            listening = listening,
            spoken = spoken,
            onPositionChange = onPositionChange,
            onSavePosition = onSavePosition,
            onWord = onWord,
            onSentence = onSentence,
            onSelectionChange = onSelectionChange,
            onSelectionCancel = onSelectionCancel,
            onSelectionCopy = onSelectionCopy,
            onSelectionDone = onSelectionDone,
        )

        ReaderPageMode.SLIDE,
        ReaderPageMode.COVER -> PagedReader(
            paragraphs = paragraphs,
            initialPosition = initialPosition,
            settings = settings,
            palette = palette,
            picked = picked,
            selection = selection,
            listening = listening,
            spoken = spoken,
            onPositionChange = onPositionChange,
            onSavePosition = onSavePosition,
            onWord = onWord,
            onSentence = onSentence,
            onSelectionChange = onSelectionChange,
            onSelectionCancel = onSelectionCancel,
            onSelectionCopy = onSelectionCopy,
            onSelectionDone = onSelectionDone,
        )
    }
}

@Composable
private fun ScrollReader(
    paragraphs: List<String>,
    initialPosition: Int,
    settings: ReaderSettings,
    palette: ReaderPalette,
    picked: PickedText?,
    selection: TextSelection?,
    listening: Boolean,
    spoken: SpeechSegment?,
    onPositionChange: (Int) -> Unit,
    onSavePosition: (Int, Float) -> Unit,
    onWord: (Int, String, IntRange) -> Unit,
    onSentence: (Int, String, String, IntRange) -> Unit,
    onSelectionChange: (TextSelection) -> Unit,
    onSelectionCancel: () -> Unit,
    onSelectionCopy: (String) -> Unit,
    onSelectionDone: (TextSelection, String, String) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPosition.coerceIn(0, paragraphs.lastIndex),
    )

    // 按字符数加权，并叠加当前段落内的滚动比例，让进度和实际阅读量一致。
    val charOffsets = remember(paragraphs) { paragraphCharOffsets(paragraphs) }

    val progress by remember(listState, charOffsets) {
        derivedStateOf {
            val total = charOffsets.last()
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (total <= 0 || first == null) {
                0f
            } else if (!listState.canScrollForward) {
                1f
            } else {
                val index = first.index.coerceIn(0, paragraphs.lastIndex)
                val size = first.size.coerceAtLeast(1)
                val scrolled = (-first.offset).toFloat().coerceIn(0f, size.toFloat()) / size
                val paragraphChars = (charOffsets[index + 1] - charOffsets[index]).toFloat()
                ((charOffsets[index] + scrolled * paragraphChars) / total).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { index ->
                onPositionChange(index)
                delay(400)
                onSavePosition(index, progress)
            }
    }

    DisposableEffect(listState) {
        onDispose { onSavePosition(listState.firstVisibleItemIndex, progress) }
    }

    // 朗读到新段落时把该段带进视野，长段落后半段再按比例滚到当前句子。
    LaunchedEffect(listening, spoken) {
        val segment = spoken ?: return@LaunchedEffect
        if (!listening) return@LaunchedEffect
        listState.animateScrollToItem(segment.paragraph)
        val paragraph = paragraphs.getOrNull(segment.paragraph) ?: return@LaunchedEffect
        if (segment.start <= 0 || paragraph.isEmpty()) return@LaunchedEffect
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == segment.paragraph } ?: return@LaunchedEffect
        listState.scrollBy(item.size * (segment.start.toFloat() / paragraph.length))
    }

    // 拖动进度条时用 seekProgress 覆盖真实进度，松手后恢复为滚动位置计算值。
    var seekProgress by remember { mutableStateOf<Float?>(null) }
    val shownProgress = seekProgress ?: progress
    val seekScope = rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<Job?>(null) }

    fun seekTo(value: Float) {
        seekProgress = value
        seekJob?.cancel()
        val total = charOffsets.last()
        if (total <= 0) return
        val target = (value.coerceIn(0f, 1f) * total).roundToInt()
        val index = indexForProgress(value, charOffsets)
        val paragraphChars = charOffsets[index + 1] - charOffsets[index]
        val fraction = ((target - charOffsets[index]).toFloat() / paragraphChars).coerceIn(0f, 1f)
        seekJob = seekScope.launch {
            listState.scrollToItem(index)
            // 超长段落只跳到段首会回弹，按字符比例继续滚到段内对应位置。
            if (fraction > 0f) {
                val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size
                if (itemSize != null) listState.scrollBy(fraction * itemSize)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = 20.dp,
                bottom = if (selection != null) 132.dp else 64.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(paragraphs.size) { index ->
                val paragraph = paragraphs[index]
                if (paragraph.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                } else {
                    val editing = selection?.takeIf { it.paragraph == index }
                    val highlight = when {
                        editing != null -> editing.start until editing.end
                        picked?.paragraph == index -> picked.range
                        spoken?.paragraph == index -> spoken.range
                        else -> null
                    }
                    TappableParagraph(
                        text = paragraph,
                        fontSize = settings.fontSizeSp.sp,
                        palette = palette,
                        highlight = highlight,
                        selection = editing,
                        onWord = { word, range -> onWord(index, word, range) },
                        onSentence = { word, sentence, range -> onSentence(index, word, sentence, range) },
                        onSelectionChange = { start, end ->
                            onSelectionChange(TextSelection(index, start, end, editing?.word.orEmpty()))
                        },
                    )
                }
            }
        }

        val activeSelection = selection
        if (activeSelection != null && activeSelection.paragraph in paragraphs.indices) {
            SelectionActions(
                paragraphText = paragraphs[activeSelection.paragraph],
                selection = activeSelection,
                palette = palette,
                modifier = Modifier.align(Alignment.BottomCenter),
                onCancel = onSelectionCancel,
                onCopy = onSelectionCopy,
                onDone = onSelectionDone,
            )
        } else {
            if (!listening) {
                ReaderProgressBar(
                    progress = shownProgress,
                    palette = palette,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onSeek = ::seekTo,
                    onSeekFinished = { seekProgress = null },
                )
            }
        }
    }
}

@Composable
private fun PagedReader(
    paragraphs: List<String>,
    initialPosition: Int,
    settings: ReaderSettings,
    palette: ReaderPalette,
    picked: PickedText?,
    selection: TextSelection?,
    listening: Boolean,
    spoken: SpeechSegment?,
    onPositionChange: (Int) -> Unit,
    onSavePosition: (Int, Float) -> Unit,
    onWord: (Int, String, IntRange) -> Unit,
    onSentence: (Int, String, String, IntRange) -> Unit,
    onSelectionChange: (TextSelection) -> Unit,
    onSelectionCancel: () -> Unit,
    onSelectionCopy: (String) -> Unit,
    onSelectionDone: (TextSelection, String, String) -> Unit,
) {
    val charOffsets = remember(paragraphs) { paragraphCharOffsets(paragraphs) }
    val totalChars = charOffsets.last()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 必须沿用正文实际使用的 LocalTextStyle，否则字距不同会让测量和渲染换行不一致。
    val localTextStyle = LocalTextStyle.current
    val textStyle = remember(localTextStyle, settings.fontSizeSp) {
        localTextStyle.copy(
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * 1.6f).sp,
        )
    }
    val spacingPx = with(density) { 10.dp.roundToPx() }
    val blankSpacingPx = with(density) { 16.dp.roundToPx() }

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val contentWidth = with(density) { (maxWidth - 44.dp).roundToPx().coerceAtLeast(1) }
        val contentHeight = with(density) { (maxHeight - 84.dp).roundToPx().coerceAtLeast(1) }
        val pages = remember(paragraphs, contentWidth, contentHeight, textStyle) {
            paginate(
                paragraphs = paragraphs,
                measurer = measurer,
                style = textStyle,
                widthPx = contentWidth,
                heightPx = contentHeight,
                spacingPx = spacingPx,
                blankSpacingPx = blankSpacingPx,
            )
        }
        if (pages.isEmpty()) return@BoxWithConstraints

        val pageStarts = remember(pages, charOffsets) {
            IntArray(pages.size) { index ->
                val first = pages[index].chunks.firstOrNull()
                if (first == null) 0 else charOffsets[first.paragraphIndex] + first.start
            }
        }
        val initialPage = remember(pages, initialPosition) {
            pageIndexForParagraph(pages, initialPosition)
        }
        val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }

        val progress by remember(pagerState, pageStarts, totalChars) {
            derivedStateOf {
                val index = pagerState.currentPage.coerceIn(0, pageStarts.lastIndex)
                when {
                    totalChars <= 0 -> 0f
                    // 最后一页按页首算会停在 90% 多，和瀑布流读到末尾的 100% 不一致。
                    index == pageStarts.lastIndex -> 1f
                    else -> (pageStarts[index].toFloat() / totalChars).coerceIn(0f, 1f)
                }
            }
        }

        LaunchedEffect(pages.size) {
            val last = pages.lastIndex
            if (last >= 0 && pagerState.currentPage > last) pagerState.scrollToPage(last)
        }

        LaunchedEffect(pagerState, pages) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collectLatest { index ->
                    val page = pages.getOrNull(index) ?: return@collectLatest
                    val paragraph = page.chunks.firstOrNull()?.paragraphIndex ?: 0
                    onPositionChange(paragraph)
                    delay(400)
                    onSavePosition(paragraph, pageFraction(pageStarts, totalChars, index))
                }
        }

        DisposableEffect(pages) {
            onDispose {
                val page = pages.getOrNull(pagerState.currentPage)
                if (page != null) {
                    val paragraph = page.chunks.firstOrNull()?.paragraphIndex ?: 0
                    onSavePosition(paragraph, pageFraction(pageStarts, totalChars, pagerState.currentPage))
                }
            }
        }

        // 分页模式下朗读到新段落时自动翻到包含当前句子的页面。
        LaunchedEffect(listening, spoken, pageStarts) {
            val segment = spoken ?: return@LaunchedEffect
            if (!listening || pageStarts.isEmpty()) return@LaunchedEffect
            val target = charOffsets[segment.paragraph] + segment.start
            val index = pageForChar(pageStarts, target)
            if (pagerState.currentPage != index) pagerState.animateScrollToPage(index)
        }

        var seekProgress by remember { mutableStateOf<Float?>(null) }
        val shownProgress = seekProgress ?: progress
        val scope = rememberCoroutineScope()

        fun seekTo(value: Float) {
            seekProgress = value
            if (totalChars <= 0) return
            val target = (value.coerceIn(0f, 1f) * totalChars).roundToInt()
            val index = pageForChar(pageStarts, target)
            scope.launch { pagerState.scrollToPage(index) }
        }

        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { index ->
                val page = pages.getOrNull(index) ?: return@HorizontalPager
                val pageOffset = (index - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                val cover = settings.pageMode == ReaderPageMode.COVER
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(index.toFloat())
                        .graphicsLayer {
                            if (cover) {
                                translationX = if (pageOffset < 0f) -pageOffset * size.width else 0f
                            }
                        },
                ) {
                    Box(Modifier.fillMaxSize().background(palette.background))
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 64.dp),
                    ) {
                        PageContent(
                            page = page,
                            fontSize = settings.fontSizeSp.sp,
                            palette = palette,
                            picked = picked,
                            selection = selection,
                            spoken = spoken,
                            onWord = onWord,
                            onSentence = onSentence,
                            onSelectionChange = onSelectionChange,
                        )
                    }
                    if (cover && pageOffset > 0.001f) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxHeight()
                                .width(14.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        0f to Color.Black.copy(alpha = 0.22f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                    }
                }
            }

            val activeSelection = selection
            if (activeSelection != null && activeSelection.paragraph in paragraphs.indices) {
                SelectionActions(
                    paragraphText = paragraphs[activeSelection.paragraph],
                    selection = activeSelection,
                    palette = palette,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onCancel = onSelectionCancel,
                    onCopy = onSelectionCopy,
                    onDone = onSelectionDone,
                )
            } else {
                if (!listening) {
                    ReaderProgressBar(
                        progress = shownProgress,
                        palette = palette,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onSeek = ::seekTo,
                        onSeekFinished = { seekProgress = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun PageContent(
    page: ReaderPage,
    fontSize: TextUnit,
    palette: ReaderPalette,
    picked: PickedText?,
    selection: TextSelection?,
    spoken: SpeechSegment?,
    onWord: (Int, String, IntRange) -> Unit,
    onSentence: (Int, String, String, IntRange) -> Unit,
    onSelectionChange: (TextSelection) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        page.chunks.forEach { chunk ->
            val editing = selection?.let { current ->
                val start = (current.start - chunk.start).coerceIn(0, chunk.text.length)
                val end = (current.end - chunk.start).coerceIn(start, chunk.text.length)
                if (current.paragraph != chunk.paragraphIndex || end <= start) {
                    null
                } else {
                    TextSelection(current.paragraph, start, end, current.word)
                }
            }
            val highlight = when {
                editing != null -> editing.start until editing.end
                picked?.paragraph == chunk.paragraphIndex -> chunkRange(picked.range, chunk)
                spoken?.paragraph == chunk.paragraphIndex -> chunkRange(spoken.range, chunk)
                else -> null
            }
            TappableParagraph(
                text = chunk.text,
                fontSize = fontSize,
                palette = palette,
                highlight = highlight,
                selection = editing,
                onWord = { word, range -> onWord(chunk.paragraphIndex, word, range.shift(chunk.start)) },
                onSentence = { word, sentence, range ->
                    onSentence(chunk.paragraphIndex, word, sentence, range.shift(chunk.start))
                },
                onSelectionChange = { start, end ->
                    onSelectionChange(
                        TextSelection(
                            paragraph = chunk.paragraphIndex,
                            start = start + chunk.start,
                            end = end + chunk.start,
                            word = editing?.word.orEmpty(),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SelectionActions(
    paragraphText: String,
    selection: TextSelection,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onCopy: (String) -> Unit,
    onDone: (TextSelection, String, String) -> Unit,
) {
    val start = selection.start.coerceIn(0, paragraphText.length)
    val end = selection.end.coerceIn(start, paragraphText.length)
    val selectedText = paragraphText.substring(start, end)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.97f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "拖动两侧圆点调整选区",
            color = palette.secondaryText,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("取消", maxLines = 1)
            }
            Button(
                onClick = { onCopy(selectedText) },
                enabled = selectedText.isNotBlank(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("复制选中", maxLines = 1)
            }
            FilledTonalButton(
                onClick = {
                    val word = wordAt(paragraphText, start) ?: selection.word
                    onDone(selection.copy(start = start, end = end), selectedText, word)
                },
                enabled = selectedText.isNotBlank(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("完成", maxLines = 1)
            }
        }
    }
}

@Composable
private fun ListenBar(
    index: Int,
    total: Int,
    paused: Boolean,
    palette: ReaderPalette,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (paused) {
                "已暂停 · 第 ${index + 1} / $total 段"
            } else {
                "正在朗读 · 第 ${index + 1} / $total 段"
            },
            color = palette.secondaryText,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = index > 0,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("上一段", maxLines = 1, fontSize = 13.sp)
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(if (paused) "继续" else "暂停", maxLines = 1, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onNext,
                enabled = index < total - 1,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("下一段", maxLines = 1, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("停止", maxLines = 1, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ReaderProgressBar(
    progress: Float,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .padding(horizontal = 32.dp, vertical = 4.dp),
    ) {
        // 吞掉进度条空白处的点击，避免穿透到正文触发查词或选区。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { } },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = progress,
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.divider,
                ),
            )
            Text(
                text = "${(progress * 100).roundToInt()}%",
                color = palette.secondaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp),
            )
        }
    }
}

private data class PageChunk(
    val paragraphIndex: Int,
    val start: Int,
    val text: String,
)

private data class ReaderPage(val chunks: List<PageChunk>)

private fun paragraphCharOffsets(paragraphs: List<String>): IntArray {
    val offsets = IntArray(paragraphs.size + 1)
    var total = 0
    paragraphs.forEachIndexed { index, paragraph ->
        offsets[index] = total
        total += paragraph.length + 1
    }
    offsets[paragraphs.size] = total
    return offsets
}

private fun paginate(
    paragraphs: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    spacingPx: Int,
    blankSpacingPx: Int,
): List<ReaderPage> {
    if (widthPx <= 0 || heightPx <= 0) return emptyList()
    val pages = mutableListOf<ReaderPage>()
    var chunks = mutableListOf<PageChunk>()
    var used = 0f

    fun flush() {
        if (chunks.isEmpty()) return
        pages += ReaderPage(chunks.toList())
        chunks = mutableListOf()
        used = 0f
    }

    paragraphs.forEachIndexed { index, source ->
        val paragraph = source.trimEnd()
        if (paragraph.isBlank()) {
            if (chunks.isNotEmpty()) used = (used + blankSpacingPx).coerceAtMost(heightPx.toFloat())
            return@forEachIndexed
        }

        val layout = measurer.measure(
            text = AnnotatedString(paragraph),
            style = style,
            constraints = Constraints(maxWidth = widthPx),
        )
        var line = 0
        while (line < layout.lineCount) {
            val gap = if (chunks.isNotEmpty()) spacingPx else 0
            val available = heightPx - used - gap
            val baseTop = layout.getLineTop(line)
            var last = line - 1
            while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - baseTop <= available) last++
            if (last < line) {
                if (chunks.isNotEmpty()) {
                    flush()
                    continue
                }
                last = line
            }

            val startChar = layout.getLineStart(line)
            val endChar = layout.getLineEnd(last, visibleEnd = true)
            if (endChar <= startChar) {
                line = last + 1
                continue
            }

            if (chunks.isNotEmpty()) used += spacingPx
            chunks += PageChunk(
                paragraphIndex = index,
                start = startChar,
                text = paragraph.substring(startChar, endChar).trimEnd(),
            )
            used += layout.getLineBottom(last) - baseTop
            line = last + 1
            if (line < layout.lineCount) flush()
        }
    }

    flush()
    if (pages.isEmpty() && paragraphs.any { it.isNotBlank() }) {
        pages += ReaderPage(emptyList())
    }
    return pages
}

private fun pageIndexForParagraph(pages: List<ReaderPage>, paragraph: Int): Int {
    if (pages.isEmpty()) return 0
    val found = pages.indexOfFirst { page ->
        (page.chunks.lastOrNull()?.paragraphIndex ?: -1) >= paragraph
    }
    return if (found >= 0) found else pages.lastIndex
}

private fun pageForChar(pageStarts: IntArray, target: Int): Int {
    var low = 0
    var high = pageStarts.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) / 2
        if (pageStarts[mid] <= target) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun pageFraction(pageStarts: IntArray, totalChars: Int, index: Int): Float {
    if (totalChars <= 0) return 0f
    if (index >= pageStarts.lastIndex) return 1f
    val start = pageStarts.getOrElse(index) { 0 }
    return (start.toFloat() / totalChars).coerceIn(0f, 1f)
}

private fun chunkRange(range: IntRange, chunk: PageChunk): IntRange? {
    val start = range.first - chunk.start
    val end = range.last - chunk.start
    if (end < 0 || start > chunk.text.lastIndex) return null
    return start.coerceAtLeast(0)..end.coerceAtMost(chunk.text.lastIndex)
}

private fun IntRange.shift(delta: Int): IntRange = (first + delta)..(last + delta)

/** 把 0f–1f 的进度换算成段落下标，charOffsets 按累计字符数升序排列。 */
private fun indexForProgress(value: Float, charOffsets: IntArray): Int {
    if (charOffsets.size < 2) return 0
    val total = charOffsets.last()
    if (total <= 0) return 0
    val target = (value.coerceIn(0f, 1f) * total).roundToInt()
    val found = charOffsets.binarySearch(target)
    val index = if (found >= 0) found else -found - 2
    return index.coerceIn(0, charOffsets.size - 2)
}

private enum class SelectionEdge { START, END }

@Composable
private fun TappableParagraph(
    text: String,
    fontSize: TextUnit,
    palette: ReaderPalette,
    highlight: IntRange?,
    selection: TextSelection?,
    onWord: (String, IntRange) -> Unit,
    onSentence: (String, String, IntRange) -> Unit,
    onSelectionChange: (Int, Int) -> Unit,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val content = remember(text, highlight, palette) {
        if (highlight == null) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                val start = highlight.first.coerceIn(0, text.length)
                val end = (highlight.last + 1).coerceIn(start, text.length)
                if (start < end) addStyle(SpanStyle(background = palette.highlight), start, end)
            }
        }
    }
    val currentSelection by rememberUpdatedState(selection)
    val editing = selection != null

    Box(Modifier.fillMaxWidth()) {
        Text(
            text = content,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text, editing) {
                    if (editing) {
                        var edge = SelectionEdge.START
                        detectDragGestures(
                            onDragStart = { position ->
                                val result = layout ?: return@detectDragGestures
                                val current = currentSelection ?: return@detectDragGestures
                                val (startPoint, endPoint) =
                                    handlePoints(result, text, current.start, current.end)
                                edge = if (
                                    (position - startPoint).getDistanceSquared() <=
                                    (position - endPoint).getDistanceSquared()
                                ) SelectionEdge.START else SelectionEdge.END
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val result = layout ?: return@detectDragGestures
                                val current = currentSelection ?: return@detectDragGestures
                                val offset = result.getOffsetForPosition(change.position)
                                when (edge) {
                                    SelectionEdge.START -> onSelectionChange(
                                        offset.coerceIn(0, current.end - 1),
                                        current.end,
                                    )
                                    SelectionEdge.END -> onSelectionChange(
                                        current.start,
                                        offset.coerceIn(current.start + 1, text.length),
                                    )
                                }
                            },
                        )
                    } else {
                        detectTapGestures(
                            onTap = { position ->
                                val result = layout ?: return@detectTapGestures
                                val offset = result.getOffsetForPosition(position)
                                val range = wordRangeAt(text, offset) ?: return@detectTapGestures
                                onWord(text.substring(range.first, range.last + 1), range)
                            },
                            onLongPress = { position ->
                                val result = layout ?: return@detectTapGestures
                                val offset = result.getOffsetForPosition(position)
                                val range = sentenceRangeAt(text, offset) ?: return@detectTapGestures
                                val sentence = text.substring(range.first, range.last + 1).trim()
                                if (sentence.isEmpty()) return@detectTapGestures
                                val word = (
                                    wordRangeAt(text, offset)
                                        ?: wordRangeAt(text, range.first)
                                    )?.let { text.substring(it.first, it.last + 1) }.orEmpty()
                                onSentence(word, sentence, range)
                            },
                        )
                    }
                },
            color = palette.text,
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.6f).sp,
            onTextLayout = { layout = it },
        )

        val current = selection
        val result = layout
        if (current != null && result != null && text.isNotEmpty()) {
            val (startPoint, endPoint) = handlePoints(result, text, current.start, current.end)
            SelectionHandle(startPoint, palette.accent)
            SelectionHandle(endPoint, palette.accent)
        }
    }
}

private fun handlePoints(
    layout: TextLayoutResult,
    text: String,
    start: Int,
    end: Int,
): Pair<Offset, Offset> {
    val startBox = layout.getBoundingBox(start.coerceIn(0, text.lastIndex))
    val endBox = layout.getBoundingBox((end - 1).coerceIn(0, text.lastIndex))
    return Offset(startBox.left, startBox.bottom) to Offset(endBox.right, endBox.bottom)
}

@Composable
private fun SelectionHandle(point: Offset, color: Color) {
    val dot = 14.dp
    val dotPx = with(LocalDensity.current) { dot.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (point.x - dotPx / 2f).roundToInt(),
                    (point.y - dotPx / 2f).roundToInt(),
                )
            }
            .size(dot)
            .clip(CircleShape)
            .background(color),
    )
}

private val wordRegex = Regex("[A-Za-z][A-Za-z'’\\-]*")

internal fun wordRangeAt(text: String, offset: Int): IntRange? {
    if (text.isEmpty()) return null
    val center = offset.coerceIn(0, text.lastIndex)
    val start = (center - 64).coerceAtLeast(0)
    val end = (center + 64).coerceAtMost(text.length)
    val match = wordRegex.findAll(text.substring(start, end)).firstOrNull {
        center in (it.range.first + start)..(it.range.last + start + 1)
    } ?: return null

    var first = match.range.first + start
    var last = match.range.last + start
    while (last > first && (text[last] == '\'' || text[last] == '’' || text[last] == '-')) last--
    return first..last
}

internal fun wordAt(text: String, offset: Int): String? {
    val range = wordRangeAt(text, offset) ?: return null
    return text.substring(range.first, range.last + 1)
}

internal fun sentenceRangeAt(text: String, offset: Int): IntRange? {
    if (text.isEmpty()) return null
    val center = offset.coerceIn(0, text.lastIndex)

    var start = 0
    for (i in center - 1 downTo 0) {
        if (text[i] in SentenceEndings) {
            start = i + 1
            break
        }
    }

    var end = text.length
    for (i in center until text.length) {
        if (text[i] in SentenceEndings) {
            end = i + 1
            break
        }
    }
    while (end < text.length && (text[end] == '"' || text[end] == '”' || text[end] == '’')) end++

    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    if (start >= end) return null
    return start until end
}
