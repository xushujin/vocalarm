# Vocalarm

用你自己的声音叫醒自己。原生 Android 语音闹钟：录音铃声、文字转语音、多套拟态配色、锁屏响铃、GitHub 应用内更新。

- 应用名：**Vocalarm**
- 包名：`com.vocalarm.app`（debug 为 `com.vocalarm.app.debug`）
- 最低系统：Android 8.0（API 26）
- 编译 / 目标 SDK：API 37（Android 17）
- 当前版本：由构建参数 `vocalarm.versionName` / `vocalarm.versionCode` 注入；未注入时为 `0.1.0` / `1`

---

## 1. 功能对照

| # | 需求 | 实现位置 | 说明 |
|---|------|----------|------|
| 1 | 自定义闹钟时间 | `ui/screens/AlarmEditScreen.kt`、`ui/neu/WheelPicker.kt` | 24 小时制滚轮，分钟精度 1 分钟 |
| 2 | 录制自己的声音作为铃声 | `audio/VoiceRecorder.kt`、`data/AlarmRepository.kt` | MediaRecorder，AAC/MP4，单声道 44.1kHz |
| 3 | 文字转语音 | `audio/TtsEngine.kt`、`domain/TtsTextSplitter.kt` | 系统 TTS；长文本按句切块，避免引擎截断 |
| 4 | 录音试听 / 暂停 / 重录 | `audio/AudioPlayer.kt`、`ui/vm/AlarmEditViewModel.kt` | MediaPlayer 暂停续播；重录成功后才替换旧文件 |
| 5 | 输入文字后试听合成语音 | `ui/vm/AlarmEditViewModel.kt#toggleSpeechPreview` | 与响铃共用同一个 TtsEngine |
| 6 | 男声 / 女声等音色 | `domain/VoiceCatalog.kt`、`audio/TtsEngine.kt` | 预设 + 系统音色列表；按名称/特性推断性别，找不到时回退默认并明确提示 |
| 7 | 播放速度 | `AlarmEditScreen.kt`（0.5×–2.0×）、`TtsEngine.setSpeechRate` | 保存到闹钟配置 |
| 8 | 一次 / 指定次数 / 持续循环 | `domain/LoopPolicy.kt`、`alarm/AlarmPlaybackEngine.kt` | 轮次语义已单测 |
| 9 | 每天 / 工作日 / 周末 / 自定义 | `domain/AlarmTime.kt`、`domain/VersionComparator.kt#RepeatPlan` | 自定义星期逐日切换 |
| 10 | 稍后提醒 5 / 10 分钟 | `alarm/AlarmService.kt`、`ui/ring/AlarmRingScreen.kt` | 独立 PendingIntent；不影响下一次重复闹钟 |
| 11 | 自定义音量 + 渐强 | `domain/LoopPolicy.kt#FadeGain`、`AudioPlayer`、`AlarmService` | MediaPlayer 实时增益；TTS 通过音量参数，是否生效取决于引擎 |
| 12 | 闹钟增删改启停 | `data/AlarmRepository.kt`、`ui/screens/AlarmListScreen.kt` | 列表开关、编辑页删除 |
| 13 | 先录音再文字语音 | `alarm/AlarmPlaybackEngine.kt` | 每轮顺序：录音 → 语音；两者可只配其一 |
| 14 | 锁屏 / 后台正常触发 | `alarm/AlarmScheduler.kt`、`AlarmService.kt`、`AlarmReceiver.kt`、`BootReceiver.kt` | `setAlarmClock` + 前台服务 + 全屏通知（优先）＋ 兜底亮屏 + CPU/屏幕唤醒锁 + 开机恢复 |
| 15 | 本地存储、无需账号 | `data/JsonStore.kt`、`AlarmRepository.kt`、`SettingsRepository.kt`、`RecordingStore.kt` | 原子写 JSON + 本地 m4a；无网络账号 |

