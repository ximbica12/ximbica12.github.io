# GPT J7 ReVanced

Experimental ReVanced-style compatibility patches for the official ChatGPT Android app, targeting Samsung Galaxy J7 Prime / Android 8.1 / ARMv7.

## Target verified from the supplied APKM

- Package: `com.openai.chatgpt`
- Version: `1.2026.258` (`2625803`)
- minSdk: 25
- Target device ABI: `armeabi-v7a`
- Primary OS: Android 8.1 (API 27)

## What this patch set changes

### J7 Oreo storage compatibility
Adds the legacy storage permissions that ChatGPT's own pre-Android-Q code checks, but which are absent from the supplied manifest.

### J7 Android 8 downloads
The supplied DEX contains two explicit Android-Q guards:

- `File preview downloads are unsupported below Android Q`
- `Codex Remote downloads are unsupported below Android Q.`

This patch removes only those SDK gates and reuses the existing app download paths. It also hooks `MainActivity.onCreate` to request legacy storage access on API 25-28.

### J7 startup performance
Optional startup reduction for low-memory devices:

- disables the Datadog RUM startup provider;
- disables Sentry NDK preload provider;
- removes the Bitdrift capture startup initializer;
- deliberately leaves auth, Firebase notifications, WorkManager, files and voice components enabled.

### Runtime J7 UI tuner
The extension removes Activity window transitions and normalizes dark system bars on Android 8. This is intentionally conservative: the app's main UI is heavily Compose/Valdi-driven and will be redesigned only where fingerprints are stable enough to avoid launch crashes.

## Safety / scope

This repository contains only patch code. It does **not** redistribute the proprietary ChatGPT APK. Apply it to an original APK you obtained legitimately.

These patches do not bypass accounts, subscriptions, model entitlements, server-side limits, moderation or authentication. They target client compatibility and performance only.

## Build

Requires Java 17 and Gradle 8.9. The GitHub workflow builds the patch bundle automatically on the `chatgpt-j7-revanced` branch.

```bash
gradle clean build
```

The project uses ReVanced Patcher 21.0.0 and the official ReVanced patches Gradle plugin.

## Status

`0.1.0-dev.1` — first Android 8/J7 compatibility pass. The download guards were fingerprinted directly from ChatGPT 1.2026.258.
