# GPT J7 ReVanced

Experimental ReVanced-style compatibility patches for the official ChatGPT Android app, targeting Samsung Galaxy J7 Prime / Android 8.1 / ARMv7.

Target verified from the supplied APKM:
- package: com.openai.chatgpt
- version: 1.2026.258 (2625803)
- minSdk: 25
- target ABI: armeabi-v7a
- primary OS: Android 8.1 (API 27)

The supplied DEX contains explicit Android-Q guards for File Preview and Codex Remote downloads. This project removes only those SDK gates, requests the legacy pre-Q storage permission, and provides an optional startup-performance patch that disables Datadog RUM preload, Sentry NDK preload and the Bitdrift startup initializer while deliberately preserving authentication, notifications, WorkManager, files and voice.

The runtime J7 tuner also removes Activity window transitions and normalizes dark system bars. The main ChatGPT UI is heavily Compose/Valdi-driven, so visual changes are kept conservative until stable fingerprints are available.

This repository contains patch code only and does not redistribute the ChatGPT APK. It does not bypass accounts, subscriptions, model entitlements, server-side limits, moderation or authentication.

Build requirements: Java 17, Gradle 8.9. ReVanced Patcher version: 22.0.1.

Status: 0.1.0-dev.1.