界面：全部组件自绘（双阴影拟态、自绘图标、滚轮、开关、滑杆、输入框），只有 Compose 官方 `foundation` / `material3` 的文本与主题骨架，没有第三方 UI 库或 DI 框架。应用图标与页内标识共用「柔光表盘 + V 形指针」语言，自适应图标、单色图标与 Android 12 启动屏保持一致。

---

## 2. 技术栈与工程结构

| 项 | 版本 / 说明 |
|----|-------------|
| 语言 | Kotlin 2.4.21（AGP 9 内置 Kotlin，不再单独应用 `kotlin-android`） |
| UI | Jetpack Compose，Compose BOM `2026.09.00`（Compose 1.12.x） |
| 构建 | AGP 9.5.0，Gradle 9.8.1，JDK 21 |
| SDK | compileSdk 37 / targetSdk 37 / minSdk 26 |
| 架构 | 单 Activity + Compose 状态驱动；手写 `AppContainer` 注入；无 Hilt/Koin |
| 存储 | 自研 JSON 文件（原子替换）+ 显式 schema 迁移 |
| 更新 | GitHub Releases REST API + 前台下载服务 + PackageInstaller |

```
app/src/main/java/com/vocalarm/app/
├── VocalarmApp.kt / AppContainer.kt      # 进程级单例，无 DI 框架
├── domain/                               # 纯 Kotlin，可单测
│   ├── Alarm.kt / AlarmTime.kt
│   ├── LoopPolicy.kt（循环 + 渐强）
│   ├── VersionComparator.kt（版本比较 + 重复计划）
│   ├── TtsTextSplitter.kt / VoiceCatalog.kt
│   └── StoreIssue.kt
├── data/                                 # JSON 存储、迁移、仓库、录音文件
├── audio/                                # MediaRecorder / MediaPlayer / TTS
├── alarm/                                # 调度、广播、前台服务、播放引擎
├── update/                               # 检查、下载、APK 校验、安装
└── ui/
    ├── MainActivity.kt / AppRoot.kt
    ├── theme/                            # 5 套拟态配色 + 字体
    ├── neu/                              # 拟态绘制、自绘图标、滚轮、控件
    ├── screens/                          # 列表 / 编辑 / 外观 / 关于
    ├── ring/                             # 全屏响铃
    └── vm/                               # 手写 ViewModel 工厂
```

---

## 3. 本地构建

前置：JDK 21、Android SDK Platform 37、Build-Tools 36.0.0（AGP 9.5 默认）。

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=$HOME/Library/Android/sdk

./gradlew test            # 59 个单元测试
./gradlew assembleDebug   # 调试包：app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 不是必需项；Android Studio 会自动生成。`keystore.properties` 仅 release 签名时需要。

---

## 4. 签名：两条通道

`app/build.gradle.kts` 先读 `keystore.properties`，再读环境变量；两者字段一一对应。

| keystore.properties | CI 环境变量 | 必填 | 示例 |
|---------------------|-------------|------|------|
| `storeFile` | `ANDROID_KEYSTORE_FILE` | 是 | `/Users/me/vocalarm-release.jks` |
| `storePassword` | `ANDROID_KEYSTORE_PASSWORD` | 是 | 生成 keystore 时设置的 store 密码 |
| `keyAlias` | `ANDROID_KEY_ALIAS` | 是 | `vocalarm` |
| `keyPassword` | `ANDROID_KEY_PASSWORD` | 是 | 生成 keystore 时设置的 key 密码 |

生成密钥（只做一次，务必备份）：

```bash
keytool -genkeypair -v \
  -keystore vocalarm-release.jks \
  -alias vocalarm -keyalg RSA -keysize 2048 -validity 10000
```

本地构建 release：

```bash
cp keystore.properties.example keystore.properties
# 填入真实路径与密码；该文件已被 .gitignore 排除
./gradlew assembleRelease \
  -Pvocalarm.versionName=1.0.0 \
  -Pvocalarm.versionCode=10000 \
  -Pvocalarm.githubOwner=<你的 GitHub 用户名> \
  -Pvocalarm.githubRepo=<仓库名>
```

