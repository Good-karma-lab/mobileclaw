# MobileClaw Android Deployment and Capability Model

This document describes how MobileClaw (ZeroClaw core) runs on Android with two distribution targets:

- `play` distribution: Google Play-compliant baseline
- `full` distribution: enterprise/sideload with optional telephony features

## Architecture

ZeroClaw uses a hybrid architecture:

1. Rust core runtime and tool orchestration
2. Android host app (`android-app/`) for UI and OS integration
3. Android capability bridge (`android_device` tool)

### Rust touchpoints

- Runtime adapter: `src/runtime/android.rs`
- Runtime factory: `src/runtime/mod.rs`
- Android tool: `src/tools/android_device.rs`
- Config contracts: `src/config/schema.rs`

## Config

```toml
[runtime]
kind = "android"

default_provider = "ollama"
default_model = "gpt-oss:20b"
api_url = "http://10.0.2.2:11434"

[android]
enabled = true
distribution = "play" # or "full"

[android.capabilities]
app_launch = true
sensors = true
sms = false
calls = false

[android.bridge]
mode = "mock" # mock | http | jni
endpoint = "http://127.0.0.1:9797/v1/android/actions"
allow_remote_endpoint = false
timeout_ms = 10000

[android.policy]
require_explicit_approval = true
allowed_packages = ["com.android.settings"]
allowed_phone_numbers = ["+15551234567"]
max_sms_per_hour = 5
max_calls_per_hour = 5
```

## Security model

- Telephony and SMS actions are blocked in `play` distribution by default.
- High-risk phone actions require `approved=true` when `require_explicit_approval=true`.
- Phone number and package allowlists are enforced before execution.
- Rate limits are applied for SMS/call actions.
- Bridge defaults to localhost-only endpoint unless explicitly opened.

## Android UI

The app provides screens for:

- Chat with the agent
- LLM/provider settings
- Integrations settings
- Device action controls
- Security policy toggles

## Android tool actions

The `android_device` tool supports:

- App/runtime: `launch_app`, `list_apps`, `open_url`, `open_settings`
- Sensors/device: `sensor_read`, `vibrate`, `get_network`, `get_battery`
- Optional privileged: `send_sms`, `read_sms`, `place_call`, `read_contacts`, `read_calendar`
- Optional permissions: `take_photo`, `record_audio`, `get_location`, `set_clipboard`, `read_clipboard`, `post_notification`

All actions are capability-gated in `[android.capabilities]`, and high-risk actions are policy-gated in `[android.policy]`.

## Emulator automation

Scripts:

- `scripts/android/setup_sdk.sh`
- `scripts/android/create_avd.sh`
- `scripts/android/start_emulator.sh`
- `scripts/android/run_e2e.sh`

Example flow:

```bash
scripts/android/setup_sdk.sh
scripts/android/create_avd.sh
scripts/android/start_emulator.sh

cd android-app
./gradlew assemblePlayDebug

cd ..
scripts/android/run_e2e.sh
```

If Android SDK tools are missing, scripts fail fast with remediation hints.
