package com.example.englishreader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.englishreader.data.BookFormat
import com.example.englishreader.data.BookStore
import com.example.englishreader.data.DictionaryRepository
import com.example.englishreader.data.HomeBackgroundStore
import com.example.englishreader.data.ReaderSettingsStore
import com.example.englishreader.data.ReaderTheme
import com.example.englishreader.data.RecentBook
import com.example.englishreader.data.VocabularyStore
import com.example.englishreader.tts.Speaker
import com.example.englishreader.ui.BookshelfScreen
import com.example.englishreader.ui.EnglishReaderTheme
import com.example.englishreader.ui.NanSplashOverlay
import com.example.englishreader.ui.NoteScreen
import com.example.englishreader.ui.ReaderScreen
import com.example.englishreader.ui.SettingsScreen
import com.example.englishreader.ui.SupportScreen
import com.example.englishreader.ui.TranslateScreen
import com.example.englishreader.ui.VocabularyScreen

class MainActivity : ComponentActivity() {
    private var splashSound: SplashSound? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动即预加载开场配乐，动画第一帧只负责播放，不在首帧读文件。
        val sound = SplashSound(this)
        splashSound = sound
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            EnglishReaderTheme {
                Box(Modifier.fillMaxSize()) {
                    EnglishReaderApp()
                    NanSplashOverlay(
                        onStart = { sound.play() },
                        onDismiss = {
                            sound.release()
                            if (splashSound === sound) splashSound = null
                        },
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        splashSound?.pause()
    }

    override fun onResume() {
        super.onResume()
        splashSound?.resume()
    }

    override fun onDestroy() {
        splashSound?.release()
        splashSound = null
        super.onDestroy()
    }
}

private sealed interface Screen {
    data object Shelf : Screen
    data object Translate : Screen
    data object Vocabulary : Screen
    data object Note : Screen
    data object Settings : Screen
    data object Support : Screen
    data class Reader(val book: RecentBook) : Screen
}

@Composable
private fun EnglishReaderApp() {
    val context = LocalContext.current
    val store = remember { BookStore(context) }
    val speaker = remember { Speaker(context) }
    val repository = remember { DictionaryRepository() }
    val settingsStore = remember { ReaderSettingsStore(context) }
    val backgroundStore = remember { HomeBackgroundStore(context) }
    val vocabulary = remember { VocabularyStore(context) }

    var settings by remember { mutableStateOf(settingsStore.load()) }
    var homeBackground by remember { mutableStateOf(backgroundStore.load()) }
    var books by remember { mutableStateOf(store.recentBooks()) }
    var vocabularyCount by remember { mutableStateOf(vocabulary.all().size) }
    var screen by remember { mutableStateOf<Screen>(Screen.Shelf) }

    val view = LocalView.current
    val darkReader = screen is Screen.Reader && settings.theme == ReaderTheme.DARK
    LaunchedEffect(darkReader) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkReader
            isAppearanceLightNavigationBars = !darkReader
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speaker.shutdown()
            vocabulary.close()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val fileName = fileName(context, uri)
        val format = BookFormat.fromFileName(fileName)
        if (format == null) {
            Toast.makeText(context, "仅支持 TXT、EPUB、HTML 文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val book = RecentBook(
            uri = uri.toString(),
            title = bookTitle(fileName),
            position = 0,
            format = format,
        )
        store.touch(book)
        books = store.recentBooks()
        screen = Screen.Reader(book)
    }

    // 在书架和翻译两个 Tab 之间切换时保留翻译页的输入和译文。
    val tabState = rememberSaveableStateHolder()

    when (val current = screen) {
        Screen.Shelf -> tabState.SaveableStateProvider("shelf") {
            BookshelfScreen(
                books = books,
                vocabularyCount = vocabularyCount,
                settings = settings,
                speaker = speaker,
                background = homeBackground,
                onImport = {
                    picker.launch(
                        arrayOf(
                            "text/plain",
                            "application/epub+zip",
                            "text/html",
                            "application/xhtml+xml",
                            "application/octet-stream",
                        ),
                    )
                },
                onOpen = { book ->
                    store.touch(book)
                    books = store.recentBooks()
                    screen = Screen.Reader(book)
                },
                onDelete = { book ->
                    store.remove(book.uri)
                    books = store.recentBooks()
                },
                onOpenVocabulary = {
                    vocabularyCount = vocabulary.all().size
                    screen = Screen.Vocabulary
                },
                onOpenNote = { screen = Screen.Note },
                onOpenSettings = { screen = Screen.Settings },
                onOpenTranslate = { screen = Screen.Translate },
            )
        }

        Screen.Translate -> tabState.SaveableStateProvider("translate") {
            TranslateScreen(
                settings = settings,
                repository = repository,
                speaker = speaker,
                vocabulary = vocabulary,
                onOpenShelf = { screen = Screen.Shelf },
            )
        }

        Screen.Settings -> SettingsScreen(
            settings = settings,
            onChange = {
                settings = it
                settingsStore.save(it)
            },
            speaker = speaker,
            background = homeBackground,
            onBackgroundChange = {
                homeBackground = it
                backgroundStore.save(it)
            },
            onBack = { screen = Screen.Shelf },
            onOpenSupport = { screen = Screen.Support },
        )

        Screen.Support -> SupportScreen(onBack = { screen = Screen.Settings })

        Screen.Note -> NoteScreen(
            settings = settings,
            repository = repository,
            speaker = speaker,
            vocabulary = vocabulary,
            onBack = { screen = Screen.Shelf },
        )

        Screen.Vocabulary -> VocabularyScreen(
            vocabulary = vocabulary,
            settings = settings,
            speaker = speaker,
            onBack = {
                vocabularyCount = vocabulary.all().size
                screen = Screen.Shelf
            },
        )

        is Screen.Reader -> ReaderScreen(
            book = current.book,
            repository = repository,
            speaker = speaker,
            settings = settings,
            vocabulary = vocabulary,
            onSettingsChange = {
                settings = it
                settingsStore.save(it)
            },
            onBack = {
                books = store.recentBooks()
                vocabularyCount = vocabulary.all().size
                screen = Screen.Shelf
            },
            onSavePosition = { position, progress ->
                store.updatePosition(current.book.uri, position, progress)
            },
        )
    }
}

private fun fileName(context: Context, uri: Uri): String {
    val name = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    return name?.takeIf { it.isNotBlank() } ?: "未命名"
}

private fun bookTitle(fileName: String): String =
    fileName.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: fileName

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