> `keystore.properties`、`*.jks` 已在 `.gitignore` 中；不要提交签名材料。

---

## 5. CI：只有推送 tag 才构建并发版

工作流：`.github/workflows/release.yml`

```yaml
on:
  push:
    tags:
      - "v*"
```

分支推送与 PR 不会触发构建；只有 `v*` tag 会执行「测试 → 签名打包 → 校验签名 → 创建 GitHub Release」。

### 5.1 发布步骤

```bash
git tag v1.0.0
git push origin v1.0.0
```

版本映射：`v1.2.3` → versionName `1.2.3`，versionCode `10203`（`major*10000 + minor*100 + patch`）。tag 必须严格为 `vX.Y.Z`，minor/patch ≤ 99，否则工作流直接失败。

工作流会：

1. 用 JDK 21 与 Gradle 缓存构建；
2. 跑 `./gradlew test`；
3. 用 Secrets 中的 keystore 构建 `assembleRelease`，并自动注入 `github.repository_owner` / `github.event.repository.name`；
4. 产物重命名为 `vocalarm-<version>.apk`（应用内更新按此名称找资产）；
5. `apksigner verify` 校验签名；
6. 上传 `mapping-<version>` 构建产物（用于还原 R8 崩溃堆栈）；
7. 创建 Release 并上传 APK。

### 5.2 仓库需要配置的 Actions 参数（照此填写）

进入仓库 **Settings → Secrets and variables → Actions → New repository secret**，添加 4 个 **Repository secrets**：

| Secret 名称 | 必填 | 值 |
|-------------|------|-----|
| `ANDROID_KEYSTORE_BASE64` | 是 | 你的 `vocalarm-release.jks` 的 Base64 单行文本 |
| `ANDROID_KEYSTORE_PASSWORD` | 是 | keystore 的 store 密码 |
| `ANDROID_KEY_ALIAS` | 是 | `vocalarm`（或你创建时用的别名） |
| `ANDROID_KEY_PASSWORD` | 是 | 该 alias 对应的 key 密码 |

生成 Base64（任选其一）：

```bash
# macOS
base64 -i vocalarm-release.jks | tr -d '\n' | pbcopy
# Linux
base64 -w0 vocalarm-release.jks
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("vocalarm-release.jks")) | Set-Clipboard
```

还需要：

| 仓库设置 | 值 |
|----------|-----|
| Settings → Actions → General → Workflow permissions | **Read and write permissions**（工作流也声明了 `contents: write`） |
| Settings → Secrets and variables → Actions → Variables | 不需要配置任何变量 |
| 仓库可见性 | **Public**（应用内更新直接读公开 Releases API；私有仓库需要额外 token，本项目未实现） |

`vocalarm.githubOwner` / `vocalarm.githubRepo` 不需要手工配置：CI 会从 `github.repository_owner` 与 `github.event.repository.name` 自动注入。本地构建若不传这两个参数，关于页会显示「此构建未配置更新源」。

---

## 6. 应用内更新

入口：**关于 → 应用更新**。

- 检查：`GET https://api.github.com/repos/<owner>/<repo>/releases/latest`，取 `tag_name` 与第一个 `.apk` 资产；
- 版本比较：语义化版本（支持 `v` 前缀、预发布、构建元数据），有单测；
- 下载：前台服务 + 通知进度（百分比、已下载/总量），可在通知或页面取消；
- 校验：下载完成后用 `PackageManager.getPackageArchiveInfo` 读取真实 APK，校验包名一致且 `versionCode` 大于当前版本，否则删除并报错；
- 安装：`PackageInstaller` 会话安装，失败时回退到 `FileProvider + ACTION_VIEW`；
- 自动检查：进入关于页且距上次检查超过 6 小时时触发一次，也可手动点「检查更新」。

注意：

- 只有 **release 包**能自我更新；debug 包名是 `com.vocalarm.app.debug`，会因包名不一致被拒绝（这是有意的保护）。
- 新旧 APK 必须使用同一签名密钥。
- 首次安装需要用户允许「安装未知应用」。
- Release 中必须存在 `vocalarm-<version>.apk` 资产；CI 已自动处理。

