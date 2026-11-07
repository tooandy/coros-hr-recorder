# Agent instructions (AGENTS.md)

This repository is public. Treat every branch, commit, workflow log, README and test fixture as externally visible.

## Scope and safety
- Android Java project using Gradle Wrapper, compileSdk 36, targetSdk 35, minSdk 26.
- No unknown BLE proprietary commands; passive notify subscriptions only for diagnostic mode.
- Never upload user activity CSV/FIT, GPS routes, device BLE MAC addresses, account names, private host paths, corporate links, tokens, certificates, keystores, credentials, or signing passwords.
- Do not reintroduce a local NAS deployment script, absolute device path, implicit network upload, or proprietary internal infrastructure.
- Keep app data on-device; maintain the current Android applicationId unless an explicit migration is designed and reviewed.
- Do not claim HRV from BPM values; use authentic RR intervals or label outputs as non-HRV.
- Do not claim hardware compatibility without testing the actual device.

## Localization
- Every new user-visible string in activities, views, services and foreground notifications must have English in app/src/main/res/values/strings.xml and Chinese in app/src/main/res/values-zh/strings.xml.
- Prefer Android string resources over literal Chinese or English Java UI copy. Run: python3 scripts/check_locales.py
- TTS language must follow the Android locale. If the required voice engine is unavailable, show an informative fallback rather than silently switching languages.

## Testing and build
1. python3 scripts/check_locales.py
2. ./gradlew --no-daemon :app:compileDebugJavaWithJavac
3. bash scripts/test.sh
4. ./gradlew --no-daemon assembleDebug
5. Inspect APK for native libraries; universal APK only while no .so files exist.

Do not call Gradle assemble tasks to publish to a personal NAS; the open repository has no such step. CI uploads APK to GitHub Actions and tagged Releases only.

## PR/release requirements
- Include summary, limitations and tests with every change.
- Maintain CHANGELOG.md and docs/releases/vX.Y.Z.md for tag releases.
- Never commit signing keys. CI debug keys are ephemeral; a production signing workflow requires separately managed secrets and review.
- No force-push to a public default branch. Avoid including old private repository Git history.
