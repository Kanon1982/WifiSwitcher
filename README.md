# 🌐 WifiSwitcher · 一键修改 Wi-Fi 网络设置的 Android App

<div align="right">
  <strong>语言 / Language：</strong>
  🇨🇳 <b>中文</b>
  <span>｜</span>
  <a href="README.en.md">🇬🇧 English</a>
</div>

> 一款面向小白用户、用 **Kotlin + Jetpack Compose** 构建的单页 Android 工具：  
> **修改当前连接 Wi-Fi 的 IP / 网关 / 主 DNS / 备用 DNS**，支持 **静态 IP 模式** 和 **DHCP 自动模式** 一键切换，  
> 特别针对 **MIUI 14（小米/红米）** 做了兼容性适配，解决了"命令执行卡死"和"设置后不生效"两大痛点。

---

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 🧭 **双模式切换** | 🟢 **DHCP 自动模式（推荐）**：切回路由器自动分配，啥也不用填<br>🔵 **静态 IP 手动模式**：自己填 IP / 前缀 / 网关 / 主备 DNS |
| 💾 **数据持久化** | 使用 **Jetpack DataStore**（替代老旧的 SharedPreferences）保存上一次设置，下次打开自动恢复 |
| 📁 **最多 20 套预设方案** | 竖向卡片列表管理"家里 / 公司 / 宿舍 / 测试环境"等常用网络，一行一套，点击即载入表单 |
| ⚡ **Root Shell 超时时保护** | 每条命令单独 `su` 执行 + 3~5 秒软/硬超时，超过 15 秒整体兜底，**杜绝卡读条** |
| 🛠️ **MIUI 14 专用适配** | 自动关掉 Private DNS、暂停 NetworkAgent、使用自定义路由表 + 高优先级 ip rule、必要时 iptables DNAT 强制 DNS，避免 MIUI "应用了但底层被覆盖" |
| 🧑‍🎨 **小白友好 UI** | 重新设计界面：语义化颜色（绿=应用 / 蓝=保存 / 橙=警告 / 红=错误）、卡片分组、示例 Placeholder、诊断日志默认折叠避免吓到用户 |
| 💡 **善意成功提醒** | 应用成功后额外弹一条 Snackbar，告诉用户"**系统设置里 Wi-Fi 页面没变化是正常的，底层已经改好** ✅，想同步只需开关一下 Wi-Fi" |

---

## 📱 环境要求

| 项目 | 要求 |
|------|------|
| 最低 Android 版本 | **Android 12（API Level 31）** |
| 权限 | 设备必须 **已 Root**（使用 Magisk/KernelSU 等），首次启动在 Root 授权弹窗点「允许」并勾选永久 |
| Wi-Fi 状态 | 必须已经连接上一个 Wi-Fi（本 App 只改"当前连接"的 Wi-Fi 网络配置） |

---

## 🚀 如何使用

### 1️⃣ 安装 APK