---

## 7. 数据与迁移

| 文件 | 内容 |
|------|------|
| `filesDir/alarms.json` | `{"schemaVersion":3,"alarms":[...]}` |
| `filesDir/settings.json` | `{"schemaVersion":2,"paletteId":"mist","lastUpdateCheckAt":0}` |
| `filesDir/recordings/<uuid>.m4a` | 录音本体，JSON 只存文件名 |
| `cacheDir/updates/*.apk` | 已下载待安装的更新包 |

闹钟字段（schema 3）包括：`id / hour / minute / label / enabled / repeatDays / recording / text / voicePreset / voiceName / speechRate / loopMode / loopCount / volume / fadeIn / fadeSeconds / snoozeMinutes / createdAt / updatedAt`。

迁移规则：

- 闹钟 schema 链：`1 → 2 → 3`，每一步都有显式迁移函数（v2 增加文字/音色/语速；v3 增加标签/循环/音量/渐强/稍后提醒/时间戳）；
- 设置 schema：`1 → 2`（增加 `lastUpdateCheckAt`）；
- 文件版本**高于**当前应用：不覆盖、不重建，转为只读并提示；
- 文件损坏：移动到 `*.corrupt-<时间戳>.bak` 后以空数据启动，原文件保留；
- 单条记录损坏：跳过该条并计数提示，其余记录保留；
- 写入：临时文件 + `fsync` + 原子替换。

真实夹具（不是内联字符串）：

- `app/src/test/resources/fixtures/alarms_v1.json`
- `app/src/test/resources/fixtures/alarms_v2.json`
- `app/src/test/resources/fixtures/settings_v1.json`

---

## 8. 权限

| 权限 | 用途 |
|------|------|
| `RECORD_AUDIO` | 录制闹钟铃声（运行时申请） |
| `POST_NOTIFICATIONS` | 响铃全屏通知、下载进度（Android 13+ 运行时申请） |
| `SCHEDULE_EXACT_ALARM`（≤32）/ `USE_EXACT_ALARM` | 精确触发闹钟 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 锁屏/后台响铃 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 后台下载更新 |
| `USE_FULL_SCREEN_INTENT` | 锁屏全屏响铃。Android 14+ 属于「特殊应用权限 → 全屏通知」，被系统或厂商关闭时系统会把闹钟降级成通知横幅，首页会给出提示并可直接跳到该设置页 |
| `WAKE_LOCK` | 响铃期间保持 CPU，并点亮/保持屏幕 |
| `RECEIVE_BOOT_COMPLETED` | 重启、改时间、改时区后恢复闹钟 |
| `VIBRATE` | 音频失败时的震动兜底 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 检查与下载更新 |
| `REQUEST_INSTALL_PACKAGES` | 应用内更新安装 |

关于页逐条列出了这些权限及用途。

`USE_FULL_SCREEN_INTENT` 在 Android 14（API 34）及以上是一个可被用户关闭的特殊权限：关闭后 `setFullScreenIntent` 不会启动响铃页，只留下一条横幅。应用启动/回到前台时用 `NotificationManager.canUseFullScreenIntent()` 读取真实状态，未开启时在首页顶部显示「全屏提醒未开启」提示条，点击跳转 `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`。

响铃时的顺序至关重要：**先发全屏通知，再按需点亮屏幕**。系统只在屏幕熄灭或锁屏时才展开全屏 Intent；如果应用先把屏幕点亮，系统会判定设备正在使用中，转而下发一条横幅。因此 `AlarmService` 只在两种情况下自己点亮屏幕——通知权限被关闭、或全屏通知权限未授予——并在全屏通知发出 3 秒后仍未见响铃页、且屏幕仍处于熄灭状态时兜底点亮，避免个别 ROM 静默丢弃全屏 Intent 后毫无反应。

文字转语音通过系统 `TextToSpeech` 完成，因此需要两件事同时成立：

