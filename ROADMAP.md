# Himitsu roadmap

This file coordinates the work on Himitsu. It defines the work tracks, which files each track owns, the distribution flavors every track must respect, and the merge order. Read [CLAUDE.md](CLAUDE.md) first; this file does not override it.

Paths below are relative to `app/src/main/java/me/kafuuneko/rpclient/` (written `…/`) unless they start with `app/`, `gradle/` or the repository root.

## Overview

| Order | Branch | Runs in parallel | Goal |
|---|---|---|---|
| 0 | `main` (groundwork) | No, before the tracks | Flavors and extension hooks that every track needs |
| 1 | `feat/security` | Yes | Encrypted storage, protected API keys, app lock, privacy hardening |
| 1 | `feat/aesthetic` | Yes | The "shut-in otaku" theme, typography, icon system |
| 1 | `feat/local-inference` | Yes | Local engine abstraction, llama.cpp, GenieX |
| 2 | `feat/disguise` | No, after security and aesthetic are merged | Calculator, compass and unit converter decoys |
| 3 | `chore/release-readiness` | No, after every track is merged | Documentation, donations, release builds, store metadata |

---

## Privacy audit findings

Findings from the initial audit of the RPClient code base (commit `c3ed325`). Each one is assigned to a track. Mark the box and add the commit when it is resolved.

| ID | Sev. | Finding | Location | Track |
|---|---|---|---|---|
| P-01 | Critical | API keys stored in plain text in the `llm_providers.apiKey` column. No Keystore use anywhere. | `…/libs/room/entity/LLMProvider.kt:29` | security |
| P-02 | Critical | Main database `primary.sqlite` is not encrypted. | `…/RPClientApp.kt:170` | security |
| P-03 | Critical | `allowBackup="true"`; backup and device-transfer rules only exclude request logs, so `primary.sqlite` (chats, API keys), `shared_prefs/AppModel.xml` and `app_repository/` (avatars, images) are exported. | `app/src/main/AndroidManifest.xml:9`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` | security |
| P-04 | High | Custom headers (may hold `Authorization`) stored in plain text and shown unmasked in the editor. | `…/libs/room/entity/LLMProvider.kt:33`, `…/feature/llmprovideredit/LLMProviderEditViewModel.kt:339` | security |
| P-05 | High | User persona, user name and about 25 editable prompts stored in plain-text Kotpref (`AppModel`). | `…/libs/AppModel.kt:177-251` | security |
| P-06 | High | Avatars and chat images stored as plain files in `app_repository/`. | `…/libs/room/repository/FileRepository.kt:54` | security |
| P-07 | High | Debug mode (off by default) stores full raw prompts and responses in the unencrypted `request_logs.sqlite`, is not cleared when debug mode is turned off, and has no retention limit. | `…/libs/room/repository/LLMRequestLogRepository.kt:43-53`, `…/feature/main/MainViewModel.kt:1378` | security |
| P-08 | Medium | Gemini API key sent as a URL query parameter instead of the `x-goog-api-key` header. | `…/libs/llm/adapter/GeminiLLMClient.kt:175`, `…/libs/llm/catalog/adapter/GeminiModelCatalogClient.kt:30` | security (narrow edit in local-inference files, see ownership) |
| P-09 | Medium | Clear-text HTTP allowed globally and user-installed CAs trusted. Needed for LAN endpoints, but there is no warning. | `app/src/main/AndroidManifest.xml:18`, `app/src/main/res/xml/network_security_config.xml:3,6` | security |
| P-10 | Medium | Full request JSON (the whole prompt) written to `cacheDir/image-requests/`; can survive up to 24 h after a crash. | `…/libs/media/MessageImageRuntime.kt:50-53` | security |
| P-11 | Medium | Clipboard copies of chats, stories and request logs are not marked `EXTRA_IS_SENSITIVE`. | `…/feature/chat/ChatActivity.kt:105`, `…/feature/groupchat/GroupChatActivity.kt:81`, `…/feature/storyeditor/StoryEditorActivity.kt:83`, `…/feature/requestlog/RequestLogActivity.kt:57` | security |
| P-12 | Medium | No `FLAG_SECURE`: content visible in recents thumbnails and screenshots. | `…/libs/core/CoreActivity.kt:34` | security |
| P-13 | Medium | API key input does not use a password keyboard type, so keyboards may learn it. | `…/ui/dialog/AppInputDialog.kt:75` | security |
| P-14 | Medium | `printStackTrace()` reaches Logcat in release builds; no ProGuard rule strips logging. | `…/utils/GsonUtils.kt:15`, `app/proguard-rules.pro` | security |
| P-15 | Medium | Exports (chat, story, characters) are plain text. They are user-initiated through the system file picker; consider an optional encrypted export. | `…/feature/chat/ChatActivity.kt:43`, `…/feature/storyeditor/StoryEditorActivity.kt:34-42` | security (decision) |
| P-16 | Medium | A stable per-conversation routing ID is sent to OpenRouter by default. | `…/libs/llm/RoutingSessionId.kt:10-24`, `…/libs/llm/model/LLMModels.kt:14` | security (decision) |

Already clean: no telemetry, no Firebase or Play Services, no third-party network SDKs, no `Log.*` calls, only the launcher activity is exported, the only permission is `INTERNET`, request logs are excluded from backup, and API keys are never displayed or logged.

Other findings that are not privacy issues:

| ID | Finding | Location | Owner |
|---|---|---|---|
| O-01 | `runBlocking` on the main thread in `Application.onCreate` (data upgrade and file cleanup). It will matter more once opening the database needs a key. | `…/RPClientApp.kt:82-86` | security |
| O-02 | Koin is a release candidate (`4.0.0-RC1`). | `gradle/libs.versions.toml` | release-readiness |
| O-03 | 14 `.idea/` files are tracked and add merge noise. | `.idea/` | release-readiness |
| O-04 | No `dependenciesInfo { includeInApk = false }`; F-Droid's scanner rejects the encrypted dependency metadata block. | `app/build.gradle.kts` | groundwork |
| O-05 | `.claude/agents/privacy-reviewer.md` is in Spanish and numbers two items as "10." | `.claude/agents/privacy-reviewer.md` | release-readiness |

---

## Distribution flavors

Every track must respect these flavors. They are added to `app/build.gradle.kts` in the groundwork step, before any track starts.

One flavor dimension, `channel`:

| Flavor | Purpose | GenieX | Donation links | Google Play links ("rate the app", `market://`) | Google services |
|---|---|---|---|---|---|
| `fdroid` | F-Droid, built from source by F-Droid | **Never** | Yes | **No** | **Never** |
| `github` | Signed APKs on GitHub Releases | Only if the maintainer approves the licensing (see below) | Yes | No | Never |
| `play` | Google Play | Only if the maintainer approves the licensing | **No, not even indirect ones** | Yes | Never (none are needed) |