方式 A — 自己构建（推荐）：跟着下面 [🧰 构建 Debug APK 步骤](#-构建-debug-apk-步骤) 生成 `app-debug.apk`，拷贝到手机安装。

方式 B — 如果你是从 GitHub Actions / Release 下载的 APK，直接点安装即可（首次安装如果提示"未知来源"，在系统设置里允许安装）。

### 2️⃣ 使用流程（小白版）

```
打开 App
    ↓
点「允许」授权 Root 权限（顶部卡片会变绿 ✓）
    ↓
选择模式：
  🟢 绿色「自动模式 DHCP」 → 直接点底部绿色大按钮「✓ 一键切回自动模式」
  🔵 蓝色「手动模式 静态 IP」 → 按①②③填好 5 个字段 → 点「✓ 立即应用设置」
    ↓
等几秒，底部弹成功提示 + 善意提醒（系统设置没同步是正常的！）
    ↓
🎉 完成！你可以打开浏览器访问一下网站验证网络是否通。
```

---

## 🖼️ 界面一览

> 截图位（你可以自己运行 App 后截图补到这里）

```
┌─────────────────────────────────────────────┐
│ 顶部栏：Wi-Fi Config + 副标题说明              │
├─────────────────────────────────────────────┤
│ 【当前状态卡片】 Root ✓ + Wi-Fi 接口 IP/DNS     │
├─────────────────────────────────────────────┤
│ 【两张模式大卡片】 🟢DHCP    🔵静态IP          │
├─────────────────────────────────────────────┤
│ 条件展开：                                     │
│  - 选 DHCP → 绿色「选择了自动模式」提示卡        │
│  - 选 静态 → ①IP ②网关 ③DNS 分组表单 + 预设列表 │
├─────────────────────────────────────────────┤
│ 【底部绿色大按钮】 立即应用 / 一键切回自动        │
└─────────────────────────────────────────────┘
```

- **预设方案**：竖向列表，一行一套，左侧图标+名称、中间摘要、右侧铅笔改名 / 垃圾桶删除。
- **诊断日志**：默认完全隐藏，只有命令失败时才会红色边框弹出，点开可查看每条命令的 stdout / stderr（方便排查问题，截图发给开发者）。

---

## 🧰 构建 Debug APK 步骤

本仓库包含了 Gradle Wrapper (`gradlew.bat`)，只需要 **JDK 17** + **Android SDK**，不需要单独装 Android Studio。

### Windows 下构建（PowerShell）

```powershell
# 1. 安装 JDK 17（推荐 Eclipse Temurin 17，可 winget 直接装）
winget install EclipseAdoptium.Temurin.17.JDK -e

# 2. 设置环境变量（假设 JDK 装在默认位置）
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"

# 3. 设置 Android SDK 根目录（里面要有 platforms/android-34 等）
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"

# 4. 进入项目根目录（这个 README.md 所在的目录）
cd WifiSwitcher

# 5. 构建 Debug 版 APK（首次构建会自动下载 Gradle 8.5 + Compose/Kotlin 依赖，需联网）
.\gradlew.bat --no-daemon assembleDebug

# 6. 构建成功后 APK 在这里：
ls .\app\build\outputs\apk\debug\app-debug.apk
```

构建成功会看到 `BUILD SUCCESSFUL`。APK 拷贝到手机直接双击安装即可。

---

## 🏗️ 项目结构

```
WifiSwitcher/
├── app/
│   ├── build.gradle.kts              # app 模块：依赖、minSdk=31, targetSdk=34
│   ├── proguard-rules.pro            # ProGuard 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml       # 声明应用名称、主题、权限
│       ├── java/com/wificonfig/app/
│       │   ├── WifiConfigApplication.kt
│       │   ├── MainActivity.kt       # Compose 主 Activity
│       │   ├── data/
│       │   │   ├── NetworkConfig.kt          # 数据类：StaticNetworkConfig / SavedPreset 等
│       │   │   └── PreferencesRepository.kt  # DataStore 持久化（配置 + 预设列表）
│       │   ├── ui/
│       │   │   ├── WifiConfigScreen.kt       # UI 主页面（Jetpack Compose 单页）
│       │   │   ├── WifiConfigViewModel.kt    # ViewModel：状态机 + 业务逻辑
│       │   │   └── theme/                    # Color/Theme/Type 主题配色
│       │   └── util/
│       │       ├── RootShell.kt              # Root Shell 执行（单命令 su + 超时兜底）
│       │       └── NetworkConfigManager.kt   # 真正改网络：静态 IP / DHCP / MIUI 适配
│       └── res/                              # 资源文件：字符串、颜色、主题、启动图标
├── build.gradle.kts                  # 项目级 Gradle 配置（Kotlin/Compose 插件版本）
├── settings.gradle.kts               # 项目名、include 模块
├── gradle.properties                 # 全局 Gradle 属性（非传递 R 类、Jetifier 等）
├── gradle/
│   └── wrapper/gradle-wrapper.properties / gradle-wrapper.jar
├── gradlew.bat                       # Windows 下 Gradle Wrapper 启动脚本
└── .gitignore                        # 忽略 build/、.gradle/、local.properties 等
```

---

## 🧠 技术栈

| 类别 | 选型 |
|------|------|
| **语言** | 100% **Kotlin** |
| **UI** | **Jetpack Compose BOM 2024.06.00**（Material3） |
| **架构** | 单 Activity + MVVM（`ViewModel` + `StateFlow`） |
| **持久化** | **Jetpack DataStore Preferences**（替代 SharedPreferences） |
| **异步** | **Kotlin Coroutines + Flow**，`viewModelScope` + `withTimeout` 双重超时保护 |
| **Root 执行** | `ProcessBuilder("su", "-c", ...)` 单命令单会话 + `waitFor(timeout)` 防卡死 |
| **构建工具** | Gradle 8.5（Gradle Wrapper，无需本地装 Gradle） + Kotlin DSL |
| **JSON 序列化** | 纯 `org.json.JSONObject` 手写，无额外依赖（避免包体积膨胀） |
| **最低支持** | Android 12 API 31 |
| **目标版本** | Android 14 API 34 |

---

## ⚠️ 常见问题（FAQ）

<details>
<summary>1. 点"应用"后一直转圈 / 进度条不动？</summary>

> 新版已做"单命令超时 3~5 秒 + 整体 15/20 秒超时"双重兜底，如果仍卡住通常是 MIUI 的 Root 权限弹窗被系统吃了。解决：先杀掉 App 重开，在 Magisk 里手动给本 App 授权 Root。

</details>

<details>
<summary>2. 显示「已应用成功」但去系统设置里看 Wi-Fi 的 IP 没变？</summary>

> **完全正常！** 因为本 App 是直接改 Android 内核层的 ip rule / route / DNS，系统设置页面的 UI 缓存不会同步刷新。解决验证办法：
> - 打开浏览器直接访问 `https://test.ustc.edu.cn`（或者别的网站） —— 能打开就是生效了
> - 想让系统设置 UI 也同步：关 Wi-Fi 再打开重连一次就行（本 App 改的 DHCP 模式本来就会自动重启 Wi-Fi）

</details>

<details>
<summary>3. 为什么应用需要 Root？</summary>

> Android 普通 App（UID < 10000）没有权限调用 `ip addr add`、`ip rule add`、`iptables -t nat`、`ndc netd` 这类底层网络接口。只有拿到 UID 0（Root）才能改网络栈。

</details>

<details>
<summary>4. 在非 MIUI 系统（原生 / 一加 / OPPO / 三星 One UI 等）能用吗？</summary>

> 能用。MIUI 专用适配代码（关 Private DNS、暂停 NetworkAgent）在非 MIUI 系统上是"无害 fallback"—— 命令执行失败会被 diagnostics 记录但整体不会报错，不影响主流程生效。

</details>

<details>
<summary>5. 为什么预设只能保存 20 套？</summary>

> 小白用户一般只在 2~5 个场景（家里 2.4G/5G、公司、宿舍、咖啡店）间切换，20 套已经远超日常需求，避免存太多混乱。上限是 `SavedPreset.MAX_PRESETS = 20`，在 [NetworkConfig.kt](app/src/main/java/com/wificonfig/app/data/NetworkConfig.kt) 里常量，开发者可以自行修改。

</details>

---

## 📜 License

```
MIT License

Copyright (c) 2026 Kanon1982

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```