- 设备上装了语音引擎（很多国产 ROM 默认不带 Google TTS，但通常自带讯飞/小爱等引擎），且该引擎已启用；
- 应用能在 Android 11+ 的包可见性规则下“看见”引擎。清单里必须声明 `android.intent.action.TTS_SERVICE` 的 `<queries>`，否则即使手机装了引擎，`TextToSpeech` 初始化也会返回 `ERROR`，界面只会显示「系统语音引擎不可用」。`TtsEngine` 在初始化失败时用 `queryIntentServices` 区分「没有引擎」与「引擎初始化失败」，编辑页据此显示不同提示，并提供直达系统「语音设置」的按钮；用户装好/切换引擎回到应用时，`Lifecycle.ON_RESUME` 会重建引擎，不需要重启应用。

---

## 9. 测试与验证记录

执行环境：macOS，JDK 21.0.2，Android SDK Platform 37，Build-Tools 36.0.0 / 37.0.0。

| 项目 | 命令 | 结果 |
|------|------|------|
| Debug 编译 | `./gradlew clean assembleDebug` | **成功，0 编译警告**（Kotlin `allWarningsAsErrors = true`） |
| 单元测试 | `./gradlew cleanTest test` | **59 个测试，0 失败** |
| Lint | `./gradlew lintRelease` | **0 error，1 warning**（仅提示 Gradle 9.8.2 可升级；`mipmap-anydpi-v26` 的目录提示已按 `app/lint.xml` 说明性豁免） |
| R8 体积 | `./gradlew assembleRelease` | 未混淆 8.28MB → R8 **约 1.5MB**（含图标 PNG；DEX 原始 22.0MB → 2.1MB） |
| R8 运行时保留 | 解析 APK 的 DEX 与 ServiceLoader | Manifest 组件、`MainDispatcherFactory` / `AndroidDispatcherFactory` 均在；服务文件未被错误改写 |
| Release 签名（本地通道） | `./gradlew assembleRelease` + `keystore.properties` | **成功**，`apksigner` 校验 V2 签名通过 |
| Release 元数据 | `aapt2 dump badging` | `versionName=1.2.3`、`versionCode=10203`、包名 `com.vocalarm.app` |
| 清单结构 | `aapt2 dump xmltree` | 两个 Activity、两个前台服务、三个 Receiver、FileProvider 均按预期导出/类型 |
| JSON 解析 | 真实 fixture 文件 + `AlarmJsonCodecTest` / `SettingsJsonCodecTest` | v1/v2 迁移、往返、损坏条目、版本过新全部通过 |
| 全屏响铃（模拟器） | API 36 pixel_6 AVD，写入闹钟后熄屏等待触发 | **通过**：`PowerManagerService` 记录 `Waking up from Asleep … details=com.android.systemui:full_screen_intent`（由系统点亮），`AlarmRingActivity` 全屏置顶并 occlude 锁屏（`mKeyguardOccluded=true`） |
| 进程被杀死后触发（模拟器） | 应用退到桌面后 `kill -9` 进程、熄屏，到点由系统拉起进程 | **通过**：进程冷启动 → 前台服务（`exactAllowReason=policy_permission`）→ 系统点亮屏幕并全屏响铃；响铃页停止后自动关闭，一次性闹钟不再残留次日重响 |
| 全屏通知权限被关闭（模拟器） | `cmd appops set … USE_FULL_SCREEN_INTENT deny` | **通过**：首页出现「全屏提醒未开启」提示条；响铃时由应用自己的 `Vocalarm:RingScreen` 唤醒屏幕并只显示横幅。权限恢复后提示条消失 |
| 亮屏解锁时触发（模拟器） | 屏幕点亮并停留在桌面 | **符合系统设计**：不接管屏幕，显示带「停止 / 稍后 5 分钟 / 稍后 10 分钟」的横幅；点按可进入响铃页 |
| 语音引擎缺失（模拟器） | `pm disable-user com.google.android.tts` 后进入编辑页 | **通过**：语音卡片出现「没有检测到语音引擎…」与「语音设置」按钮；点击进入 `Settings$TextToSpeechSettingsActivity` |
| 语音引擎恢复（模拟器） | 重新 `pm enable` 引擎后返回应用 | **通过**：`ON_RESUME` 重建引擎，提示自动消失；试听触发 `GoogleTTSServiceImpl: Synthesis request …`，无需重启应用 |
| 闹钟自检面板（模拟器） | 关于页 → 闹钟自检 | **通过**：正确显示应用内/系统登记的下一个闹钟、最近收到系统闹钟、精确闹钟/通知/全屏通知/电池优化状态；电池受限时给出「允许后台运行」入口 |
| 一键试响（模拟器） | 自检面板 → 10 秒后试响一次 → 立即锁屏 | **通过**：10 秒后 `AlarmRingActivity` 全屏置顶并点亮屏幕；`trigger.log` 记录 `deliveredAt` 与 `ringingAt`，面板显示「累计收到 1 次」 |

