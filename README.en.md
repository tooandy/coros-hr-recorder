# COROS HR Recorder

[简体中文](README.md) | [English](README.en.md)

> An independent, offline Android heart rate logger. Not affiliated with or endorsed by COROS. For training and research, not medical diagnosis.

![App icon](assets/app-logo.png)

## Features

- Records standard BLE Heart Rate Service (0x180D/0x2A37) notifications, raw payload, UTC milliseconds and monotonic timestamps.
- Foreground background recording, disconnect/reconnect, remember and optionally auto-connect to your last heart rate sensor.
- Optional phone GPS with outlier rejection, distance, speed, pace, step count, cadence and estimated step length.
- Spoken HR reminders every 1–120 minutes via Android Text-to-Speech. English and Simplified Chinese UI and TTS follow the Android language preference.
- Session history, landscape charts with up to 8 overlaid traces, import/export Garmin FIT and CSV.
- Passive BLE diagnostics that capture raw proprietary notifications without sending unknown vendor commands.
- No account or Internet permission. Exercise files may contain identifiable GPS routes; check before sharing.

## Screen layout

The app uses three bottom tabs: **Workout** for live HR and recording controls, **History** for session selection and FIT/CSV export, and **Settings** for Bluetooth, GPS/steps, voice reminders and BLE diagnostics. Tab switching does not stop an ongoing session. Pause/resume is not yet available; Stop & Save finishes the session after confirmation to avoid accidental taps. Spoken reminders are short (“Current heart rate 150” or “当前心率150” depending on system language), while visual notifications retain BPM.

## Install and compatibility

Android 8.0+ (minSdk 26), compileSdk 36, targetSdk 35. Java-only code uses no native libraries; a **single universal APK** is compatible with arm64-v8a, armeabi-v7a, x86 and x86_64. CI fails if native libraries are introduced without review.

**Important signing notice:** GitHub Actions packages are *debug-signed* and intended for testing. Different Actions runs can have different debug keys, so overlay upgrades are not guaranteed and uninstalling may delete app-private sessions. For stable signed releases, supply your own secure signing configuration; never commit keys or passwords. The historical Android applicationId is deliberately preserved for data compatibility.

## Build and test

Requires JDK 17 or 21, Android SDK 36, Build Tools 36, and network access for Gradle/FIT dependencies.

    ./gradlew --no-daemon :app:compileDebugJavaWithJavac
    bash scripts/test.sh
    ./gradlew --no-daemon assembleDebug

Result: app/build/outputs/apk/debug/app-debug.apk.

GitHub Actions builds the universal APK on every main branch push, PR and manual run. Version tags with a matching docs/releases/vX.Y.Z.md file trigger a GitHub Release with a testing APK and SHA-256 checksum.

## Locales

Default English resources: app/src/main/res/values/strings.xml. Simplified Chinese: app/src/main/res/values-zh/strings.xml. Check translation parity with python3 scripts/check_locales.py. The Android system chooses the appropriate locale automatically. A working Android TTS engine for that language is required for spoken output; Chinese devices may need to enable/download the Chinese speech pack.

See [AGENTS.md](AGENTS.md) for contributor/agent instructions, [CHANGELOG.md](CHANGELOG.md) for history, and [v0.5.0 release notes](docs/releases/v0.5.0.md).

## License

MIT — [LICENSE](LICENSE). Garmin FIT, Android SDK, and other third-party dependencies retain their respective licenses. COROS and associated marks belong to their owners. Measurements and inferred training metrics are not medical advice; BPM alone is insufficient for valid HRV.
