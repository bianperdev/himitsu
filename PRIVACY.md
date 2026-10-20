# Himitsu privacy policy

Last updated: 2026-10-20. Applies to Himitsu 0.1.0 and later, in every distribution channel (F-Droid, GitHub Releases and Google Play), until this file says otherwise.

Himitsu is a free and open source Android app for AI role-play. It is developed by volunteers and has no server of its own. **The Himitsu developers do not collect, receive or store any of your data.** Everything below describes what the app does on your device, and the only network connections it makes, which are the ones you set up.

This policy describes the code in this repository. If you build Himitsu yourself or use a modified version, check its source.

## Summary

- Your chats, characters, lorebooks, settings and API keys stay on your device, encrypted.
- Himitsu connects only to the AI endpoint you configure, and to Hugging Face when you start a model download. Nothing else.
- No analytics, no telemetry, no crash reporting, no ads, no Google Play Services, no third-party SDK that makes network calls.
- Android backups and device-to-device transfers export nothing from Himitsu.
- Uninstalling Himitsu deletes everything it stored, except files you exported yourself.

## What Himitsu stores on your device

All of the following lives in Himitsu's private app storage, which other apps cannot read.

### Encrypted databases

Himitsu uses three [SQLCipher](https://www.zetetic.net/sqlcipher/) databases (AES-256):

| File | Contents |
|---|---|
| `primary.sqlite` | Chats, group chats, stories, characters, lorebooks, Regex scripts, prompt presets, AI provider settings including API keys and custom headers, and token usage statistics (counts, model and host; never the request or response body). |
| `request_logs.sqlite` | The debug request log, only if you turn debug mode on (see below). |
| `local_models.sqlite` | The list of on-device models: names, file names and model metadata. |

**Keys.** On first start, Himitsu generates a random 256-bit master key. It is stored only in wrapped (encrypted) form in `himitsu_master.key`, inside the app's no-backup directory. It is wrapped by an AES-256-GCM key held in the Android Keystore (in StrongBox when the device has it), which never leaves the device's secure hardware or trusted environment. A separate key is derived from the master key for each database and for files and preferences, so one key never serves two purposes.

**Recovery code (optional).** You can create a recovery code in the security settings. It adds a second wrapped copy of the master key that only the code can open, so you can recover your data if the Keystore key is ever lost. Himitsu cannot show the code again; keep it somewhere safe.

### Sealed files and preferences

- **Images and drafts.** Avatars, chat images and message drafts are sealed with AES-256-GCM before they are written, and their file names are keyed hashes, so a name does not reveal a known image.
- **Text preferences.** Text settings such as your user name, persona and editable prompts are sealed with AES-GCM. Settings that are not personal text, such as the theme, the lock timeout or which disguise is active, are stored as plain values so the first screen can draw without waiting for a key.
- **Temporary files.** Files that exist only while a request or an import runs (for example a request with images) are sealed with a key kept only in memory and deleted afterwards and at the next start.

### Debug request log

Debug mode is inherited from RPClient and is **off by default**. While it is on, Himitsu keeps the raw JSON of requests to your AI endpoint and of its responses, so you can inspect them. Those logs can contain your prompts and chats, so:

- they are stored in the encrypted `request_logs.sqlite`;
- API keys, authorization headers and other credentials are replaced with `[REDACTED]` before anything is stored;
- entries older than 7 days are deleted, and at most 200 are kept;
- turning debug mode off deletes every stored log at once.

### On-device models

Model files you import or download are stored in the app's no-backup directory (`models/`). **Model files are not encrypted**: they are large, public weights, and encrypting them would slow loading. Their names and metadata are in the encrypted `local_models.sqlite`. Compiled GPU kernels are cached in the app's cache directory.

### Disguise secret code

If you enable the optional disguise, the secret code (or compass tap sequence) is never stored. Himitsu keeps only a salted hash, computed with PBKDF2-HMAC-SHA256 (310,000 iterations, 16-byte random salt), plus a count of failed attempts, in a preferences file of its own. It is kept outside the encrypted storage on purpose, so the decoy keeps working and leads to the recovery screen even if the Keystore fails. The code is never written to logs. Turning disguise off deletes the hash.

### Logs

Himitsu never writes chat content, prompts, responses, characters or API keys to Android's system log (Logcat). Release builds strip the logging calls entirely, and the native logs of the database library (SQLite error messages and SQLCipher's own log) are turned off at startup.

## What leaves your device

### Requests to the AI endpoint you configure

When you send a message, generate a story or summary, or ask the provider editor to list available models, Himitsu sends a request to the endpoint **you configured**: a cloud service such as OpenAI, Google Gemini, Anthropic, DeepSeek, OpenRouter or xAI, or a server of your own. The request contains what the model needs: the prompt built from your chat, character, persona, lorebook entries and presets, any images you attached, and your API key or custom headers for that endpoint.

That provider receives this data under its own privacy policy, which Himitsu does not control. Choose providers you trust. With an on-device model, nothing is sent anywhere.