单测覆盖的核心逻辑：

- 下一次触发：每天、工作日、周末、自定义、仅一次、禁用、跨周末、严格晚于当前时刻；
- 循环策略与渐强增益；
- 版本比较（预发布、构建元数据、缺省段）；
- 重复类型判定与星期切换；
- TTS 长文本切块；
- 音色推断与回退；
- JSON 编解码、显式迁移、字段钳制、真实文件夹具、录音垃圾回收。

### 明确未验证的部分

本机没有 Android 真机，以下项目只有模拟器（API 36 google_apis、swiftshader）证据或完全没有运行证据：

- 录音、试听、暂停续播的实际音频链路（模拟器无麦克风输入）；
- 系统 TTS 的中文音色列表与「男声/女声」映射在真机上的结果（模拟器装有 Google TTS，试听可触发真实合成请求 `Synthesis request for locale zho-CHN`，但不同厂商引擎的音色与特性差异无法覆盖）；引擎缺失时的提示与恢复路径已单独验证；
- 厂商 ROM（MIUI / HyperOS、EMUI、ColorOS）对全屏 Intent、后台弹出、锁屏显示的额外拦截；
- 开机恢复、改时区恢复；
- 应用内更新的真实下载、进度通知与 PackageInstaller 安装；
- 拟态视觉、滚动、主题切换在真机上的实际渲染效果。

此外：

- **CI 环境变量签名通道**：本地只完整验证了 `keystore.properties` 通道；CI 使用同一段读取逻辑，但首次打 tag 前没有实际执行过（本地环境变量方式因审批超时未运行，未伪造结果）。
- **二进制录音格式**：没有设备就无法生成真实 m4a 进行解码验证；APK 这一二进制格式已用 `aapt2` / `apksigner` 真实解析验证。

---

## 10. 已知限制与设计取舍

