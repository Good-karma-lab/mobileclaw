# Guappa

Guappa is a Kotlin-native Android AI organism with a React Native interface.

It is being built as:

- a mobile-first autonomous AI runtime
- a voice, chat, task, memory, and swarm experience in one product
- an Android-native backend with modular providers, tools, memory, proactive flows, and WWSP connectivity
- a compliant, testable, release-ready mobile app rather than a wrapper around a legacy daemon

## Current direction

Guappa is migrating away from the old wrapped architecture. The active product direction is:

- Kotlin-native backend in `mobile-app/android/app/src/main/java/com/guappa/app/`
- React Native UI in `mobile-app/src/`
- World Wide Swarm integration through the dedicated `Swarm` surface
- app-wide AI presence across Chat, Voice, Command, Config, and ambient visuals

## Build the app

Prerequisites:

- Node.js 20+
- npm
- JDK 17
- Android Studio with Android SDK
- a connected Android device or emulator

Install dependencies and run:

```bash
cd mobile-app
npm ci
npx expo prebuild --platform android
npm run android
```

To build a release APK locally:

```bash
cd mobile-app/android
./gradlew assembleRelease
```

## Validation

- Android helper scripts live under `scripts/android/`
- mobile UI flows live under `mobile-app/.maestro/`
- active execution plans live under `docs/plans/`

## Status

The repository is still in migration. If you see legacy references outside the current Guappa mobile stack, they are surfaces being removed.