- Himitsu does **not follow HTTP redirects** for provider requests, so your API key and prompt are never forwarded to another host; a redirect fails as an error.
- Only the system's trusted certificate authorities are accepted; certificates added by the user are not trusted.
- Plain HTTP is allowed so you can reach servers on your local network, and the provider editor warns you when you save a remote endpoint without HTTPS.
- Himitsu sends no session or user identifier of its own. OpenRouter's `session_id` is sent only if you add it to the request JSON yourself.

### Model downloads you start

The model screen lists a few suggested models. The list is built into the app, so browsing it makes no network request. When you tap download, Himitsu fetches that file from a pinned commit on Hugging Face (`huggingface.co` and its download servers). Hugging Face sees your IP address, which file you download and the default user agent of the network library; no cookies or account are used. The file is accepted only if its size and SHA-256 match the values built into the app. Before a download starts while disguise is active, Himitsu warns you that its notification shows the app name.

### Nothing else

- No analytics, telemetry, usage statistics, crash reporting or advertising.
- No Google Play Services, Firebase or other third-party SDK that makes network calls.
- No update checks, no remote configuration, no fonts or images downloaded at runtime. (Android's EmojiCompat font request is disabled: emoji use the system font.)
- Links in the About screen open in your browser only when you tap them.

## Android backups and device transfer

`allowBackup` is `false`, and Himitsu's backup and data extraction rules exclude every storage domain (app files, databases, shared preferences, external files) from cloud backup and from device-to-device transfer. The master key and models are in the no-backup directory, which Android never backs up. Moving to a new phone therefore does not copy your Himitsu data; export what you want to keep first.

## Exports and files you choose

Exports are always started by you and saved through the Android file picker to a location you choose: chats (including SillyTavern `.jsonl`), stories (`.rpstory` archives), characters, lorebooks, Regex scripts and saved images. **Exported files are not encrypted**, and their names can reveal what they contain. Copies of set-aside data exported from the security settings stay encrypted, byte for byte; set-aside model files are left out of those exports.

When you copy text from a chat, Himitsu marks the clipboard entry as sensitive, so Android hides it from clipboard previews.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Requests to your AI endpoint and model downloads you start. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Keep a model download running, with a progress notification, when you leave the app. |
| `POST_NOTIFICATIONS` | Show the model download progress and the notification of a local generation running in the background. You can refuse it. |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | Added by the AndroidX Biometric library for the app lock (`USE_FINGERPRINT` is for Android 8.0 to 8.1). Himitsu never sees your fingerprint or face data; Android only reports success or failure. |
| `dev.bianperdev.himitsu.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added by AndroidX Core. A signature permission that only Himitsu itself holds, used to keep internal broadcasts private on older Android versions. |

Components added by libraries: AndroidX ProfileInstaller declares a receiver protected by the `DUMP` permission, which only the system and adb can use; it installs ahead-of-time compilation profiles for faster startup and handles no user data. Room declares a non-exported service for its own use. This list was checked against the merged manifest of the release builds.

The disguise needs no permission. The compass uses the device's sensors, which need none.

## What the disguise cannot hide

Disguise is a discretion feature, not a security feature. Even while Himitsu looks like a calculator, compass or unit converter:

- Android's Settings (apps, notifications, battery, data and storage usage) show Himitsu with its name and icon.
- The package name `dev.bianperdev.himitsu` is visible to other apps, file managers and adb.
- Your app store's library and update history list Himitsu.
- The system fingerprint or PIN prompt, the header of some notifications (such as a model download) and screen readers in recent apps say Himitsu.
- After Android closes Himitsu in the background, reopening it can show its colors or icon for a moment, and short system messages may show its icon.
- Anyone with technical access to the phone (adb, forensic tools) can see it is installed, and the decoys and the recovery gesture are public in this source code.

## Deleting your data

- **Uninstall Himitsu**, or use Android's "Clear storage" for it, to delete everything it stored. Files you exported stay where you saved them.
- Delete individual chats, characters, lorebooks, models and providers inside the app.
- If your data can no longer be unlocked (for example the Keystore key was lost and you have no recovery code), the error screen offers **"Start over"**. It never deletes anything: your old data is moved aside, still encrypted, together with the on-device model files, which were never encrypted. The same happens when a damaged database is set aside. Set-aside copies are listed in the security settings, where you can export, restore or permanently delete them (deleting asks you to confirm and to authenticate).

## Children

Himitsu is a general tool for AI role-play and is not directed at children. What a model generates depends on the model and provider you choose.

## Changes

Changes to this policy are made in this file, and its history is public in the repository. The date at the top shows the last change.

## Contact

Questions about privacy: open an issue at https://github.com/bianperdev/himitsu/issues. To report a security or privacy vulnerability privately, use "Report a vulnerability" on the repository's Security tab, as described in [SECURITY.md](SECURITY.md). Please never include API keys or private chats.
