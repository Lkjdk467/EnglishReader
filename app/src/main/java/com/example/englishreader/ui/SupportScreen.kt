package com.example.englishreader.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.englishreader.R
import java.io.File

private val SupportTabs = listOf("微信", "支付宝")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }

    fun saveCurrentQr() {
        val wechat = tab == 0
        val saved = saveQrToGallery(
            context = context,
            resId = if (wechat) R.drawable.wechat_qr else R.drawable.alipay_qr,
            fileName = if (wechat) "wechat_qr.jpg" else "alipay_qr.jpg",
        )
        Toast.makeText(
            context,
            if (saved) "保存成功" else "保存失败，请稍后重试",
            Toast.LENGTH_SHORT,
        ).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            saveCurrentQr()
        } else {
            Toast.makeText(context, "需要存储权限才能保存收款码", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveQr() {
        // Android 10 起写入系统相册不需要存储权限，旧版本才申请。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveCurrentQr()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text("支持开发者", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "如果这个 App 对你有帮助，欢迎请我喝杯咖啡，你的支持是我更新的最大动力！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(28.dp))
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                SupportTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Image(
                    painter = painterResource(
                        if (tab == 0) R.drawable.wechat_qr else R.drawable.alipay_qr,
                    ),
                    contentDescription = if (tab == 0) "微信收款码" else "支付宝收款码",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { saveQr() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("保存收款码", fontSize = 16.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 把 drawable 中的收款码写入系统相册，返回是否成功。 */
private fun saveQrToGallery(context: Context, resId: Int, fileName: String): Boolean {
    val bitmap = context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it) }
        ?: return false

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/EnglishReader",
            )
        } else {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "EnglishReader",
            )
            folder.mkdirs()
            put(MediaStore.Images.Media.DATA, File(folder, fileName).absolutePath)
        }
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    val saved = context.contentResolver.openOutputStream(uri)?.use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
    } ?: false

    if (!saved) {
        runCatching { context.contentResolver.delete(uri, null, null) }
        return false
    }
    // Android 9 及以下依赖媒体扫描才能立即在相册中看到新图片。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
    }
    return true
}
