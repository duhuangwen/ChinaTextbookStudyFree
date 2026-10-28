# 安卓与 Android TV 工程

对应[上游 issue #6](https://github.com/wuwangzhang1216/ChinaTextbookStudyFree/issues/6)。`apps/android` 是可由 Android Studio 打开、编译和安装的 Java/Gradle 工程，最低 Android 6.0（API 23）。APK 默认通过系统 WebView 加载内置的 `assets/www` 静态站和完整课程题库，安装后不依赖线上网页；同一个 APK 同时声明手机启动入口和 Android TV 的 Leanback 启动入口。

## Android Studio

1. 打开仓库中的 `apps/android` 文件夹。
2. 使用 JDK 17；安装 Android SDK Platform 35 和 Build Tools 34.0.0，然后完成 Gradle 同步。
3. 选择设备或模拟器，点击 Run；或者选择 Build → Build APK(s)。

工程固定 AGP 8.7.3 / Gradle 8.9。版本组合参考 [Android 官方兼容表](https://developer.android.com/build/releases/agp-8-7-0-release-notes)。已提交官方 Gradle Wrapper，并校验 Wrapper 和 Gradle 分发包 SHA-256，无需全局安装 Gradle。

命令行构建：

```sh
cd apps/android
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

默认从安全本地域名 `https://appassets.androidplatform.net/` 加载内置 Web。若希望切换回远程站点，可以在 `apps/android/gradle.properties` 添加以下配置，或构建时传 `-PwebAppUrl=https://你的课堂地址/`：

```properties
webAppUrl=https://你的课堂地址/
```

远程模式仅接受 HTTPS 地址；本地 Docker 的 HTTP 地址需要先配置 HTTPS。修改地址后重建 APK。

## 自包含构建

Android APK 会把 `apps/web/out/` 复制到 `assets/www`。构建前必须先准备完整课程数据并导出静态 Web：

```sh
python3 scripts/install-web-data.py
cd apps/web && npx next build
cd ../../apps/android && ./gradlew assembleDebug
```

`install-web-data.py` 只下载约 4.9 MB 的 `data.zip`，不会下载 2 GB 音频和图片。APK 内含全部互动课程、题库、页面、字体、图标和课程 JSON；没有预生成 MP3 时，题目和讲解的喇叭按钮调用电视系统 TTS。课本原页、故事插画和预生成语音不打包进 APK，相关页面仍可显示文字内容。GitHub Actions 的 `Android APK` 工作流会自动执行这些步骤。

## U 盘导入课本页、插画和语音

进入「我的」→「离线课本与语音」→「从 U 盘导入」。可以一次多选以下三个官方压缩包，也可以逐个导入：

- [`audio.tar.gz`](https://github.com/wuwangzhang1216/ChinaTextbookStudyFree/releases/download/v1.2.0-assets/audio.tar.gz)
- [`story-images.zip`](https://github.com/wuwangzhang1216/ChinaTextbookStudyFree/releases/download/v1.2.0-assets/story-images.zip)
- [`textbook-pages.zip`](https://github.com/wuwangzhang1216/ChinaTextbookStudyFree/releases/download/v1.2.0-assets/textbook-pages.zip)

也可把 `audio/`、`story-images/`、`textbook-pages/` 三个目录放在一个 ZIP 中。导入采用系统文件选择器，不需要申请全盘存储权限；应用会先校验三个官方文件名的 SHA-256，再流式解压到私有目录，完成后即使拔掉 U 盘或重启电视仍可使用。建议电视至少预留 2.5 GB 空间。

音视频目录中的 `.opus` 文件由 Android WebView 直接播放；若目标固件的 WebView 不支持 Opus，可使用系统 TTS 兜底，或改用预转码 MP3 媒体包。导入接口只接受这三个媒体目录及常见音视频扩展名，并拒绝绝对路径、目录穿越和符号链接式路径。

## Android TV / 雷鸟电视

TV 支持代码随 Web 静态站一起打包，因此默认 APK 不需要先部署线上站点。

- 应用在电视系统启动时自动附加 `ChinaStudyFreeTV` WebView User-Agent，并启用 Web 端 TV 模式。
- 遥控器上下左右按屏幕位置移动焦点，OK/Enter 执行控件，系统返回键返回 Web 历史。
- 电视浏览器也可使用 `?tv=1` 强制进入 TV 模式，使用 `?tv=0` 关闭。
- 应用声明了 `LEANBACK_LAUNCHER`、可选触摸屏和 16:9 TV banner，可在 Android TV 启动器中显示。
- 雷鸟鹤6 2026款属于各厂商定制系统，首次侧载仍可能需要在电视的“电视卫士”、U 盘安装或 ADB 中允许第三方应用。不同固件的限制可能变化。

TV 验收至少应覆盖：首次年级选择、一年级教材入口、答题选项、继续按钮、返回键、长页面滚动、音频播放及 TTS。当前实现已考虑 1080p 电视与 5% 过扫描安全边距，并完成浏览器模拟遥控器流程验证；最终仍需在目标电视真机确认遥控器键值和厂商启动器行为。

## GitHub 下载 APK

进入仓库 Actions → **Android APK**，打开成功的运行，下载 `china-textbook-study-debug-apk` artifact，解压安装 APK。需要登录 GitHub。工作流在安卓工程发生更改时构建、执行单元测试和 Android Lint；合并工作流到默认分支后也可手动运行。

CI APK 使用调试签名，供自行安装和测试，不是应用商店发行包。不同机器产生的调试签名可能不同，无法直接覆盖已有安装；卸载会删除学习记录。正式分发应在 Android Studio 生成并妥善保存自己的 release 签名，私钥不要提交到仓库。

## 功能与边界

- Web 课程、答题与学习记录随 APK 内置；进度存在本机 WebView 的 localStorage，浏览器和 APK 的记录相互独立。
- 跟读请求麦克风时才显示 Android 权限申请；仅为配置站点授予音频采集，摄像头和其他权限不授予。拒绝权限后仍可学习。实现参考 [Android PermissionRequest](https://developer.android.com/reference/android/webkit/PermissionRequest)。
- Android 返回键返回课堂历史；屏幕旋转保留浏览状态，内容避开系统栏、刘海及键盘。
- 外部 HTTPS 链接交给浏览器，文件导入使用系统文件选择器；网页只暴露受限的系统 TTS 朗读接口，没有文件或相机原生桥权限。
- 目前不支持网页生成的 `blob:` 下载（如学习备份导出）；会提示用户。请不要依赖该 APK 备份数据，也不要随意清除应用数据。完整导出支持和离线媒体包是后续范围。
- 预生成 MP3 缺失时，手动朗读会回退到 Android 系统 TTS；系统未安装中文语音包时仍可能无法发声。请保持 Android System WebView 和中文语音包更新。

本机未安装 Android SDK；构建与检查交由 GitHub Actions。成功的 CI 只证明工程可以构建及静态/单元检查通过，不能替代 Android 真机的布局、录音和播放验收。

2026-10-05 上游验证：[GitHub Actions 构建](https://github.com/wuwangzhang1216/ChinaTextbookStudyFree/actions/runs/37336100162)成功执行 `testDebugUnitTest lintDebug assembleDebug`，Android Lint 零问题，并上传 APK 和检查报告。网址策略的 6 项测试覆盖课程路径、端口、大小写、伪造域名、凭据及危险协议。
