# Security design (`feat/security`)

Approved by the maintainer on 2026-10-20. This document records the decisions of the security track and the reasons behind them. The contracts other tracks rely on are summarized in [ROADMAP.md](../../ROADMAP.md#track-featsecurity).

## Decisions

| Topic | Decision |
|---|---|
| Database encryption | `net.zetetic:sqlcipher-android` 4.19.1 through Room's `SupportOpenHelperFactory`. |
| Key management | Random 256-bit master key wrapped by an AES-GCM key in the Android Keystore (no user authentication), plus an optional recovery code. A PIN-derived "paranoid mode" may be added later as another key slot. |
| API keys | Stay in `llm_providers.apiKey`, protected by the encrypted database. No schema change. |
| Device without a screen lock | The app lock lets the user in and shows a persistent warning (removing the system screen lock already requires the credential). |
| Plain database from earlier Himitsu builds | Migrated with `sqlcipher_export`, verified, then the plain files are deleted. |
| Avatars and images (P-06) | Sealed with a segmented AES-256-GCM format under the `Files` key, in `feat/security-followups`. |
| Sensitive settings changes | Turning the lock off and changing the recovery code ask for biometrics or the device credential. |
| Recents on Android 12 and earlier | With the lock on, `FLAG_SECURE` hides the preview and also blocks screenshots (maintainer decision, 2026-10-20). |
| Paranoid mode | Not in `feat/security-followups` (maintainer decision, 2026-10-20). |
| P-15 encrypted export | Deferred, tracked in ROADMAP.md. |
| P-16 OpenRouter routing ID | Off by default, opt-in. |
| User-installed CAs (P-09) | Not trusted; only system CAs. |

### SQLCipher

- Maintained library: `net.zetetic:sqlcipher-android` (the older `android-database-sqlcipher` stopped at 4.5.4 in 2023). Version 4.19.1 was released on 2026-09-29. Version 5.0.0 is still a beta with breaking changes.
- License: BSD-3-Clause (Zetetic LLC, Community edition). The default build uses LibTomCrypt, which is public domain. The BSD notice must be reproduced in a user-accessible place: `NOTICE` and the open source licenses screen.
- No network calls. Requires API 23 or later. Native libraries are built for 16 KB pages.
- The Java part logs to Logcat by default. Himitsu installs `NoopTarget` before opening any database.
- Room 2.8.4 still uses the `SupportSQLite` API, so `SupportOpenHelperFactory` needs no DAO change. The Room 3 driver API (`SQLCipherDriver`) was not chosen because it changes transaction and raw query semantics in inherited code.

### Key management

- A random 256-bit **master key** is generated on first start. Per-purpose keys are derived from it with HKDF-SHA256 (primary database, request log database, preferences, files), so one key never serves two purposes.
- The master key is stored only in wrapped form, in `noBackupFilesDir/himitsu_master.key`. The file holds versioned **key slots**:
  - Keystore slot: AES-256-GCM, key in the Android Keystore (StrongBox when available), no user authentication, so fingerprint changes and screen lock changes never invalidate it.
  - Recovery slot (optional): AES-256-GCM under a key derived with HKDF from a random 140-bit recovery code that the user writes down. The code has enough entropy that a slow KDF is not needed.
- SQLCipher receives the derived key as a raw key (`x'…'`), which skips its internal PBKDF2.
- **What happens when…**
  - The user adds or removes a fingerprint, or disables the screen lock: the Keystore key is not bound to authentication, so nothing happens to the data.
  - The Keystore key is lost (OEM bug, factory reset of the secure element) or the key file is missing: the app shows an error screen. Options: retry, unlock with the recovery code, or start over after moving the old files aside with an `.unrecoverable-<time>` suffix (key file, avatars and chat images, databases, sealed preferences, in that order). Nothing is ever deleted automatically.
- Paranoid mode (not implemented): a slot wrapped with Argon2id over a user passphrase (`com.lambdapioneer.argon2kt`, MIT). It would require deferring every database access until the passphrase is entered. Forgetting the passphrase would destroy the data.

### Stored files (P-06)

- Format (`FileCipher`, no dependency): header `HMF1`, a version byte and a random 32-byte salt; then AES-256-GCM segments of 64 KiB plaintext with a 16-byte tag. Each file has its own key, HKDF of the `Files` key with the salt (`himitsu/v1/files/segment`). A segment's nonce holds its index and a last-segment flag, and the header is authenticated with every segment, so tampering, reordering, truncation and extension are detected. No plaintext is returned before its segment verifies.
- Names: HMAC-SHA256 of the content hash under a second derived key (`himitsu/v1/files/name`), in `app_repository/sealed/`, so a name does not reveal a known image. The `files` table keeps the SHA-256 for deduplication, inside SQLCipher. Drafts and their `.meta` files are sealed too.
- Reading goes through `StoredFile` streams (`BitmapFactory.decodeStream`, `ExifInterface(InputStream)`); nothing reads a stored file by path.
- Migration: after the app opens, each plain file of an earlier build is sealed into a synced `.sealing` copy, read back and checked against its hash and size, renamed, and only then is the plain file deleted. Files being read, files whose content does not match their name and storage errors are left for a later start.
- Temporary files (chat archive import and export) are sealed with `FileCipher.ephemeral()`, whose key lives only in the process, and deleted at startup.

### Set-aside data

- "Start over" and "set aside and continue" move files aside with a `.<kind>-<yyyyMMdd-HHmmss>` suffix and never delete them. The security settings list these copies and can save them as a byte-for-byte `.zip`, delete them (a ticked box and reauthentication; the only deletion of user data in the app) or restore them.
- Restore opens the copy's key file with its own Keystore key or its recovery code, checks the key against the copy's main database, seals it again under a new Keystore key, and records the swap in `himitsu_restore.journal`. The swap happens at the start of the next process, before anything opens the files: the data in use moves aside as `.replaced-<time>` and the copy takes its place. Every step renames only what is still at its source and never replaces anything, so a crash is finished on the next start.
- A plain database that can never be encrypted (damaged, or two plain copies) is reported as `PlainDatabaseUnusable`; "set aside and continue" moves it aside still in plain form, and the dialog says so.
- An encrypted database that does not open with its key (damaged, or encrypted with another key) is found before Room gets the key: every encrypted database is opened read only first, with an error handler that never deletes the file. It is reported as `DatabaseDamaged`; "set aside and continue" moves it aside still encrypted, under a `.damaged-<time>` suffix. Such a copy has no key file of its own, so it can be exported or deleted but not restored. A SQLite error in the startup tasks shows the error screen with retry instead of crashing.

### App lock

- `androidx.biometric:biometric` 1.1.0 (stable). Authenticators: strong biometrics or the device credential on API 30+, weak biometrics or the device credential on API 26-29 (the platform does not support the strong combination there).
- The app always starts locked when the lock is enabled. After going to the background it re-locks after the configured delay (immediately, 1 minute, 5 minutes), measured with `elapsedRealtime`.
- `CoreActivity` covers its page with a curtain while the gate is not open: it raises it in `onStop` when the lock is on and lowers it in `onResume` only after a synchronous gate check, so nothing flashes on screen. Dialogs that can show private content read `LocalAppGateOpen` and skip their window while locked.
- Only `libs/security` can mark the app as unlocked, and only after a successful authentication. Decoys from `feat/disguise` can *start* authentication through `UnlockEntry`, never skip it.

### Hardening

- Backups: `allowBackup="false"`, and every domain excluded from cloud backup and device transfer (Android 12+ ignores `allowBackup` for device transfer).
- `FLAG_SECURE` as an option, off by default. With the app lock on, recents thumbnails are hidden on API 33+ with `setRecentsScreenshotEnabled(false)`.
- Request logs: separate encrypted database, credentials redacted before storage, retention limit, wiped when debug mode is turned off.
- No logging: inherited `printStackTrace` calls removed and `android.util.Log` stripped by R8 in release builds.
