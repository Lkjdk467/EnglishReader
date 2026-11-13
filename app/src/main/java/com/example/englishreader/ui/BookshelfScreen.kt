package com.example.englishreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.englishreader.R
import com.example.englishreader.data.BookFormat
import com.example.englishreader.data.HomeBackground
import com.example.englishreader.data.RecentBook
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.data.dailyQuoteIndex
import com.example.englishreader.data.dailyQuotes
import com.example.englishreader.tts.SpeechState
import com.example.englishreader.tts.Speaker
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    books: List<RecentBook>,
    vocabularyCount: Int,
    settings: ReaderSettings,
    speaker: Speaker,
    background: HomeBackground,
    onImport: () -> Unit,
    onOpen: (RecentBook) -> Unit,
    onDelete: (RecentBook) -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTranslate: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    var menuBook by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { speaker.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (background.hasImage) {
            AsyncImage(
                model = background.uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentScale = ContentScale.Crop,
                alpha = background.opacity,
            )
            // 图片上方覆盖一层浅色遮罩，保证标题和正文始终清晰可读。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f * background.opacity)),
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerState = drawerState) {
                    Column(Modifier.fillMaxHeight()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
                        ) {
                            Text("阅读历史", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (books.isEmpty()) "还没有阅读记录" else "共 ${books.size} 本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (books.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "导入图书后，阅读记录会出现在这里",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(books, key = { it.uri }) { book ->
                                    HistoryRow(
                                        book = book,
                                        menuOpen = menuBook == book.uri,
                                        onOpen = {
                                            menuBook = null
                                            scope.launch { drawerState.close() }
                                            onOpen(book)
                                        },
                                        onMenu = { menuBook = book.uri },
                                        onDismissMenu = { menuBook = null },
                                        onDelete = {
                                            menuBook = null
                                            onDelete(book)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text("我的书架") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "阅读历史")
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "全局设置")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                    )
                },
                bottomBar = {
                    HomeBottomBar(selected = HomeTab.SHELF, hazeState = hazeState) { tab ->
                        if (tab == HomeTab.TRANSLATE) onOpenTranslate()
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    DailyQuoteCard(
                        settings = settings,
                        speaker = speaker,
                        hazeState = hazeState,
                        cardAlpha = background.cardAlpha,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .glass(
                                state = hazeState,
                                shape = RoundedCornerShape(28.dp),
                                color = Color.White.copy(alpha = background.cardAlpha),
                                accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            ),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    ) {
                        Text("导入本地文件 (TXT/EPUB/HTML)", fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenVocabulary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .glass(
                                state = hazeState,
                                shape = RoundedCornerShape(26.dp),
                                color = Color.White.copy(alpha = background.cardAlpha),
                            ),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Text(
                            text = if (vocabularyCount > 0) "★ 我的生词本（$vocabularyCount）" else "☆ 我的生词本",
                            fontSize = 15.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .glass(
                                state = hazeState,
                                shape = RoundedCornerShape(26.dp),
                                color = Color.White.copy(alpha = background.cardAlpha),
                            ),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Text("✎ 新建 TXT 书写本", fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(24.dp))
                    if (books.isEmpty()) {
                        Text(
                            text = "书架还是空的。导入 TXT、EPUB 或 HTML 图书后，阅读记录会收进左上角菜单。",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("最近阅读", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            books.forEach { book ->
                                HistoryRow(
                                    book = book,
                                    hazeState = hazeState,
                                    cardAlpha = background.cardAlpha,
                                    menuOpen = menuBook == book.uri,
                                    onOpen = {
                                        menuBook = null
                                        onOpen(book)
                                    },
                                    onMenu = { menuBook = book.uri },
                                    onDismissMenu = { menuBook = null },
                                    onDelete = {
                                        menuBook = null
                                        onDelete(book)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DailyQuoteCard(
    settings: ReaderSettings,
    speaker: Speaker,
    hazeState: HazeState,
    cardAlpha: Float,
) {
    val context = LocalContext.current
    val today = remember { Calendar.getInstance() }
    val dateLabel = remember {
        SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date(today.timeInMillis))
    }
    val baseIndex = remember { today.get(Calendar.DAY_OF_YEAR) }
    var offset by rememberSaveable { mutableStateOf(0) }
    val quote = dailyQuotes[dailyQuoteIndex(baseIndex, offset)]
    val speechState by speaker.state.collectAsState()

    fun copyEnglish() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("每日一句", quote.english))
        Toast.makeText(context, "已复制英文原句", Toast.LENGTH_SHORT).show()
    }

    fun speak() {
        val started = speaker.speak(quote.english, Locale.US, settings) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        if (!started && settings.ttsEngine == TtsEngine.SYSTEM) {
            Toast.makeText(context, "语音引擎还没准备好，请稍后再试", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(
                state = hazeState,
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = cardAlpha),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "每日一句",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = quote.english,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
            lineHeight = 26.sp,
            modifier = Modifier.clickable(onClick = ::copyEnglish),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = quote.chinese,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "— ${quote.author}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "点击英文可复制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = ::speak, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_up),
                    contentDescription = "朗读每日一句",
                    modifier = Modifier.size(20.dp),
                    tint = if (speechState == SpeechState.SPEAKING) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TextButton(onClick = { offset += 1 }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("换一换", fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    book: RecentBook,
    hazeState: HazeState? = null,
    cardAlpha: Float = 0.62f,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onDelete: () -> Unit,
) {
    val container = if (hazeState != null) {
        Modifier.glass(
            state = hazeState,
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = cardAlpha),
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
    } else {
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(container)
            .combinedClickable(onClick = onOpen, onLongClick = onMenu)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatBadge(book.format)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = book.progressLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text("删除本书") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun FormatBadge(format: BookFormat) {
    Text(
        text = format.label,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun RecentBook.progressLabel(): String {
    val percent = (progress * 100).roundToInt().coerceIn(0, 100)
    return when {
        percent <= 0 && position == 0 -> "未开始阅读"
        percent <= 0 -> "继续阅读"
        else -> "已读 $percent%"
    }
}
