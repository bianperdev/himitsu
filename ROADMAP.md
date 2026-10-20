# Himitsu roadmap

This file coordinates the work on Himitsu. It defines the work tracks, which files each track owns, the distribution flavors every track must respect, and the merge order. Read [CLAUDE.md](CLAUDE.md) first; this file does not override it.

Paths below are relative to `app/src/main/java/me/kafuuneko/rpclient/` (written `…/`) unless they start with `app/`, `gradle/` or the repository root.

## Overview

| Order | Branch | Runs in parallel | Goal | Status |
|---|---|---|---|---|
| 0 | `main` (groundwork) | No, before the tracks | Flavors and extension hooks that every track needs | Done |
| 1 | `feat/security` | Yes | Encrypted storage, protected API keys, app lock, privacy hardening | Done, merged 2026-10-20 (`acc6de1`) |
| 1 | `feat/aesthetic` | Yes | The "shut-in otaku" theme, typography, icon system | Done, merged 2026-10-20 (`ab1caef`) |
| 1 | `feat/local-inference` | Yes | Local engine abstraction, llama.cpp (GenieX moved to `feat/geniex`) | Done, merged 2026-10-20 (`529cdf5`) |
| 2 | `feat/disguise` | No, after security and aesthetic are merged | Calculator, compass and unit converter decoys | Next |
| 3 | `chore/release-readiness` | No, after every track is merged | Documentation, donations, release builds, store metadata | Not started |