Rules:

- All flavors share `applicationId = "dev.bianperdev.himitsu"`. F-Droid signs with its own key, so users cannot switch between the F-Droid build and the others without reinstalling; that is expected and must be documented, not worked around.
- Donation code and resources live only in a source set used by `fdroid` and `github` (proposed: `app/src/donations/`, added to both flavors' `java.srcDirs` and `res.srcDirs`). The `play` APK must not contain the donation screen, its strings or its URLs. Verify by inspecting the `play` APK resources before every Play release.
- Play-specific code (store link, "rate the app" card) lives only in `app/src/play/`. Shared code calls it through an interface with a no-op implementation in `app/src/main/` or in the other flavors.
- GenieX lives only in flavor source sets (`app/src/github/`, `app/src/play/`) and is wired in through the local engine abstraction. The `fdroid` build must not reference it, not even as a `compileOnly` dependency.
- **GenieX licensing is undecided.** The SDK is BSD-3-Clause, but its native Qualcomm runtime may be proprietary, which conflicts with the GPL. Until the maintainer chooses an option (a GPL linking exception limited to those components, GitHub-only distribution, or no GenieX), GenieX may be developed behind the flavor boundary but must not be shipped.
- Build types stay as they are: `debug` (application ID suffix `.dev`) and `release`.
- Every published binary must have a matching git tag with its source, as the GPL requires.

---

## Step 0: groundwork on `main`

A short set of commits on `main`, made before the track branches are created, so that the tracks only touch shared files in an additive way.

- [x] Add the `channel` flavor dimension with `fdroid`, `github` and `play`, plus the `app/src/donations/` and `app/src/play/` source sets. Update the README build command.
- [x] Add `dependenciesInfo { includeInApk = false; includeInBundle = false }` (O-04).
- [x] Koin hook (see below).
- [x] Settings hook (see below).
- [x] Reserve the disguise activity-alias block in `app/src/main/AndroidManifest.xml`.
- [x] Raise the Gradle heap to 4 GB: building several variants in one invocation ran KSP out of memory with 2 GB.
- [x] Verify: `./gradlew assembleFdroidDebug assembleGithubDebug assemblePlayDebug testFdroidDebugUnitTest`.

Done when the three flavors build, the app runs unchanged, and the hooks exist.

### How to use the hooks

**Koin.** Declare the track's module in its own file and append it to `himitsuModules` in `…/HimitsuModules.kt`:

```kotlin
internal val himitsuModules: List<Module> = listOf(
    securityModule, // feat/security
)
```

**Settings.** Give the track its own settings Activity and ViewModel, register the Activity in the track's manifest block, and append an entry to `himitsuSettingsEntries` in `…/feature/main/model/HimitsuSettingsEntry.kt`. Entries appear on the settings page, after the debug panel and before "About":

```kotlin
internal val himitsuSettingsEntries: List<HimitsuSettingsEntry> = listOf(
    HimitsuSettingsEntry(
        titleRes = R.string.security_settings_title,
        subtitleRes = R.string.security_settings_desc,
        icon = Icons.Rounded.Lock,
        activity = SecuritySettingsActivity::class.java
    ), // feat/security
)
```

Exception: the aesthetic track owns `AppearancePanel` in `…/feature/main/ui/MainLayout.kt` and may extend it (for example with the dynamic color switch), together with `MainAppearanceSettingsState` and its handlers in `MainViewModel`.

Each track adds one line or one entry per hook, so merges only need to keep both lines.

---

## File ownership

### Rules for every track

- A track may freely edit the files it **owns**.
- A track may only make **additive** changes to **shared** files: add a line, an entry, a block or a new file. It must not reorder, reformat or rewrite what is there.
- New code goes in new files and packages whenever possible (this also keeps upstream merges easy).
- **Strings:** never add strings to the existing `strings.xml` files. Each track adds `res/values/strings_<track>.xml` plus the same file in every locale directory (at least English and Spanish fully translated; follow `doc/coding-guidelines.md` for the other locales). A track may edit existing strings only for findings it owns.
- **Preferences:** do not add fields to `…/libs/AppModel.kt`. Create a new Kotpref object in the track's package (for example `SecurityPrefs`). Sensitive data never goes into Kotpref.
- **Koin:** each track declares its own module in its own file (for example `…/libs/security/SecurityModule.kt`) and adds one entry to the module list in `RPClientApp.kt`.
- **Database schema:** schema versions are assigned in merge order. A track that changes the schema bumps the version from whatever `main` has when it rebases, adds an auto-migration and commits the exported schema in `app/schemas/`. Only one track bumps the version at a time; say so in the pull request.
- **Dependencies:** add them to `gradle/libs.versions.toml` and `app/build.gradle.kts` in a block commented with the track name. Every new dependency is added to `NOTICE` and to [Third-party licenses](#third-party-licenses) in the same commit.
- **Manifest:** add components in a block commented with the track name. Never touch the disguise block.
- Each track may edit only its own section of this file, plus additive rows in the licenses table.

### Shared files (additive only, for every track)

`…/HimitsuModules.kt`, `…/feature/main/model/HimitsuSettingsEntry.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `NOTICE`, `ROADMAP.md`.

---

## Track: `feat/security`

**Goal:** no sensitive data stored in plain text on the device, and the app can be locked.

**Owns:**
- New: `…/libs/security/**`, `…/feature/lock/**`, `res/values*/strings_security.xml`
- `…/RPClientApp.kt`: the database builder blocks and the startup sequence in `onCreate` (O-01)
- `…/libs/room/AppDatabase.kt`, `…/libs/room/RequestLogDatabase.kt`, `…/libs/room/entity/LLMProvider.kt` (`apiKey` and `customHeadersJson` storage only)
- `…/libs/room/repository/LLMRequestLogRepository.kt`, `…/libs/room/repository/FileRepository.kt`, `…/feature/requestlog/**`
- `…/libs/core/CoreActivity.kt` (lock check, `FLAG_SECURE`)
- `…/libs/media/MessageImageRuntime.kt` (temporary request file)
- `…/utils/GsonUtils.kt`, `app/proguard-rules.pro`
- `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`, `network_security_config.xml`
- `…/ui/dialog/AppInputDialog.kt` (keyboard type only)
- Narrow edits allowed in files owned by local-inference: the Gemini key header (P-08) in `GeminiLLMClient.kt` and `GeminiModelCatalogClient.kt`, and the masking of custom headers in `LLMProviderEditViewModel.kt` (P-04). Coordinate in the pull request.

**Tasks, in order:**
1. Design document (options, trade-offs and a recommendation for each), approved by the maintainer: SQLCipher library and Room integration (verify the current version and license), database key management (Keystore-wrapped random key, optional PIN-derived "paranoid mode"), API key storage, app lock with `androidx.biometric`, hardening.
2. Master key generation and storage, with unit tests for the parts that do not depend on the Keystore.
3. Encrypted Room databases (P-02, and the request log database for P-07). Note: the new `applicationId` means there is no RPClient data to migrate; only earlier Himitsu debug builds may have a plain database.
4. API key and custom header protection (P-01, P-04, P-13). API keys live in Room, not in Kotpref; the migration moves them out of the plain database.
5. App lock: biometrics with device credential fallback, re-lock after a configurable time in background (immediately, 1 min, 5 min), lock screen.
6. Hardening: backup rules (P-03), Kotpref and file encryption (P-05, P-06), debug log retention and redaction (P-07), cache file (P-10), clipboard (P-11), optional `FLAG_SECURE` (P-12), logging (P-14), clear-text warning (P-09), Gemini header (P-08), main-thread startup (O-01). Present P-15 and P-16 as decisions.
7. Final review: go through the findings table and mark what is resolved.

**Contracts this track must document here before it is merged:**
- How the database is opened (key retrieval, error handling) and what other tracks must call.
- **Unlock extension point:** an interchangeable "unlocker" entry so that `feat/disguise` can start authentication from a decoy. The lock never has a bypass: every entry point leads to authentication.
- The lock screen composable API, so `feat/aesthetic` can style it with theme tokens.

### Contracts (feat/security)

Design and reasons: [doc/himitsu/security-design.md](doc/himitsu/security-design.md).

**Opening the databases.** `AppDatabase` and `RequestLogDatabase` are SQLCipher databases. Other tracks keep using `get<AppDatabase>()` from Koin exactly as before; nothing else is required.

- The builders in `RPClientApp.kt` pass `SecureStorageManager.openHelperFactory(EncryptedDatabase.…)`. Do not build another `Room.databaseBuilder` for these files, and never open them with `android.database.sqlite`: the file is unreadable without the key.
- The key is derived from the master key per database (`KeyPurpose`). The first query waits on a background thread until the key is loaded; Room never queries on the main thread, so do not enable `allowMainThreadQueries`.
- If the key cannot be loaded, queries keep waiting and the app gate shows the error screen with retry, recovery code and start over. Nothing is deleted automatically, and no screen crashes.
- A new database file must be added to `EncryptedDatabase` with its own `KeyPurpose` so it is encrypted, migrated from plain form and moved aside on "start over".
- Schema versions and auto-migrations work as before; this track did not change the schema.
- Startup work that needs the database (upgrades, file cleanup) runs in the block passed to `SecureStorageManager.start` in `RPClientApp.onCreate`, off the main thread. Screens stay behind the gate curtain until it finishes.

**Unlock extension point.** `UnlockEntry` (`…/libs/security/AppGate.kt`), resolved from Koin, opens the gate screen. A decoy calls `get<UnlockEntry>().open(context)` from its secret entry. It only shows the gate screen; authentication happens there, so there is no bypass. `AppGate.state` tells whether content may be shown.

- Unlocking happens only in the `BiometricPrompt` success callback inside `AppLockManager`; there is no public unlock method. The one exception is a device without any screen lock, where the gate screen shows a warning and lets the user continue (removing the system lock already required the credential).
- The app lock settings (`SecurityPrefs`: on/off, timeout) are not secrets; they live in Kotpref.
- Note for `feat/disguise`: the system authentication prompt shows the application label (seen as "Himitsu (Dev)" on Android 17). Check what it shows when an activity-alias is the launcher entry, and document it as a known limit if it cannot be hidden.

**Lock screen styling.** `LockScreenFrame(icon, title, message, modifier, actions)` in `…/feature/lock/ui/LockLayout.kt` draws every gate state and reads only `MaterialTheme` tokens, so `feat/aesthetic` can restyle it through the theme.

### Findings status (feat/security)

Final review of the [privacy audit findings](#privacy-audit-findings) on this branch.

| ID | Status | Commits | Notes |
|---|---|---|---|
| P-01 | Resolved | `c7b1141`, `1bb63d2` | API keys stay in `llm_providers.apiKey`, now inside the SQLCipher database; plain databases from earlier builds are migrated and the plain copy deleted after verification. No key was ever in Kotpref. |
| P-02 | Resolved | `c7b1141` | `primary.sqlite` is SQLCipher with a per-database key derived from the Keystore-wrapped master key. |
| P-03 | Resolved | `a39fd4e`, `022e835` | `allowBackup="false"`; every domain excluded from full backup, cloud backup and device transfer. |
| P-04 | Resolved | `1bb63d2` | Header values are masked in the editor; stored inside the encrypted database. |
| P-05 | Resolved | `309abf8` | `AppModel` strings are sealed (AES-GCM, key derived from the master key). Numbers and booleans stay plain. |
| P-06 | Deferred (decision) | | Maintainer decision (2026-10-20): done later in its own branch, following the plan in "Follow-ups". Avatars and chat images stay plain until then. |
| P-07 | Resolved | `c7b1141`, `7d27cba` | Encrypted database, credentials redacted, 7 days / 200 entries, wiped when debug mode is turned off. |
| P-08 | Resolved | `46a3959` | `x-goog-api-key` header. |
| P-09 | Resolved (decision) | `f24e6ba`, `3fa3047` | Warning when saving a plain-HTTP remote provider; clear text stays allowed for LAN servers. Only system CAs are trusted (maintainer decision, 2026-10-20). |
| P-10 | Partly | `0d782e4` | Leftover request files are deleted at startup. The file is still plain while a request is in flight; encrypting it needs a change in `MultimodalWireCodec` (local-inference). |
| P-11 | Resolved | `c3b12b1` | `IS_SENSITIVE` on copies of messages, stories, characters, prompts and logs. |
| P-12 | Resolved | `655a726` | Optional `FLAG_SECURE`; with the lock on, recents previews are hidden on Android 13+. |
| P-13 | Resolved | `1bb63d2` | Password keyboard without autocorrect. |
| P-14 | Resolved | `2425e39` | `printStackTrace` removed; R8 strips `Log`, `printStackTrace` and SQLCipher logging in release (verified on the dex). |
| P-15 | Deferred (decision) | | Optional encrypted export, after P-06. |
| P-16 | Resolved (decision) | `d31820e` | The OpenRouter routing ID is no longer in the default template; opt-in through the request JSON. |
| O-01 | Resolved | `c7b1141`, `77ffea0` | Startup work runs off the main thread behind the gate. The theme stays plain so the first frame never waits; the first read of a sealed `AppModel` string may still perform one Keystore operation on the main thread (P-05). |

### Follow-ups (feat/security)

- **P-06 plan (deferred by the maintainer):** encrypt `app_repository/` files with AES-256-GCM in 64 KiB segments under the `Files` key (no new dependency); name files by HMAC instead of the plain SHA-256 so names do not reveal known images; replace `File`-based leases with streams (`BitmapFactory.decodeStream`, `ExifInterface(InputStream)`); adapt `MessageImageRuntime`, `CharacterCardRepository.exportPng` and `ChatArchiveRepository` (narrow edits outside this track); migrate existing files in the background at startup.
- **P-10 rest:** write the image request file with an ephemeral in-memory key and decrypt while streaming (`MultimodalWireCodec`, local-inference).
- **Paranoid mode:** a PIN or passphrase key slot with Argon2id (`argon2kt`, MIT); the key file format already supports extra slots.
- **Keystore error screen:** add an option to export the encrypted files through the system file picker, and a way to delete `.unrecoverable-*` copies left by "start over".
- **F-Droid:** SQLCipher ships prebuilt native libraries from Maven Central; confirm the F-Droid scanner accepts them or build `sqlcipher-android` from source in the recipe (release-readiness).
- **Privacy review (2026-10-20), not fixed in this track:**
  - Ship the SQLCipher BSD-3 notice inside the app (licenses screen or packaged `NOTICE`) before any release (release-readiness).
  - Text copied through the system selection toolbar (`MarkdownMessage`, `PromptInspectorDialog`) is not marked `IS_SENSITIVE`; needs a clipboard manager that flags it.
  - Turning the lock off and creating, replacing or removing the recovery code need no fresh authentication; consider a prompt for these actions.
  - `AppDatabaseAutoMigrationSpec` (2 to 3) still injects `session_id` into OpenRouter providers migrated from RPClient databases, and the provider editor's example JSON shows it.
  - Chat, persona and character inputs do not set the IME "no personalized learning" flag.
  - Recents previews: on Android 12 and earlier only `FLAG_SECURE` hides them; the app lock alone does not.
- **Build:** a combined `assembleFdroidDebug assembleGithubDebug assemblePlayDebug testFdroidDebugUnitTest` once failed in KSP with Room errors on `MutableDao<T>`; rerunning passed. Probably KSP incremental state; watch for it.

**Done when:** P-01 to P-14 are resolved or have an approved decision; a Keystore failure never deletes data silently (a clear error and options are shown); no key, passphrase or API key appears in logs or tests; `assembleDebug` for every flavor and the unit tests pass; the privacy-reviewer agent marks the branch fit to merge.

## Track: `feat/aesthetic`

**Goal:** Himitsu's own visual identity, the "shut-in otaku" look (see CLAUDE.md), on Material 3.

**Owns:**
- `…/ui/theme/**`, `…/libs/theme/**`
- `app/src/main/res/values/themes.xml`, `colors.xml`, `ic_launcher_background.xml`
- New: `app/src/main/res/font/**`, `res/values*/strings_aesthetic.xml`
- Launcher icons: `app/src/main/res/mipmap-*/**`, `app/src/main/res/drawable/ic_launcher_*`, `app/src/main/res/drawable/app_logo.png`, splash screen resources
- `…/ui/widgets/**`, `…/ui/message/**` (appearance only)
- Feature `ui/*Layout.kt` files: replacing hard-coded colors and text styles with theme tokens only, never structure or behavior. **Exception:** do not touch `…/feature/llmprovideredit/**`, `…/feature/llmproviderlist/**` or `…/feature/lock/**` until their owning tracks are merged; do those in a follow-up commit afterwards.

**Tasks, in order:**
1. Proposal, approved by the maintainer: complete dark Material 3 `ColorScheme` with a contrast table (WCAG AA), secondary light theme, OFL fonts bundled in the APK with a fallback for CJK and the other app languages, identity details, the icon system and two or three icon concepts.
2. Color tokens and both color schemes. Dynamic color as a setting, off by default.
3. Typography and shapes.
4. Replace hard-coded colors with theme tokens (except the files listed above).
5. Identity details and empty states.
6. Icon (adaptive, with a monochrome version for Android 13+) and splash screen.
7. Compose previews of the main screens in both themes.

**Contract this track must document here before it is merged:**
- **Icon system:** where launcher icons live and how alternative icons are added, so `feat/disguise` can add generic calculator, compass and converter icons.

**Done when:** text contrast meets WCAG AA in both themes; no fonts or images are downloaded at runtime; every font and asset is original or has a verified free license recorded in `NOTICE`; animations respect the system "remove animations" setting; screen structure and behavior are unchanged; every flavor builds.

## Track: `feat/local-inference`

**Goal:** offline role-play with models running on the device, through the existing prompt pipeline (presets, macros, lorebooks, Regex, Prompt Inspector).

**Extension point (from the audit):** implement `LLMClient` (`…/libs/llm/LLMClient.kt:16`), add an `LLMProviderProtocol` value (`…/libs/llm/model/LLMModels.kt:54`) and its branch in `LLMClientFactory.create` (`…/libs/llm/LLMClientFactory.kt:32`). The prompt pipeline runs before the adapter, so it is reused unchanged. Enum values are stored as text, so a new value needs no schema migration.

**Owns:**
- New: `…/libs/llm/local/**`, `…/feature/localmodels/**`, `app/src/main/cpp/**`, the llama.cpp submodule (proposed: `third_party/llama.cpp`), `res/values*/strings_local_inference.xml`
- GenieX code in `app/src/github/**` and `app/src/play/**` (local engine part only)
- `…/libs/llm/**`, except the narrow security edits listed in the security track
- The `when` branches in `…/libs/llm/catalog/LLMModelCatalogClientFactory.kt:15`, `…/libs/llm/model/LLMProviderCapabilities.kt:17`, `…/libs/llm/adapter/RequestBodyPatch.kt:114`, `…/libs/llm/adapter/MultimodalWireCodec.kt:74`, `…/feature/tokenusage/ui/TokenUsageLayout.kt:947`
- Protocol checks in `…/libs/prompt/PromptPostProcessor.kt:225`, `…/libs/prompt/PromptRequestFinalizer.kt:107`, `…/libs/prompt/PromptTokenizer.kt:143-161`
- `…/feature/llmprovideredit/**`, `…/feature/llmproviderlist/**`

**Tasks, in order:**
1. Design document, approved by the maintainer: engine interface, chat templates and how the Prompt Inspector shows them, llama.cpp from source (NDK, CMake, pinned commit) versus existing bindings, GenieX runtime detection, native library conflicts between llama.cpp and GenieX, licensing options for GenieX, model import and download, memory and thermal management, and a short list of recommended models.
2. `LocalInferenceEngine` interface and provider integration, with a fake engine that streams text, to validate the UI and the Prompt Inspector.
3. llama.cpp engine: load an imported GGUF, streaming generation, cancellation, memory release.
4. Model management screen: import through the Storage Access Framework; optional download only on explicit user action, with SHA-256 verification, free-space check and progress.
5. GenieX engine, only in the flavors that allow it, with runtime compatibility detection; hidden on unsupported devices.
6. Performance settings (threads, context size, GPU or NPU) with sensible defaults.

**Done when:** a downloaded or imported GGUF can be used offline with the same presets, lorebooks, Regex and Prompt Inspector as remote providers; no network call is added except the explicit model download; nothing heavy runs on the main thread; generation can be cancelled and the model is released; the `fdroid` build contains only source-built llama.cpp and no GenieX; unsupported devices never crash; every new license is in `NOTICE` and in the table below; every flavor builds.

## Track: `feat/disguise` (after security and aesthetic are merged)

**Starts** from `main` once `feat/security` (app lock and unlock extension point) and `feat/aesthetic` (icon system) are merged. It does not run in parallel with them.

**Goal:** an optional disguise so Himitsu looks like an everyday app at a casual glance: a calculator, a compass and a unit converter. This is discretion, not security (see CLAUDE.md, "Disguise mode").

**Owns:**
- The reserved block between `BEGIN/END disguise activity-aliases` in `app/src/main/AndroidManifest.xml`, and moving the `LAUNCHER` intent filter from `MainActivity` to an alias
- New: `…/feature/disguise/**`, `…/libs/disguise/**`, `res/values*/strings_disguise.xml`, decoy icons under the icon system defined by the aesthetic track

**Tasks, in order:**
1. Design document, approved by the maintainer: activity-alias switching and launcher quirks; functional decoys; secret shortcuts that always lead to authentication; recovery; leaks (recents, notifications, shortcuts, share targets); honest limits; store policies per flavor.
2. Activity aliases and the appearance switch, with warnings to the user.
3. Calculator with secret code and recovery.
4. Integration with the app lock.
5. Leak fixes.
6. Compass and unit converter.
7. Disguise settings screen.
8. Tests: calculator logic, code verification, alias switching, and proof that no path skips authentication.

**Done when:** disguise is off by default; no entry point skips authentication; the secret code is stored only as a salted KDF hash and never logged; wrong entries behave like normal decoy input; decoys need no new permissions and make no network calls; toggling never loses data or locks the user out; the limits are explained honestly in the UI; the privacy-reviewer agent marks the branch fit to merge.

---

## Merge order

1. **`feat/security` first.** It changes how the databases are opened and how the app starts, and it defines the lock and unlock contracts that two other tracks depend on. Merging it first means every later schema change and every UI built on the lock lands on top of encrypted storage.
2. **`feat/aesthetic` second.** It touches many layout files, but only their appearance. Merging it early shortens the time other branches live with a different theme, and it unblocks `feat/disguise` (icon system).
3. **`feat/local-inference` third.** It is the largest track (native code, CMake, flavors) and the most likely to take longest. It mostly touches its own files, so rebasing it onto a `main` that already has security and aesthetic is cheap. After it is merged, aesthetic does its follow-up on `feat/llmprovideredit` and `feat/llmproviderlist`.
4. **`feat/disguise`** once 1 and 2 are merged. It can run while local inference is still in progress.
5. **`chore/release-readiness`** last.

Before every merge: rebase onto `main`, build every flavor, run the unit tests, and run the privacy-reviewer agent on the branch.

---

## Phase: Release (`chore/release-readiness`)

Starts when every track, including disguise, is merged.

- [ ] Audit the full git history for secrets and personal data. This is the requirement for making the repository public.
- [ ] Check the "Himitsu" name for conflicts with other apps, projects or trademarks.
- [ ] Verify licensing: official GPLv3 text in `LICENSE`, intact RPClient notice in `LICENSES/MIT-RPClient.txt`, SPDX headers on new files, and a GenieX exception text if one was approved.
- [ ] Full README, `PRIVACY.md` (must match what the code does), `SECURITY.md`, `CONTRIBUTING.md`, issue and pull request templates, `.github/FUNDING.yml`.
- [ ] Donation screen in `fdroid` and `github` only; open source licenses screen with every dependency, including native ones.
- [ ] `fastlane/metadata/android/`: store descriptions, changelogs and screenshots still describe RPClient. Rewrite them for Himitsu in every locale.
- [ ] `fdroid/me.kafuuneko.rpclient.yml` and `fdroid/README.md`: still declare `License: MIT`, the RPClient author and the upstream repository. Replace them with Himitsu metadata and list the expected anti-features.
- [ ] `metadata/images/`: RPClient screenshots used by the old README.
- [ ] `README_ZH.md`: Chinese README of RPClient. Translate the Himitsu README or remove it.
- [ ] `.github/workflows/release.yml`: tag trigger disabled. Adapt the signing secrets, the artifact names (`RPClient-*.apk`), the `origin/master` branch check and the release title, build the `github` flavor with SHA-256 checksums, then re-enable it.
- [ ] About screen: the repository and feedback links (`AppModel.GITHUB_REPO`, `…/feature/about/`) still point to RPClient. Point them to Himitsu, keep an explicit credit to RPClient, and link the licenses screen.
- [ ] About screen: move the Google Play link and the "rate the app" card into the `play` flavor.
- [ ] Reproducible builds: evaluate what is needed.
- [ ] Adult content: report what Google Play and F-Droid require (content rating, warnings) so the maintainer can decide.
- [ ] O-02, O-03, O-05.

---

## Third-party licenses

Current dependencies (audited at commit `c3ed325`). Keep this table and `NOTICE` up to date with every dependency change.

### Shipped in the app

| Dependency | Version | License | Network calls of its own |
|---|---|---|---|
| AndroidX core-ktx, exifinterface, lifecycle-runtime-ktx, activity-compose | 1.18.0, 1.3.6, 2.10.0, 1.13.0 | Apache-2.0 | No |
| Jetpack Compose BOM (ui, foundation, material3, material3-adaptive, material-icons-extended) | 2026.05.00 | Apache-2.0 | No |
| AndroidX Room (runtime, ktx) | 2.8.4 | Apache-2.0 | No |
| Kotlin stdlib, kotlin-reflect | 2.0.21 | Apache-2.0 | No |
| kotlinx-serialization-core | 1.8.1 | Apache-2.0 | No |
| Koin (koin-android) | 4.0.0-RC1 | Apache-2.0 | No |
| Kotpref | 2.13.2 | Apache-2.0 | No |
| OkHttp (with Okio) | 4.12.0 | Apache-2.0 | Only the requests the app makes |
| Gson | 2.11.0 | Apache-2.0 | No |
| JTokkit | 1.1.0 | MIT | No |
| AndroidX biometric, lifecycle-process (feat/security; lifecycle-process was already a transitive dependency) | 1.1.0, 2.10.0 | Apache-2.0 | No |
| SQLCipher for Android (`net.zetetic:sqlcipher-android`, bundles LibTomCrypt, public domain), pulls in AndroidX sqlite 2.7.0 (feat/security) | 4.19.1 | BSD-3-Clause (notice reproduced in `NOTICE`) | No |

Notable transitive dependencies (appcompat, fragment, sqlite, kotlinx-coroutines, atomicfu, error_prone_annotations) are Apache-2.0. There is no Google Play Services, Firebase, ML Kit or analytics SDK.

Bundled resources: the launcher icon background vector is from the AOSP template (Apache-2.0). The provenance of `app/src/main/res/drawable/app_logo.png` is unverified; the aesthetic track replaces it.

### Build and test only (not shipped)

| Dependency | License |
|---|---|
| Android Gradle Plugin 9.0.1, Compose compiler plugin, KSP, Gradle wrapper 9.5.0 | Apache-2.0 |
| Room compiler, AndroidX Test, Espresso, Compose UI test | Apache-2.0 |
| JUnit 4.13.2 | EPL-1.0 (not GPL-compatible, acceptable because it is never shipped) |
| Hamcrest 1.3 | BSD-3-Clause |

No current dependency is incompatible with GPLv3.
