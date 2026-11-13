package com.example.englishreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.englishreader.data.HomeBackground
import com.example.englishreader.data.ReaderSettings
import com.example.englishreader.data.TtsEngine
import com.example.englishreader.tts.Speaker
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    speaker: Speaker,
    background: HomeBackground,
    onBackgroundChange: (HomeBackground) -> Unit,
    onBack: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val engines by speaker.systemEngines.collectAsState()
    val engineLabel by speaker.engineLabel.collectAsState()

    LaunchedEffect(settings.ttsEngine, settings.systemTtsPackage) {
        speaker.prepare(settings)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (!persisted) {
            Toast.makeText(context, "无法长期保存该图片权限", Toast.LENGTH_SHORT).show()
        }
        releaseBackgroundPermission(context, background.uri)
        onBackgroundChange(background.copy(uri = uri.toString()))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("全局设置") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            ReaderSettingsContent(
                settings = settings,
                onChange = onChange,
                engines = engines,
                engineLabel = engineLabel,
                onPreview = {
                    val started = speaker.speak(Speaker.PREVIEW_TEXT, Locale.US, settings) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    if (!started && settings.ttsEngine == TtsEngine.SYSTEM) {
                        Toast.makeText(context, "语音引擎还没准备好，请稍后再试", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(18.dp))
            Text("主页背景设置", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (background.hasImage) "已启用自定义背景，可点击下方按钮更换" else "当前为默认护眼背景",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("选择背景图")
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("背景透明度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(background.opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = background.opacity,
                onValueChange = { onBackgroundChange(background.copy(opacity = it)) },
                valueRange = 0f..1f,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("卡片透明度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(background.cardAlpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "调低后能看清背景图，文字始终保持深色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = background.cardAlpha,
                onValueChange = { onBackgroundChange(background.copy(cardAlpha = it)) },
                valueRange = 0.2f..1f,
                steps = 15,
            )
            OutlinedButton(
                onClick = {
                    releaseBackgroundPermission(context, background.uri)
                    onBackgroundChange(background.copy(uri = null, opacity = 1f))
                },
                enabled = background.hasImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("恢复默认背景")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSupport)
                    .padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "☕ 支持开发者",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun releaseBackgroundPermission(context: Context, uri: String?) {
    if (uri == null) return
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(uri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
