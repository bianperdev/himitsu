# Himitsu

**Himitsu** (秘密, "secret") is a privacy-first AI role-play client for Android, with on-device inference as a first-class option.

> [!WARNING]
> Himitsu is in early development. There are no releases yet, and data formats, the user interface and features will change.

## Built on RPClient

Himitsu is a fork of **[RPClient](https://github.com/KafuuNeko/RPClient) by [Kafuu Neko](https://github.com/KafuuNeko)**. RPClient is a local-first role-play client for Android with character cards, lorebooks, group chats, story mode, Regex scripts and a prompt inspector. Nearly everything Himitsu can do today comes from that work, and the full RPClient history is preserved in this repository. Thank you, Kafuu Neko.

If you want a stable, mature app today, use RPClient.

## Goals

- **Privacy**: encrypted database, API keys protected by the Android Keystore, app lock, optional disguise mode, zero telemetry.
- **Local inference**: run models on the device with llama.cpp, with optional acceleration on supported Snapdragon chips.
- **Its own look**: a cozy, late-night theme.
- **SillyTavern compatibility**: keep RPClient's support for character cards, lorebooks and Regex scripts.

Himitsu is free software funded by voluntary donations: no ads, no paid features, no tracking.

## Building

Requirements:

- JDK 21 or later (the JDK bundled with Android Studio works).
- Android SDK with platform 36. Android Studio creates `local.properties` with the SDK path when you open the project; for command-line builds, set `ANDROID_HOME` or create that file yourself.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Debug builds use the application ID `dev.bianperdev.himitsu.dev`, so they install alongside RPClient and any future release build.

Contributors using AI coding assistants should read [CLAUDE.md](CLAUDE.md) and [doc/coding-guidelines.md](doc/coding-guidelines.md) first.

## License

Himitsu is licensed under the [GNU General Public License v3.0 or later](LICENSE).

It includes code from RPClient, Copyright (c) 2026 Kafuu Neko, under the MIT License. The original notice is kept in [LICENSES/MIT-RPClient.txt](LICENSES/MIT-RPClient.txt). See [NOTICE](NOTICE) for details and third-party licenses.
