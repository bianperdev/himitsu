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
| Avatars and images (P-06) | Encrypted in this track. |
| P-15 encrypted export | Deferred, tracked in ROADMAP.md. |
| P-16 OpenRouter routing ID | Off by default, opt-in. |

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
  - The Keystore key is lost (OEM bug, factory reset of the secure element) or the key file is missing: the app shows an error screen. Options: retry, unlock with the recovery code, or start over after moving the old files aside with an `.unrecoverable-<time>` suffix. Nothing is ever deleted automatically.
- Paranoid mode (not implemented): a slot wrapped with Argon2id over a user passphrase (`com.lambdapioneer.argon2kt`, MIT). It would require deferring every database access until the passphrase is entered. Forgetting the passphrase would destroy the data.

### App lock

- `androidx.biometric:biometric` 1.1.0 (stable). Authenticators: strong biometrics or the device credential on API 30+, weak biometrics or the device credential on API 26-29 (the platform does not support the strong combination there).
- The app always starts locked when the lock is enabled. After going to the background it re-locks after the configured delay (immediately, 1 minute, 5 minutes), measured with `elapsedRealtime`.
- `CoreActivity` checks the gate in `onStart` and hides its content while locked, so nothing flashes on screen.
- Only `libs/security` can mark the app as unlocked, and only after a successful authentication. Decoys from `feat/disguise` can *start* authentication through `UnlockEntry`, never skip it.

### Hardening

- Backups: `allowBackup="false"`, and every domain excluded from cloud backup and device transfer (Android 12+ ignores `allowBackup` for device transfer).
- `FLAG_SECURE` as an option, off by default. With the app lock on, recents thumbnails are hidden on API 33+ with `setRecentsScreenshotEnabled(false)`.
- Request logs: separate encrypted database, credentials redacted before storage, retention limit, wiped when debug mode is turned off.
- No logging: inherited `printStackTrace` calls removed and `android.util.Log` stripped by R8 in release builds.
