# MobileClaw Android Agent 🦀+ Mobile App

![MobileClaw Logo](./assets/logo.png)

MobileClaw is the Android-first agent implementation in this repository.
It combines:

- a Rust agent/runtime with Android and hardware tool support
- a React Native mobile client in `mobile-app/`
- policy-aware device/tool controls for phone capabilities

## What is included

- **Android agent runtime**: Rust runtime and `android_device` tool integration
- **Hardware/peripheral tools**: hardware memory/map/info and related tooling
- **Mobile app (Expo + RN)**

## Run mobile app

From `mobile-app/`:

```bash
npm install
npm run android
```

## E2E UI tests (Maestro)

From `mobile-app/`:

```bash
maestro test .maestro/smoke_navigation.yaml
maestro test -e OPENROUTER_TOKEN="<token>" .maestro/live_openrouter_chat.yaml
```