Side branches that run in parallel with `feat/disguise`: `feat/security-followups` (deferred security work), `feat/geniex` (blocked on the QAIRT EULA) and the local-inference follow-ups. See [Pending after stage 1](#pending-after-stage-1).

---

## Stage 1 closed (2026-10-20)

`feat/security`, `feat/aesthetic` and `feat/local-inference` are merged into `main`, and their branches and worktrees are deleted. Checks run on `main` at `d16392f` before closing:

- `assembleFdroidDebug assembleGithubDebug assemblePlayDebug` and `assembleFdroidRelease assembleGithubRelease assemblePlayRelease` (R8) build.
- Unit tests: 343 per flavor (`test{Fdroid,Github,Play}DebugUnitTest`), no failures.
- Instrumented tests: they did not compile because `MultimodalImageIntegrationTest` lacked the `Local` branch; fixed while closing the stage. `connectedFdroidDebugAndroidTest`: 104 tests, no failures (spare AVD `Himitsu_LocalInference`, Android 17).
- privacy-reviewer on `c3ed325..main`: fit to close the stage, with follow-ups. No new Critical, High or Medium findings; the new Low ones are listed below.
- Integration tests on the maintainer's phone combining the three tracks (local model chat with the lock, encrypted storage and the theme; multitasking during a generation; reboot; theme switching with a chat open).

### Pending after stage 1

Grouped by the branch that will do them. Details stay in each track's section.

**`feat/security-followups`** (worktree `himitsu-security-followups`, in progress):
- P-06 (encrypt avatars and chat images) and the rest of P-10 (in-flight image request file).
- The 7 findings of the pre-merge privacy review and the items of the first and second privacy reviews (see the security track's Follow-ups).
- `GatedDropdownMenu`, now that `feat/aesthetic` is merged.
- New (stage 1 review): `openrouter_session_affinity_note` in every locale still says Himitsu sends a session ID in Auto mode; since P-16 it is only sent when `session_id` is in the request JSON (or the provider was migrated from RPClient).

**Local inference follow-ups** (new branch from `main`, not started):
- Due before the first public release: encrypt `local_models.sqlite` through `EncryptedDatabase`, and make "start over" set the registry and `noBackupFilesDir/models/` aside. Remove the stale TODO in `…/libs/llm/local/model/LocalModelDatabase.kt` at the same time.
- Decision: 32-bit devices (`abiFilters`).
- `LocalModelDownloadService.onTimeout`, OpenCL loaded only with the GPU on, and the low findings of the final review.
- Jinja templates: a recursion or step limit needs an upstream patch.

**`feat/geniex`** (worktree `himitsu-geniex`, blocked): the maintainer must read the QAIRT EULA and pick a licensing option; then rebase onto `main` and isolate GenieX's native libraries from Himitsu's `libggml-*.so`.

**`feat/disguise`**: the notification header label, the system authentication prompt label, and the leaks listed in its section.

**`chore/release-readiness`** (added to the Release phase below):
- SQLCipher BSD-3 notice in the app; SQLCipher prebuilt libraries and the F-Droid scanner; llama.cpp, JSON for Modern C++, libc++ and OpenCL-Headers in the licenses screen.
- Declare the `dataSync` foreground service type in the Play Console (or use a user-initiated data transfer job).
- New (stage 1 review): in the `play` build, the About links to the GitHub repository become an indirect donation link as soon as `FUNDING.yml` or donation text exists there. Remove them from `play` or point them to a page without funding links.
- New (stage 1 review): `.idea/deploymentTargetSelector.xml` records the serial number of the device last used to run the app. Never commit it; untrack it and `deviceManager.xml` as part of O-03.
- New (stage 1 review): before the history audit, replace the specific test device mentions (this file, `…/libs/llm/local/llamacpp/LlamaCppEngine.kt`) with a generic description, if the maintainer prefers.

**Decisions for the maintainer:**
- New (stage 1 review): `LockActivity` does not follow the dynamic color preference, so with dynamic color on the lock screen keeps the Himitsu palette. Keep it (and document it in the lock styling contract) or pass the preference.
- Converting `values-es/strings.xml` from "usted" to "tú" in one dedicated commit (planned after every track is merged).
- Font subsetting (about 1.5 MB less APK, adds a build step).

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

**Status:** completed. Merged into `main` on 2026-10-20 (`acc6de1`); branch and worktree deleted. Open work moved to `feat/security-followups`.

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
1. [x] Design document (options, trade-offs and a recommendation for each), approved by the maintainer: SQLCipher library and Room integration (verify the current version and license), database key management (Keystore-wrapped random key, optional PIN-derived "paranoid mode"), API key storage, app lock with `androidx.biometric`, hardening.
2. [x] Master key generation and storage, with unit tests for the parts that do not depend on the Keystore.
3. [x] Encrypted Room databases (P-02, and the request log database for P-07). Note: the new `applicationId` means there is no RPClient data to migrate; only earlier Himitsu debug builds may have a plain database.
4. [x] API key and custom header protection (P-01, P-04, P-13). API keys live in Room, not in Kotpref; the migration moves them out of the plain database.
5. [x] App lock: biometrics with device credential fallback, re-lock after a configurable time in background (immediately, 1 min, 5 min), lock screen.
6. [x] Hardening: backup rules (P-03), Kotpref and file encryption (P-05, P-06), debug log retention and redaction (P-07), cache file (P-10), clipboard (P-11), optional `FLAG_SECURE` (P-12), logging (P-14), clear-text warning (P-09), Gemini header (P-08), main-thread startup (O-01). Present P-15 and P-16 as decisions. File encryption (P-06) and the in-flight part of P-10 were deferred by the maintainer to `feat/security-followups`, where P-06 is done.
7. [x] Final review: go through the findings table and mark what is resolved.

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
- "Start over" (`SecureStorageManager.startOver`) moves the key file, the `FileRepository` directory (`app_repository/`), the databases and the sealed preferences aside, in that order, with one `.unrecoverable-<time>` suffix. A track that stores user files in a new directory must add it there too, or startup cleanup code may treat them as abandoned once the database is empty.
- Schema versions and auto-migrations work as before; this track did not change the schema.
- Startup work that needs the database (upgrades, file cleanup) runs in the block passed to `SecureStorageManager.start` in `RPClientApp.onCreate`, off the main thread. Screens stay behind the gate curtain until it finishes.

**Unlock extension point.** `UnlockEntry` (`…/libs/security/AppGate.kt`), resolved from Koin, opens the gate screen. A decoy calls `get<UnlockEntry>().open(context)` from its secret entry. It only shows the gate screen; authentication happens there, so there is no bypass. `AppGate.state` tells whether content may be shown.

- Unlocking happens only in the `BiometricPrompt` success callback inside `AppLockManager`; there is no public unlock method. The one exception is a device without any screen lock, where the gate screen shows a warning and lets the user continue (removing the system lock already required the credential).
- The app lock settings (`SecurityPrefs`: on/off, timeout) are not secrets; they live in Kotpref.
- Note for `feat/disguise`: the system authentication prompt shows the application label (seen as "Himitsu (Dev)" on Android 17). Check what it shows when an activity-alias is the launcher entry, and document it as a known limit if it cannot be hidden.

**Lock screen styling.** `LockScreenFrame(icon, title, message, modifier, actions)` in `…/feature/lock/ui/LockLayout.kt` draws every gate state and reads only `MaterialTheme` tokens, so `feat/aesthetic` can restyle it through the theme.

**Rules for code in other tracks.**

- **Screens.** Every Activity that can show user data extends `CoreActivity` (or `CoreActivityWithEvent`): that is what puts it behind the gate curtain. An Activity that must work while the app is locked (the `feat/disguise` decoys, the gate screen itself) must not extend it and must never show user data; its secret entry calls `UnlockEntry`.
- **Dialogs and popups.** The curtain only covers the Activity window. A dialog that shows private content skips its window while `LocalAppGateOpen.current` is false, as `AppDialogScaffold` does; reuse `AppDialogScaffold` where possible.
- **Startup.** Startup work that reads the database or sealed preferences goes in the block passed to `SecureStorageManager.start` in `RPClientApp.onCreate`. Work that must run before the key is loaded (for example deleting stale cache files) goes in `beforeKey` and must not touch the database.
- **Preferences.** `AppModel` uses `SealedPreferencesProvider`: its strings are sealed, numbers and booleans stay plain. A track's own Kotpref object that holds personal text uses the same provider and calls `SealedPreferencesProvider.sealPlainValues(it)` in the startup block. Settings the first frame needs (theme) must stay plain: either non-string values, a Kotpref object without the provider, or an entry in the provider's plain list. Reading a sealed string may perform a Keystore operation, so never read one in the first frame.
- **Secrets.** API keys, tokens and passwords go in the encrypted database, never in Kotpref. Inputs for secrets use `AppInputDialog(password = true)`, which sets a password keyboard without autocorrect.
- **Clipboard and keyboard.** Copy user content with `SensitiveClip.plainText(label, text)` so it is marked `IS_SENSITIVE`. On every `CoreActivity` page, `PrivateTextInput` already marks what Compose copies (selection toolbar, text fields) and asks keyboards not to learn from text fields; pages outside `CoreActivity` that take user text should wrap their content in it.
- **Request log.** Entries are written only through `LLMRequestLogRepository`, which redacts credentials (`RequestLogRedactor`) and applies retention. A local engine that logs requests uses the same repository.
- **Files.** Avatars, chat images and drafts are sealed by `FileRepository` (P-06) and read as streams through `StoredFile`; never read `app_repository/` by path. New features must not write user content to plain files: keep it in `FileRepository`, or seal temporary files with `FileCipher.ephemeral()` (key in memory only), delete them after use and at startup (`beforeKey`).
- **Backups and network.** Every data domain is excluded from backup and device transfer, so new files need no rule. Only system CAs are trusted; clear text stays allowed for LAN servers.
- **Logging.** Never log user content or secrets. R8 strips `android.util.Log` and `printStackTrace` from release builds, but debug builds keep them.

### Findings status (feat/security)

Final review of the [privacy audit findings](#privacy-audit-findings) on this branch.

| ID | Status | Commits | Notes |
|---|---|---|---|
| P-01 | Resolved | `c7b1141`, `1bb63d2` | API keys stay in `llm_providers.apiKey`, now inside the SQLCipher database; plain databases from earlier builds are migrated and the plain copy deleted after verification. No key was ever in Kotpref. |
| P-02 | Resolved | `c7b1141` | `primary.sqlite` is SQLCipher with a per-database key derived from the Keystore-wrapped master key. |
| P-03 | Resolved | `a39fd4e`, `022e835` | `allowBackup="false"`; every domain excluded from full backup, cloud backup and device transfer. |
| P-04 | Resolved | `1bb63d2` | Header values are masked in the editor; stored inside the encrypted database. |
| P-05 | Resolved | `309abf8` | `AppModel` strings are sealed (AES-GCM, key derived from the master key). Numbers and booleans stay plain. |
| P-06 | Resolved (feat/security-followups) | `a47334a`, `690a7e7`, `d9e5fc8`, `d153943`, `6f07d3e` | Files sealed with AES-256-GCM in 64 KiB segments under the `Files` key, keyed file names, drafts and their metadata sealed too; plain files of earlier builds sealed in the background with verification before each delete; chat archive temporaries sealed with an in-memory key. |
| P-07 | Resolved | `c7b1141`, `7d27cba` | Encrypted database, credentials redacted, 7 days / 200 entries, wiped when debug mode is turned off. |
| P-08 | Resolved | `46a3959` | `x-goog-api-key` header. |
| P-09 | Resolved (decision) | `f24e6ba`, `3fa3047` | Warning when saving a plain-HTTP remote provider; clear text stays allowed for LAN servers. Only system CAs are trusted (maintainer decision, 2026-10-20). |
| P-10 | Partly, rest deferred (decision) | `0d782e4` | Leftover request files are deleted at startup. Encrypting the file while a request is in flight needs a change in `MultimodalWireCodec` (local-inference); the maintainer decided on 2026-10-20 to do it after `feat/local-inference` is on main (see "P-10 rest" in Follow-ups). |
| P-11 | Resolved | `c3b12b1`, `3449426` | `IS_SENSITIVE` on copies of messages, stories, characters, prompts and logs, and on everything Compose copies (selection toolbar, text fields). Text fields ask keyboards not to learn. |
| P-12 | Resolved | `655a726`, `5ea24c3` | Optional `FLAG_SECURE`; with the lock on, recents previews are hidden on every version (on Android 12 and earlier through `FLAG_SECURE`, maintainer decision 2026-10-20). |
| P-13 | Resolved | `1bb63d2` | Password keyboard without autocorrect. |
| P-14 | Resolved | `2425e39` | `printStackTrace` removed; R8 strips `Log`, `printStackTrace` and SQLCipher logging in release (verified on the dex). |
| P-15 | Deferred (decision) | | Optional encrypted export, after P-06. |
| P-16 | Resolved (decision) | `d31820e`, `d92a766` | The OpenRouter routing ID is no longer in the default template nor added by the 2 to 3 migration; opt-in through the request JSON. |
| O-01 | Resolved | `c7b1141`, `77ffea0` | Startup work runs off the main thread behind the gate. The theme stays plain so the first frame never waits; the first read of a sealed `AppModel` string may still perform one Keystore operation on the main thread (P-05). |

### Follow-ups (feat/security)

Done in `feat/security-followups` (2026-10-20): P-06; reauthentication before turning the lock off and changing the recovery code (`2f3157b`); export of the files from the storage error screen, "set aside and continue" for a plain database that can never be encrypted, and a "Set-aside data" section that exports, deletes (ticked box and reauthentication) and restores set-aside copies (`c4ee750`, `4b05bf6`, `ff669e0`).

- **P-10 rest (after `feat/local-inference` is on main, maintainer decision 2026-10-20):** write the image request file with `FileCipher.ephemeral()` and decrypt while OkHttp streams it (`MultimodalWireCodec.prepare`, about 10 lines).
- **Dropdown menus while locked (after `feat/aesthetic` is on main):** a `GatedDropdownMenu` that reads `LocalAppGateOpen`, used by the 10 `DropdownMenu` calls in 7 files (2 of them touched by aesthetic).
- **Paranoid mode (not in this branch, maintainer decision 2026-10-20):** a PIN or passphrase key slot with Argon2id. `argon2kt` 1.6.0 (MIT) ships prebuilt native libraries (F-Droid would need them built from source) whose 16 KB page alignment is unverified; the reference Argon2 (CC0 or Apache-2.0) could be built with the local-inference CMake instead.
- **Set-aside data:** importing an exported `.zip` back is not supported yet. A restore leaves the copy's original key file as a small set-aside entry (it held only a lost Keystore slot), which the user can delete.
- **Known limits (privacy review of the follow-ups, 2026-10-20):**
  - A restore restarts the app with `Runtime.exit`, which can cut a pending `SharedPreferences.apply()`; the journal finishes the swap on the next start either way, and some OEMs may not relaunch the app by themselves.
  - Sealing a plain file renames and then deletes without syncing the directory in between; a plain file whose content does not match its name stays plain and is retried at every start.
  - On a device without a screen lock, the confirmation passes at once, also before "Delete permanently" (the ticked box remains), like the gate.
  - On API 26-29 the device credential opens its own screen, so confirming a settings change with the PIN counts as leaving the app: with the "Immediately" timeout the app locks again and asks to unlock once more.
  - `LockActivity` recognizes a prompt on screen by the fragment tag `androidx.biometric.BiometricFragment`, internal to `androidx.biometric` 1.1.0; check it when updating that library.
  - `PreparedFile` and `FileEntity` are inherited data classes whose `toString` prints the image hash; nothing logs them.
  - If a restore can never finish (a destination that stays taken), the error screen offers only a restart and an export; an "abandon the restore" action that undoes phase 1 would be the way out.
- **F-Droid:** SQLCipher ships prebuilt native libraries from Maven Central; confirm the F-Droid scanner accepts them or build `sqlcipher-android` from source in the recipe (release-readiness).
- **Privacy review (2026-10-20), not fixed in this track:**
  - Ship the SQLCipher BSD-3 notice inside the app (licenses screen or packaged `NOTICE`) before any release (release-readiness).
  - Done in `feat/security-followups`: selection toolbar copies, reauthentication, the `session_id` migration, the keyboard flag and recents previews on Android 12 and earlier.
- **Second privacy review (2026-10-20), documented limits:**
  - Dropdown menus and small popups are not hidden while locked; only the dialogs that show private content are (`AppDialogScaffold`, `PromptInspectorDialog`, `MessageImageViewerDialog`). The gate screen covers them right after. Planned: `GatedDropdownMenu`, see above.
  - With only system CAs trusted, self-hosted HTTPS servers with a self-signed certificate no longer work; plain HTTP on the LAN still does, with the warning.
  - Done in `feat/security-followups`: a way out for permanent failures that are not about the key, and restoring set-aside copies with their recovery code.
- **Pre-merge privacy review (2026-10-20), resolved in `feat/security-followups` (maintainer decision, 2026-10-20):**
  - Medium, done: the error screen always offers Retry; with a lost Keystore key the recovery code stays the first button.
  - Medium, done: when a plain main database survives start over, only `app_repository/sealed/` goes aside (it needs the lost key); its plain files from builds before P-06 stay with it.
  - Low, done: `OpenedValue.Plain` and `OpenedValue.Value` print `[REDACTED]`.
  - Low, done: `LockActivity` forgets a prompt whose fragment is gone, so "Unlock" works after a lost callback.
  - Low, done: the licenses table lists the Kotlin stdlib that is packaged (2.2.10).
  - Low, done: retrying a half-finished start over sets no empty directory aside.
  - Low, done: `StartOverAndroidTest` covers start over keeping stored files.
- **Build:** a combined `assembleFdroidDebug assembleGithubDebug assemblePlayDebug testFdroidDebugUnitTest` once failed in KSP with Room errors on `MutableDao<T>`; rerunning passed. Probably KSP incremental state; watch for it.

**Done when:** P-01 to P-14 are resolved or have an approved decision; a Keystore failure never deletes data silently (a clear error and options are shown); no key, passphrase or API key appears in logs or tests; `assembleDebug` for every flavor and the unit tests pass; the privacy-reviewer agent marks the branch fit to merge.

## Track: `feat/aesthetic`

**Status:** completed. Merged into `main` on 2026-10-20 (`ab1caef`), followed by the post-merge token commits `5416226` and `d16392f`; branch and worktree deleted.

**Goal:** Himitsu's own visual identity, the "shut-in otaku" look (see CLAUDE.md), on Material 3.

**Owns:**
- `…/ui/theme/**`, `…/libs/theme/**`
- `app/src/main/res/values/themes.xml`, `colors.xml`, `ic_launcher_background.xml`
- New: `app/src/main/res/font/**`, `res/values*/strings_aesthetic.xml`
- Launcher icons: `app/src/main/res/mipmap-*/**`, `app/src/main/res/drawable/ic_launcher_*`, `app/src/main/res/drawable/app_logo.png`, splash screen resources
- `…/ui/widgets/**`, `…/ui/message/**` (appearance only)
- Feature `ui/*Layout.kt` files: replacing hard-coded colors and text styles with theme tokens only, never structure or behavior. **Exception:** do not touch `…/feature/llmprovideredit/**`, `…/feature/llmproviderlist/**` or `…/feature/lock/**` until their owning tracks are merged; do those in a follow-up commit afterwards.
- Approved by the maintainer: the other UI files of features (`…/feature/*/ui/*.kt`, for example `AboutBranding.kt`, `MainChatImportViews.kt`, `ImageSendPanel.kt`) and `…/ui/dialog/**`, under the same rule (theme tokens only), with the same exceptions.
- Narrow edits: the `ToggleDynamicColor` entry in `…/feature/main/presentation/MainUiIntent.kt`, and one line in `initView` of `…/libs/core/CoreActivity.kt` (owned by security) that passes the dynamic color preference to `AppTheme`. Keep both when merging.

**Tasks, in order:**
1. [x] Proposal, approved by the maintainer: complete dark Material 3 `ColorScheme` with a contrast table (WCAG AA), secondary light theme, OFL fonts bundled in the APK with a fallback for CJK and the other app languages, identity details, the icon system and two or three icon concepts.
2. [x] Color tokens and both color schemes. Dynamic color as a setting, off by default.
3. [x] Typography and shapes.
4. [x] Replace hard-coded colors with theme tokens (except the files listed above).
5. [x] Identity details and empty states.
6. [x] Icon (adaptive, with a monochrome version for Android 13+) and splash screen.
7. [x] Compose previews of the main screens in both themes.

**Follow-ups after the owning tracks merge (done after merging into `main`, `5416226` and `d16392f`):** theme tokens and `HimitsuMonoFamily` in `…/feature/llmprovideredit/**`, `…/feature/llmproviderlist/**` (success green and provider colors now use Himitsu accents), `…/feature/requestlog/**`, `…/feature/localmodels/**`, `…/feature/lock/**` (lock screen and security settings) and `…/utils/PromptMacroHighlighting.kt`.

**Spanish register (decided by the maintainer):** Himitsu addresses the user with informal "tú" in Spanish. `strings_aesthetic.xml` already does; the inherited `values-es/strings.xml` still uses formal "usted" ("Cree", "Elija"). Converting it touches a file no track owns, so do it in one dedicated commit after every track is merged.

**Kept on purpose:** the neutral "darkroom" greys of `…/feature/imagecrop/**` and the black/white overlays drawn on top of photos (`…/ui/message/**`, `MessageImageViewerDialog`), so images are judged without a color cast.

**Possible follow-ups (not started):**
- Subset the bundled fonts to Latin, Vietnamese and Cyrillic with fonttools (and drop Inter's `opsz` axis) to cut about 1.5 MB from the APK. This adds a build step, so it needs a decision first.

**Contract this track must document here before it is merged:**
- **Icon system:** where launcher icons live and how alternative icons are added, so `feat/disguise` can add generic calculator, compass and converter icons.
  - Every launcher icon is an adaptive icon made only of vectors (minSdk 26 supports them everywhere, so there are no raster `mipmap-*dpi` files).
  - An icon with id `<id>` has three layers in `app/src/main/res/drawable/`: `ic_launcher_<id>_background.xml`, `ic_launcher_<id>_foreground.xml` and `ic_launcher_<id>_monochrome.xml` (108 dp, artwork inside the 66 dp safe zone; the monochrome layer is a single-color silhouette for Android 13+ themed icons). They are combined by `app/src/main/res/mipmap-anydpi-v26/ic_launcher_<id>.xml`.
  - Himitsu's own icon has id `himitsu`; it is wired as `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`, which the manifest already uses.
  - `feat/disguise` adds `calculator`, `compass` and `converter` the same way and references `@mipmap/ic_launcher_<id>` from each `activity-alias` (`android:icon` and `android:roundIcon`). Decoy icons must be original and generic, never copies of real apps.
  - The splash screen (Android 12+) does not set an icon, so it shows the icon of the entry that was tapped; a disguise alias never shows the Himitsu logo while starting. Its background is the neutral night or morning color.

**Lock screen styling:** `feat/security` defined the frame, `LockScreenFrame` in `…/feature/lock/ui/LockLayout.kt`, so the aesthetic track's own `RpLockScreenFrame` was dropped. The security frame draws the Himitsu look with two pieces from `…/ui/widgets/RpLockVisuals.kt`: `Modifier.lockRoomLight()` (monitor glow and desk lamp) and `RpLockMark(icon)` (the icon on a glowing tile). Both hold no text or branding and have no animation, so `feat/disguise` can reuse them with a neutral icon.

**Done when:** text contrast meets WCAG AA in both themes; no fonts or images are downloaded at runtime; every font and asset is original or has a verified free license recorded in `NOTICE`; animations respect the system "remove animations" setting; screen structure and behavior are unchanged; every flavor builds.

## Track: `feat/local-inference`

**Status:** completed. Merged into `main` on 2026-10-20 (`529cdf5`); branch and worktree deleted. GenieX (task 5) continues in `feat/geniex`; the follow-ups below go to a new branch from `main`.

**Goal:** offline role-play with models running on the device, through the existing prompt pipeline (presets, macros, lorebooks, Regex, Prompt Inspector).

**Extension point (from the audit):** implement `LLMClient` (`…/libs/llm/LLMClient.kt:16`), add an `LLMProviderProtocol` value (`…/libs/llm/model/LLMModels.kt:54`) and its branch in `LLMClientFactory.create` (`…/libs/llm/LLMClientFactory.kt:32`). The prompt pipeline runs before the adapter, so it is reused unchanged. Enum values are stored as text, so a new value needs no schema migration.

**Owns:**
- New: `…/libs/llm/local/**`, `…/feature/localmodels/**`, `app/src/main/cpp/**`, the llama.cpp submodule (proposed: `third_party/llama.cpp`), `res/values*/strings_local_inference.xml`
- GenieX code in `app/src/github/**` and `app/src/play/**` (local engine part only)
- `…/libs/llm/**`, except the narrow security edits listed in the security track
- The `when` branches in `…/libs/llm/catalog/LLMModelCatalogClientFactory.kt:15`, `…/libs/llm/model/LLMProviderCapabilities.kt:17`, `…/libs/llm/adapter/RequestBodyPatch.kt:114`, `…/libs/llm/adapter/MultimodalWireCodec.kt:74`, `…/feature/tokenusage/ui/TokenUsageLayout.kt:947`
- Protocol checks in `…/libs/prompt/PromptPostProcessor.kt:225`, `…/libs/prompt/PromptRequestFinalizer.kt:107`, `…/libs/prompt/PromptTokenizer.kt:143-161`
- `…/feature/llmprovideredit/**`, `…/feature/llmproviderlist/**`
- Additive changes only: `…/libs/prompt/model/PromptInspection.kt` (an optional rendered-prompt field) and `…/ui/dialog/PromptInspectorDialog.kt` (a collapsible section that shows it)
- Additive changes only, approved by the maintainer on 2026-10-20: `…/feature/GenerationFailureMessage.kt` (messages for on-device model failures)

**Design decisions (approved by the maintainer on 2026-10-20):**
- Local engines plug in as `LLMProviderProtocol.Local` and a `LocalLLMClient` on top of a `LocalInferenceEngine` interface, so the prompt pipeline runs unchanged.
- llama.cpp is a git submodule pinned to a release tag, built with the NDK and CMake behind a thin JNI layer of our own. No binding library. `llama-common` (which links an HTTP client) is not built; only its Jinja engine is compiled.
- Changed during implementation and confirmed by the maintainer on 2026-10-20: ggml uses its upstream runtime CPU dispatch (`GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`), which loads `libggml-cpu-*.so` by their fixed upstream names. The static, renamed build in the design only served to coexist with GenieX, which is paused. If GenieX returns, its libraries must be isolated (separate process or renamed build) before both ship together.
- The model registry is a separate Room database (`local_models.sqlite`) in `noBackupFilesDir`, so this track never bumps the main schema. It must be opened with the encrypted helper from feat/security once that track is merged.
- Chat templates come from the GGUF metadata and are rendered natively. The Prompt Inspector shows the rendered prompt for local providers.
- Changed after testing on the S26 Ultra: Local messages get the same strict normalization as Gemini (one leading system message, mid-chat system messages become user turns, alternating roles), because GGUF templates such as Qwen3.5, Gemma and Mistral raise an error otherwise. The Prompt Inspector shows the normalized messages.
- GenieX: option (c), no GenieX for now. Task 5 is paused until the maintainer has read the QAIRT EULA, because the AAR bundles proprietary Qualcomm runtime libraries.
- Models are copied to `noBackupFilesDir` and are not encrypted. Their names and metadata live in the separate `local_models.sqlite` registry above, which is not encrypted until feat/security is merged.
- The optional download list contains only Apache-2.0 models; any other model is imported by hand.
- New permissions accepted for model downloads and background generation: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`.

**Tasks, in order:**
1. [x] Design document, approved by the maintainer: engine interface, chat templates and how the Prompt Inspector shows them, llama.cpp from source (NDK, CMake, pinned commit) versus existing bindings, GenieX runtime detection, native library conflicts between llama.cpp and GenieX, licensing options for GenieX, model import and download, memory and thermal management, and a short list of recommended models.
2. [x] `LocalInferenceEngine` interface and provider integration, with a fake engine that streams text, to validate the UI and the Prompt Inspector.
3. [x] llama.cpp engine: load an imported GGUF, streaming generation, cancellation, memory release.
4. [x] Model management screen: import through the Storage Access Framework; optional download only on explicit user action, with SHA-256 verification, free-space check and progress.
5. Moved to `feat/geniex` (maintainer, 2026-10-20): GenieX engine, only in the flavors that allow it, with runtime compatibility detection; hidden on unsupported devices. Needs the QAIRT EULA read and a licensing decision first. Verified on the maintainer's Galaxy S26 Ultra: `ro.soc.model` reports `SM8850`.
6. [x] Performance settings (threads, context size, GPU or NPU) with sensible defaults.

**Follow-ups from the privacy review (2026-10-20):**
- feat/security is merged, so this is now due, before the first public release: add `local_models.sqlite` to `EncryptedDatabase` with its own `KeyPurpose` (file names and model metadata are readable today; chats with local models already live in the encrypted `AppDatabase`), and make "start over" (`SecureStorageManager.startOver`) set the model registry and `noBackupFilesDir/models/` aside too. Local inference preferences are covered by security task P-03 (backups).
- Decision needed: `abiFilters` limits the APK to `arm64-v8a` and `x86_64`, so 32-bit-only devices, which could install `main` before this track, can no longer install Himitsu at all. Options: a tiny stub library for `armeabi-v7a` and `x86` so the app installs and the engine stays hidden (recommended), building llama.cpp for 32-bit too, or requiring 64-bit and documenting it.
- From the final privacy review (medium): `LocalModelDownloadService` does not override `onTimeout`, so on Android 15+ a `dataSync` download running past 6 hours in a day makes the system kill the app; `ggml_backend_load_all_from_path` loads the vendor OpenCL driver as soon as the models screen opens, even with the GPU off, which could crash devices with a broken driver. Load the OpenCL backend only when the GPU is enabled.
- From the final privacy review (low): a cancellation that arrives before the native call resets the abort flag still evaluates the whole prompt; `LocalModelMemory` adds untrusted `Int` header values without overflow checks; the Prompt Inspector preview renders the template and counts tokens for every local request even when the Inspector is closed; `throw_state` can throw with a JNI exception already pending, and a half-failed `nativeInit` is retried and reported as an unknown error; two fields of `LocalModelDownloadService` are written from two threads without `@Volatile`.
- For chore/release-readiness: declare the `dataSync` foreground service type in the Play Console (or move downloads to a user-initiated data transfer job), and list llama.cpp, JSON for Modern C++, libc++ and OpenCL-Headers in the in-app licenses screen.
- For feat/disguise: the model download and background-generation notifications show the app's label in their header (their icons and channel names are already neutral); the label must follow the disguise.
- Chat templates from GGUF files are untrusted code. They render on their own thread with a 128 KB size limit and a 15 s time limit, but llama.cpp's Jinja runtime has no recursion or step limit, so a malicious template can still crash the app (never leak data). A real limit needs an upstream patch. A template that times out stays refused until the app restarts, even if a heavily throttled phone was the cause.

**Contracts and extension points for other tracks:**
- **New local engine:** implement `LocalInferenceEngine` (`…/libs/llm/local/LocalInferenceEngine.kt`), add a `LocalEngineKind` value and register the instance in `availableEngines()` in `…/libs/llm/local/LocalInferenceModule.kt`, guarded by a runtime capability check so unsupported devices never see it. Everything above the engine (provider type, prompt pipeline, Prompt Inspector, model screen, failure messages) is shared. Engine-specific errors must be mapped to the exceptions in `LocalInferenceExceptions.kt`, which `classifyGenerationFailure` turns into `GenerationFailure.Local` messages.
- **Provider type:** `LLMProviderProtocol.Local` with `LocalLLMClient`. Any new `when` over `LLMProviderProtocol` needs a `Local` branch; Local prompts get the same strict normalization as Gemini in `PromptPostProcessor`.
- **Model registry database (feat/security):** `LocalModelDatabase`, file `local_models.sqlite` in `noBackupFilesDir`, built in one place: the second `single {}` of `localInferenceModule`. Switching it to the encrypted helper only needs that builder changed; the schema is exported to `app/schemas/me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDatabase/`.
- **Files:** models live in `noBackupFilesDir/models/<uuid>.gguf`, partial imports (`.part`, deleted at start-up) and partial downloads (`.download`, kept so the download can resume) next to them; OpenCL kernels are cached in `cacheDir/opencl-kernels`. None of this is backed up.
- **Notifications (feat/disguise):** `LocalModelDownloadService` and `LocalGenerationService` post the only notifications of this track, on the channels `file_downloads` and `background_work`.
- **Prompt Inspector:** `PromptInspection.renderedPrompt` is optional and only set for local providers, by `PromptRequestFinalizer` through `LocalPromptPreviewer`.
- **Build and packaging (chore/release-readiness):** the native build needs the git submodules under `third_party/`, NDK `30.0.16248370` and CMake `3.31.6`. Keep `jniLibs.useLegacyPackaging = true` (ggml scans `nativeLibraryDir`), the `libOpenCL.so` exclusion and the optional `<uses-native-library>`; the F-Droid recipe must fetch submodules.

**Done when:** a downloaded or imported GGUF can be used offline with the same presets, lorebooks, Regex and Prompt Inspector as remote providers; no network call is added except the explicit model download; nothing heavy runs on the main thread; generation can be cancelled and the model is released; the `fdroid` build contains only source-built llama.cpp and no GenieX; unsupported devices never crash; every new license is in `NOTICE` and in the table below; every flavor builds.

## Track: `feat/disguise` (after security and aesthetic are merged)

**Status:** next. Its prerequisites are merged. The worktree `himitsu-disguise` already exists on `feat/disguise`, but at the groundwork commit `8bb28ed`; fast-forward it to `main` before starting.

**Starts** from `main` once `feat/security` (app lock and unlock extension point) and `feat/aesthetic` (icon system) are merged. It does not run in parallel with them. It does run in parallel with `feat/security-followups`, `feat/geniex` and the local-inference follow-ups, so it must keep to the files below.

**Goal:** an optional disguise so Himitsu looks like an everyday app at a casual glance: a calculator, a compass and a unit converter. This is discretion, not security (see CLAUDE.md, "Disguise mode").

**Owns:**
- The reserved block between `BEGIN/END disguise activity-aliases` in `app/src/main/AndroidManifest.xml`, and moving the `LAUNCHER` intent filter from `MainActivity` to an alias (plus the decoy activities, inside the same block)
- New: `…/feature/disguise/**`, `…/libs/disguise/**`, `res/values*/strings_disguise.xml` (English and Spanish with "tú", other locales per `doc/coding-guidelines.md`)
- New decoy icons under the icon system defined by the aesthetic track: `app/src/main/res/drawable/ic_launcher_<id>_{background,foreground,monochrome}.xml` and `app/src/main/res/mipmap-anydpi-v26/ic_launcher_<id>.xml` for `<id>` in `calculator`, `compass`, `converter`
- New tests: `app/src/test/java/me/kafuuneko/rpclient/{feature,libs}/disguise/**` and the same paths under `app/src/androidTest/`

**Shared files (additive only):** one `disguiseModule` entry in `…/HimitsuModules.kt`; one settings entry in `…/feature/main/model/HimitsuSettingsEntry.kt`; `app/build.gradle.kts` and `gradle/libs.versions.toml` only if the maintainer approves a dependency (the secret code KDF can use the platform's PBKDF2 with no dependency; Argon2id through `argon2kt` (MIT) is also proposed for the security paranoid mode, so agree on one choice with `feat/security-followups`); `NOTICE` and this section.

**Narrow edits allowed (say so in the pull request):**
- `…/libs/llm/local/model/LocalModelDownloadService.kt` and `…/libs/llm/local/LocalGenerationService.kt`: only the notification builder, to fix the header label leak.
- `…/libs/core/CoreActivity.kt`: only a task description (recents label and icon) that follows the active disguise, if the alias label is not enough. `feat/security-followups` also edits this file; keep the change to one block.

**Does not touch:** `…/feature/lock/**` and `…/libs/security/**` (owned by `feat/security-followups` while it runs; the decoys use the `UnlockEntry` contract as it is, and a contract gap is raised with the maintainer), `res/values*/strings.xml`, `…/libs/AppModel.kt`, the files owned by the local-inference follow-ups.

**Inputs from the merged tracks:**
- Secret entry: `get<UnlockEntry>().open(context)`; decoy activities must not extend `CoreActivity` and must never show user data (security contract).
- Reusable visuals without text or branding: `Modifier.lockRoomLight()` and `RpLockMark(icon)` in `…/ui/widgets/RpLockVisuals.kt`.
- The splash screen shows the icon of the entry that was tapped, so an alias never shows the Himitsu logo while starting.
- Known leaks to handle or document: the system authentication prompt shows the application label; the download and background-generation notifications show the app label in their header (their text, icon and channel names are already neutral).
- `LockActivity` keeps the Himitsu palette even with dynamic color on (pending decision above); check how that looks behind a decoy.

**Testing:** the track needs its own emulator; ask the maintainer which one (emulator-5554 belongs to `feat/security-followups`; the spare AVD `Himitsu_LocalInference` has no owner).

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
- [ ] O-02, O-03, O-05. For O-03, untrack the per-device `.idea` files first (`deploymentTargetSelector.xml` records a device serial).
- [ ] The `play` build must not link the GitHub repository once it shows a funding button or donation text (indirect donation link).
- [ ] Licenses screen and packaged notices: SQLCipher (BSD-3), llama.cpp, JSON for Modern C++, libc++, OpenCL-Headers.
- [ ] Play Console: declare the `dataSync` foreground service type, or move model downloads to a user-initiated data transfer job.

---

## Third-party licenses

Current dependencies (audited at commit `c3ed325`). Keep this table and `NOTICE` up to date with every dependency change.

### Shipped in the app

| Dependency | Version | License | Network calls of its own |
|---|---|---|---|
| AndroidX core-ktx, exifinterface, lifecycle-runtime-ktx, activity-compose | 1.18.0, 1.3.6, 2.10.0, 1.13.0 | Apache-2.0 | No |
| Jetpack Compose BOM (ui, foundation, material3, material3-adaptive, material-icons-extended) | 2026.05.00 | Apache-2.0 | No |
| AndroidX Room (runtime, ktx) | 2.8.4 | Apache-2.0 | No |
| Kotlin stdlib | 2.2.10 (resolved; SQLCipher and other libraries need it) | Apache-2.0 | No |
| kotlin-reflect | 2.0.21 | Apache-2.0 | No |
| kotlinx-serialization-core | 1.8.1 | Apache-2.0 | No |
| Koin (koin-android) | 4.0.0-RC1 | Apache-2.0 | No |
| Kotpref | 2.13.2 | Apache-2.0 | No |
| OkHttp (with Okio) | 4.12.0 | Apache-2.0 | Only the requests the app makes |
| Gson | 2.11.0 | Apache-2.0 | No |
| JTokkit | 1.1.0 | MIT | No |
| AndroidX biometric, lifecycle-process (feat/security; lifecycle-process was already a transitive dependency) | 1.1.0, 2.10.0 | Apache-2.0 | No |
| SQLCipher for Android (`net.zetetic:sqlcipher-android`, bundles LibTomCrypt, public domain), pulls in AndroidX sqlite 2.7.0 (feat/security) | 4.19.1 | BSD-3-Clause (notice reproduced in `NOTICE`) | No |
| llama.cpp and ggml (git submodule `third_party/llama.cpp`, built from source; local-inference) | b11748 (`3bb63cb`) | MIT | No (built without `llama-common`, OpenSSL or any downloader) |
| JSON for Modern C++, vendored in llama.cpp (local-inference) | 3.12.0 | MIT | No |
| LLVM libc++ (`libc++_shared.so` from NDK 30.0.16248370; local-inference) | NDK r30 | Apache-2.0 WITH LLVM-exception | No |
| OpenCL-Headers (git submodule, compiled into `libggml-opencl.so`; local-inference) | v2026.05.29 | Apache-2.0 | No |

Notable transitive dependencies (appcompat, fragment, sqlite, kotlinx-coroutines, atomicfu, error_prone_annotations) are Apache-2.0. There is no Google Play Services, Firebase, ML Kit or analytics SDK.

Bundled resources: the launcher icon background vector is from the AOSP template (Apache-2.0). The provenance of `app/src/main/res/drawable/app_logo.png` is unverified; the aesthetic track replaces it. **Update (aesthetic track):** both are removed; the launcher icon, the in-app logo (`app_logo.xml`) and the empty-state pictograms are original artwork made for Himitsu, under the project license.

| Bundled font | Version | License | Network calls of its own |
|---|---|---|---|
| Inter (variable, regular and italic) | google/fonts `47d54ca1ebb1` | OFL-1.1 | No |
| Unbounded (variable) | google/fonts `47d54ca1ebb1` | OFL-1.1 | No |
| JetBrains Mono (variable) | google/fonts `47d54ca1ebb1` | OFL-1.1 | No |

### Build and test only (not shipped)

| Dependency | License |
|---|---|
| Android Gradle Plugin 9.0.1, Compose compiler plugin, KSP, Gradle wrapper 9.5.0 | Apache-2.0 |
| Room compiler, AndroidX Test, Espresso, Compose UI test | Apache-2.0 |
| JUnit 4.13.2 | EPL-1.0 (not GPL-compatible, acceptable because it is never shipped) |
| Hamcrest 1.3 | BSD-3-Clause |
| OpenCL ICD Loader v2026.05.29 (git submodule; link-time stub, excluded from the APK; local-inference) | Apache-2.0 |

No current dependency is incompatible with GPLv3.
