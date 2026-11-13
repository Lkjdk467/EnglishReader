<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" alt="帧慧读 App 图标">
</p>

<h1 align="center">帧慧读 · EnglishReader</h1>

<p align="center">
  为英语阅读打造的 Android 本地阅读器：点词即查、整句翻译、双语朗读。
</p>

<p align="center">
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
</p>

> 帧慧读把「查词、翻译、朗读、记生词」放进同一个阅读界面，尽量不打断你读英文。

---

## ✨ 核心功能

### 📖 多格式本地阅读

- 支持 **TXT（UTF-8 / GB18030）、EPUB、HTML**，通过系统文件选择器导入。
- 自动记住每本书的阅读位置和进度，下次打开直接回到原处。
- 三种翻页方式：**瀑布流（上下滑动）、平移翻页、覆盖翻页**。
- 阅读页底部常驻进度条与百分比，滚动到末尾自动显示 100%。

### 👆 点读查词

- 点击单词高亮并弹出查词面板，显示**英式 / 美式音标**和**全部词性与释义**。
- 多词性释义按行完整展示，不再只显示一条。
- 长按选中整句，可复制、拖动圆点调整选区、翻译整句。
- 查词面板支持朗读英文 / 中文、一键收藏、复制单词。

### 🌐 双向翻译

- 输入中文自动译成英文，输入英文自动译成中文。
- 默认使用有道网页词典公开接口，**无需 API Key** 即可查词。
- 可选配置百度翻译开放平台；未配置或请求失败时自动回退。
- 整句翻译结果可朗读、复制，并保留在查词面板中。

### 🔊 双语朗读

- **系统 TTS**：可选择 MultiTTS 等第三方语音引擎，支持语速、音调调节和试听。
- **Qwen3-TTS（阿里云百炼）**：Serena、Cherry、Ethan、Chelsie 四种音色。
- 长文本自动按句切分合成，连续播放；失败时回退系统朗读。
- 全文朗读支持暂停 / 继续 / 上一段 / 下一段 / 停止。
- 阅读设置内置**媒体音量滑块**，朗读时可直接调节系统音量。

### 🎨 阅读体验

- 字号 16–32sp，实时预览。
- 三种阅读主题：**护眼米色、纯白、夜间**，状态栏与导航栏自动适配。
- 首页背景图自定义：可选择图片，并调节背景透明度和卡片透明度。
- 毛玻璃卡片、启动页「楠」字书写动画、每日一句。

### 📒 生词本与笔记

- 查词面板一键收藏单词，保存单词、音标、释义到本地 SQLite。
- 生词本支持查看和删除，首页显示收藏数量。
- 内置 **TXT 书写本**，支持中英文输入，一键导出到 `Download/帧慧读/`。

---

## 🛠 技术栈

| 项目 | 使用 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + Compose 页面 |
| 异步 | Kotlin Coroutines |
| 图片 | Coil 3 |
| 毛玻璃 | Haze |
| 本地存储 | SharedPreferences + SQLite |
| 构建 | Gradle Kotlin DSL |

- `minSdk 24`（Android 7.0）
- `compileSdk / targetSdk 37`
- JDK 17

---

## 🚀 编译与运行

### 方式一：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)。
2. 打开 Android Studio，选择 **Open**，打开本项目根目录。
3. 等待 Gradle 同步完成（首次会下载依赖）。
4. 连接手机或启动模拟器，点击 **Run ▶**。

### 方式二：命令行

需要 JDK 17+ 和 Android SDK。Windows 下可直接使用 Android Studio 自带的 JDK：

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd EnglishReader
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
cd EnglishReader
./gradlew assembleDebug
```

生成的 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已开启 USB 调试的手机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> `local.properties` 由 Android Studio 自动生成，已加入 `.gitignore`，不需要上传。

---

## 🔐 API Key 与隐私

- 默认的查词和翻译**不需要任何 Key**。
- 百度翻译、Qwen3-TTS 的 Key 在 App 的「设置」里填写，只保存在手机本地，不会写进项目文件。
- `data/ApiConfig.kt` 中的有道 Key 默认为空。**不要把真实 Key 写进这个文件后提交**；如果本地填写过，提交前请清空。
- 以下文件已被 `.gitignore` 排除，请不要手动强制添加：

```text
local.properties
*.jks / *.keystore / *.p12
keystore.properties
api.properties
secrets.properties
.env
```

- 阅读的图书文件保存在手机本地；查词、翻译和在线朗读时，选中的文本会发送到对应的第三方服务（有道 / 百度 / MyMemory / 阿里云百炼）。

---

## 📁 项目结构

```text
EnglishReader/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/example/englishreader/
│       │   ├── data/        # 图书解析、词典、翻译、设置、生词本
│       │   ├── tts/         # 系统 TTS 与 Qwen3-TTS
│       │   ├── ui/          # Compose 页面与组件
│       │   ├── MainActivity.kt
│       │   └── SplashSound.kt
│       ├── res/             # 图标、主题、启动音乐
│       └── AndroidManifest.xml
├── gradle/                  # Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## 📌 已知限制

- EPUB 和 HTML 目前只提取纯文字，不还原插图、CSS 排版和目录跳转。
- 有道网页词典是免费公开接口，稳定性依赖服务端；对虚词的释义不如正式词典精确。
- Qwen3-TTS 需要有效的阿里云百炼 API Key，语音合成按字符计费。
- 仓库不包含发布签名文件，默认构建的是 debug APK。正式发布请自行配置签名，并妥善保管密钥库。

---

## 💡 说明

这个项目最初是为了「读英文小说时不想反复切到词典 App」而写的。如果它对你的英语阅读有帮助，欢迎点一个 ⭐。
