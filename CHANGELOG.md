# Changelog / 更新记录

## [Unreleased] — UI reorganization and spoken HR brevity

- Replace a long single-page home with three persistent bottom tabs: Workout, History, Settings.
- Keep live HR, recording state, GPS/steps summary and the one relevant Start/Stop & Save action on Workout; Stop & Save now requires confirmation.
- Move session selection, FIT/CSV export and comparison to History; Bluetooth setup, sensor preferences, TTS configuration and passive BLE diagnostics to Settings.
- Preserve saved user preferences and live service recording when switching tabs; show last selected tab after Activity recreation.
- Chinese TTS now says “当前心率150” instead of “当前心率150次每分钟”; English now says “Current heart rate 150”. Preview uses identical concise phrases.
- Pause/resume recording is not implemented yet; existing Stop & Save behavior remains.


## [0.5.0] — 2026-11-07 (initial public-source release)

### Added
- Offline Android BLE heart rate capture and raw CSV logging, Garmin FIT export, and session history comparison.
- Optional GPS track, distance, pace, step count, cadence and estimated step length with basic denoising.
- Last-device memory and opt-in auto-connect/auto-record.
- Periodic voice heart rate reminders with configurable 1–120-minute interval and TTS preview.
- Passive three-service BLE diagnostics for research, without undocumented vendor writes.
- Android app launcher icon and adaptive icons.
- Simplified Chinese and English interface resources.
- Public-source developer documentation, privacy review and GitHub Actions workflow.
- Universal debug APK workflow for 32-/64-bit ARM and x86 (Java-only, no native libraries).

### Known limitations
- TTS and background timing require testing on the target Android device; OEM battery policies may delay callbacks.
- CI build artifacts are signed with ephemeral debug keys, not release keys. Upgrade may require uninstalling and losing locally stored recordings.
- No RR/IBI is provided by observed standard heart rate packets, so no valid HRV feature is claimed.
- GPS distance and estimated step length are not medically or competition certified.
- Public release omits old private repository history and private NAS deployment.

## Earlier development
- 0.4.1: passive BLE private-notify research capture.
- 0.4.0: optional GPS/steps and enriched FIT export; local-time FIT file name.
- 0.3.1: session list refresh and local date display.
- 0.3.0: landscape heart rate comparison.
- 0.2.0: sensor battery level and GATT diagnostics.
- 0.1.0: initial BLE heart rate recorder.
