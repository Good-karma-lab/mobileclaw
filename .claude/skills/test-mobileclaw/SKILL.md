---
name: test-mobileclaw
description: Complete testing workflow for the Guappa Android app. Use when running tests, verifying agent behavior, checking integrations, or troubleshooting test failures. Includes build pipeline, Maestro testing, Android verification, and side effect validation.
disable-model-invocation: false
allowed-tools: Bash, Read, Grep, Glob, Write, Edit
---

# Guappa Android Testing Protocol

Use this workflow when validating the Guappa Android app.

## Architecture principle

- Guappa uses a Kotlin-native Android backend with a React Native UI.
- Tests must validate native behavior, bridge behavior, UI state, and real side effects together.
- Do not assume an embedded Rust daemon or localhost gateway architecture.

## 1. Build pipeline

```bash
cd mobile-app
npm ci
npx expo prebuild --platform android
cd android
./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

## 2. Clean app state

```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am force-stop com.guappa.app
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell pm clear com.guappa.app
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am start -n com.guappa.app/.MainActivity
```

## 3. Core validation flow

1. Install a fresh debug build.
2. Launch the app and verify onboarding or the landing screen appears.
3. Run the relevant Maestro flow from `mobile-app/.maestro/`.
4. Inspect the visible result manually.
5. Verify the expected side effect actually happened.

## 4. Recommended Maestro flows

```bash
maestro test --device emulator-5554 mobile-app/.maestro/guappa_all_screens_smoke.yaml
maestro test --device emulator-5554 mobile-app/.maestro/guappa_voice_full_flow.yaml
maestro test --device emulator-5554 mobile-app/.maestro/guappa_swarm_identity_then_connect.yaml
maestro test --device emulator-5554 mobile-app/.maestro/guappa_config_then_chat.yaml
```

## 5. Android event simulation

```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 emu sms send +341234567890 "Guappa test message"
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 emu gsm call +341234567890
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 emu gsm cancel +341234567890
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 emu geo fix -122.4194 37.7749
```

## 6. What to inspect after a test

- the expected screen state is visible
- the app does not fall back to placeholder or mock behavior
- the side effect actually happened when the test expects one
- logcat does not show repeated native bridge or crash errors

## 7. Useful commands

```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 logcat -d
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p > /tmp/guappa-test-result.png
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell content query --uri content://sms/sent --projection address,body,date
```

## 8. Current rule

If a test passes but the user-visible result is wrong, the test is not good enough yet.
