# COROS HR Recorder · 高驰心率记录器

[简体中文](README.md) | [English](README.en.md)

> 非 COROS 官方项目。离线 Android 蓝牙心率记录器，适用于标准 BLE 心率设备。项目用于运动记录和开发研究，不属于医疗器械。

![App icon](assets/app-logo.png)

## 功能

- **蓝牙采集**：BLE Heart Rate Service（0x180D/0x2A37），记录 BPM、原始通知、UTC 毫秒及单调时钟。
- **持续记录**：前台服务、断连重试、记住上次设备、可选打开 App 自动连接和开始记录。
- **运动传感器**：可选手机 GPS 轨迹、距离、速度、配速、步数、步频、估算步幅。过滤 GPS 精度差、跳点、漂移和中断时的假距离。
- **定时播报**：每 1～120 分钟播报心率，系统语言为中文时使用中文 TTS，英语时使用英语 TTS；支持试听。数据过期、蓝牙断开时不播报旧 BPM。
- **历史分析**：本地心率历史、横屏叠加对比最多 8 条曲线、FIT/CSV 文件导入及曲线 CSV 导出。
- **活动导出**：原始 CSV 与 Garmin FIT（心率、轨迹、距离、速度、步频等）。
- **BLE 研究**：可选被动订阅三个私有通知特征，用于保存和分析原始字节。不会发送未知厂商控制指令。
- **隐私**：不需要账号和互联网权限，不上传个人心率、GPS。分享 FIT 前请检查精确轨迹。

## 安装及架构

最低 Android 8（SDK 26），Android 16（SDK 36）编译，targetSdk 35。项目不包含 JNI 原生库，因此 CI 直接生成 **一个通用 APK**，同时兼容 arm64-v8a、armeabi-v7a、x86、x86_64。未来如引入原生库，流水线会要求先审查 ABI 覆盖。

GitHub Actions 会上传通用 **debug 签名 APK**，可安装测试。请注意：不同 CI 运行的调试签名不一定一致，无法保证跨运行覆盖升级；卸载 App 可能删除私有记录。正式稳定更新需要你自行配置持久的签名密钥，不要上传 keystore、密码或个人令牌。

为了与早期安装的数据兼容，Android applicationId 暂保留原包名。若修改 applicationId，会被系统视为不同 App。

## 使用方法

顶部及横屏对比均按 Android 状态栏、导航栏与摄像头挖孔的安全区域留白，避免遮挡。**历史记录**支持编辑运动名称与备注、模糊搜索（名称／备注／日期／文件名）、按年月筛选、按时间或名称排序、只看有备注以及分批加载；名称备注只存 App 本地，不改变原始记录文件。改进后的图标不再保留原 PNG 的白色边框。

应用采用底部三个页面：**运动**（实时心率、运动数据、开始/停止保存）、**历史记录**（选择记录、FIT/CSV 导出、曲线对比）、**设置**（蓝牙连接、GPS/计步、自动记录、语音提醒、BLE 诊断）。切换页面不会停止后台记录。目前没有暂停/继续功能，请使用“停止并保存”结束一次完整记录，App 会再次确认，避免跑步时误触。中文语音提醒简化为“当前心率150”，不再读出“次每分钟”，通知栏继续显示 BPM。

1. 授予蓝牙权限，佩戴并激活心率臂带。
2. 扫描并选择设备，订阅成功后自动记住。
3. 按需要打开自动记录、GPS、计步与定时提醒。GPS、运动检测需要相应权限。
4. 点击停止并保存，再从记录列表导出 FIT/CSV。
5. 点击语音试听按钮验证 TTS；设备需安装当前语言对应的文字转语音引擎，并开启媒体音量。

## 本地构建

需要 JDK 17/21、Android SDK Platform 36、Build Tools 36、可下载 Gradle 与 Garmin FIT 依赖。使用以下命令依次编译、测试与打包：

    ./gradlew --no-daemon :app:compileDebugJavaWithJavac
    bash scripts/test.sh
    ./gradlew --no-daemon assembleDebug

APK 输出：app/build/outputs/apk/debug/app-debug.apk。图标源文件：assets/app-logo.png。

## GitHub Actions / 发布

- Push 到 main 或创建 PR：检查 en/zh 本地化资源、编译 Java、运行离线测试、构建通用 APK，并上传 Actions Artifact。
- 推送版本标签 vX.Y.Z：要求 docs/releases/ 中存在对应 Markdown 版本说明，自动创建 GitHub Release 并附加 **测试用 debug APK** 与 SHA-256 文件。
- 发布包没有生产签名密钥；需要可长期覆盖升级时，应自行保管和配置正式签名方案。

## 国际化与贡献

默认英语资源位于 app/src/main/res/values/strings.xml；简体中文资源位于 values-zh/strings.xml。安卓根据系统语言加载对应资源。执行 python3 scripts/check_locales.py 验证两套键一致。

开发与自动化代理须阅读 [AGENTS.md](AGENTS.md)。更新记录：[CHANGELOG.md](CHANGELOG.md)。首版公开发布说明：[v0.5.0](docs/releases/v0.5.0.md)。

## 许可与免责声明

本项目非 COROS 官方软件，与 COROS 无赞助、隶属或合作关系。COROS 为其权利人的商标。心率、GPS、步频数据及 HRV 实验用途不能替代医学诊断；缺少逐搏 RR/IBI 时不应以 BPM 变化冒充 HRV。

本项目源码采用 [MIT License](LICENSE)。Android SDK、Garmin FIT SDK 等第三方组件遵守各自许可证。