- **屏幕亮着时的全屏**：Android 10 起禁止后台启动 Activity，全屏闹钟只能由「全屏 Intent」拉起；而系统只在屏幕熄灭或锁屏时展开全屏 Intent，屏幕亮着且已解锁时按设计只显示带操作按钮的横幅。系统自带时钟是特权应用，第三方应用无法完全复刻这一行为。Vocalarm 的处理：**先发全屏通知、不主动碰屏幕**，由系统点亮屏幕并全屏响铃；正在使用手机时给出带「停止 / 稍后 5 分钟 / 稍后 10 分钟」的横幅，点按即可进入响铃页，声音与震动不依赖屏幕是否点亮。只有全屏通知不可用（通知被关或权限被撤销）时才由应用自己点亮屏幕，3 秒兜底逻辑则用于个别静默丢弃全屏 Intent 的 ROM。
- **「强行停止」会撤销闹钟**：从系统设置里「强行停止」应用、或部分清理软件执行同等操作后，Android 会把应用置为 stopped 状态并撤销它注册的全部闹钟与广播接收器，直到用户再次手动打开应用为止——这不是本应用的行为，任何第三方应用都无法绕过。Vocalarm 在每次进程启动时都会 `rescheduleAll()` 重新登记，所以**打开一次应用即可恢复**；日常清理请只使用「最近任务上滑」，不要用「强行停止 / 强制结束」。
- **闹钟自检**：关于页提供「闹钟自检」面板，直接读出系统侧事实——应用内下一个闹钟、`AlarmManager.getNextAlarmClock()` 登记的下一个闹钟、最近一次收到系统闹钟的时间与累计次数，以及精确闹钟/通知/全屏通知/电池优化四项状态。`AlarmReceiver` 在启动服务前把送达时间写入 `files/trigger.log`（`AlarmService` 再写入响铃时间），因此「到点后时间戳没有更新」可以明确判定为系统没有送达，而不是响铃失败。面板还提供「10 秒后试响一次」，走真实的 `AlarmManager → Receiver → Service → 全屏通知` 链路，用于快速验证锁屏、清理后台后的行为。
- **全屏通知权限**：Android 14+ 的「全屏通知」默认只授予通话与闹钟类应用，部分厂商 ROM 对侧载应用默认关闭。关闭后系统会静默降级成横幅；首页顶部会显示提示条，点击可直达该设置页。
- **厂商 ROM 的额外开关**：小米 / 红米需要在「应用设置 → 权限管理」中允许「后台弹出界面」与「锁屏显示」；华为 / 荣耀需要在「应用启动管理」中允许自启动与后台活动。这些开关没有公开 API，应用无法自行检测或跳转。
- **TTS 音色依赖设备**：系统语音引擎不同，可用音色差异很大。预设按名称/特性推断，找不到时用默认音色并在编辑页提示；「更多音色」列出引擎真实返回的列表。
- **TTS 音量/渐强**：通过 `Bundle("volume")` 传入，部分引擎会忽略；录音播放的渐强由 MediaPlayer 保证。
- **系统闹铃音量**：应用只设置自身播放音量，不修改系统音量；如果系统闹铃声道被静音，仍然不会出声。
- **持续循环上限**：单次响铃最长 30 分钟，避免无人处理时无限播放；期间可随时停止或稍后提醒。
- **仅一次闹钟**：响铃后自动关闭（保留配置），下次启用会重新排期。
- **不做云同步/备份**：`allowBackup=false`，数据只在本机。
- **更新源可达性**：应用内更新直接访问 `api.github.com` 与 Release 资产地址；所在网络无法访问 GitHub 时会显示网络错误。
- **Gradle 9.8.1**：Lint 提示 9.8.2 可用；本机下载 9.8.2 分发包超时，为保证可复现构建固定 9.8.1。升级只需改 `gradle-wrapper.properties` 并提交。
- **图标目录**：自适应图标必须放在 `mipmap-anydpi-v26`；尝试按 Lint 建议移到 `mipmap-anydpi` 会导致 AAPT2 找不到资源（已用 clean build 复现），因此在 `app/lint.xml` 中记录了豁免原因。
- **R8 已启用**：release 开启 `isMinifyEnabled` 与 `isShrinkResources`，APK 约 1.5MB；CI 会保留 `mapping-<version>` artifact，崩溃堆栈可用其中的 `mapping.txt` 还原。

---

## 11. GitHub Flow

1. `main` 始终可构建、可发版；
2. 功能从 `main` 拉短分支（`feat/xxx`、`fix/xxx`），通过 PR 合并；
3. PR 阶段本地或人工跑 `./gradlew test assembleDebug`；
4. 合并后需要发版时，在 `main` 上打 `vX.Y.Z` tag 并推送；
5. 只有 tag 推送会触发 `.github/workflows/release.yml`，自动生成签名 APK 与 GitHub Release；
6. 应用内更新读取最新 Release，因此发版即生效。

依赖保持在当前稳定线：AGP 9.5.0、Kotlin 2.4.21、Compose BOM 2026.09.00、Lifecycle 2.11.0、Activity Compose 1.13.0、Coroutines 1.11.0。升级时优先跑 `./gradlew test lintRelease`。
