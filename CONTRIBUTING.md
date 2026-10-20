# Contributing to Himitsu

Thank you for wanting to help. Himitsu is a privacy-first fork of [RPClient](https://github.com/KafuuNeko/RPClient), so contributions are judged first by what they mean for users' privacy.

Before changing code, read:

- [CLAUDE.md](CLAUDE.md): the project rules for everyone, humans and AI assistants alike (privacy, licensing, distribution channels, disguise, local engines, design).
- [doc/coding-guidelines.md](doc/coding-guidelines.md) and the topic guides in [doc/guides/](doc/guides) relevant to your change (architecture, MVI, Room, Koin and Kotpref, comments). They are inherited from RPClient and written in Chinese; CLAUDE.md takes precedence on privacy, local engines, licensing, distribution and design.
- [ROADMAP.md](ROADMAP.md): open work, decisions already taken, and which files each work track owns.

For a change that touches more than a few files or the architecture, open an issue first and describe your plan.

## Building

Requirements: Git, JDK 21, the Android SDK with platform 36.1, NDK `30.0.16248370` and CMake `3.31.6`.

```sh
git clone https://github.com/bianperdev/himitsu.git
cd himitsu
git submodule update --init --depth 1
```

The submodules under `third_party/` are shallow; always fetch them with `--depth 1`.

| Flavor | Channel | Build |
|---|---|---|
| `fdroid` | F-Droid, built from source by F-Droid. Never any Google service, never GenieX. | `./gradlew assembleFdroidDebug` |
| `github` | Signed APKs on GitHub Releases. | `./gradlew assembleGithubDebug` |
| `play` | Google Play. No donation links, not even indirect ones. | `./gradlew assemblePlayDebug` |

Replace `Debug` with `Release` for an R8-minified release build (unsigned unless the `RELEASE_*` signing properties are set). Donation code lives only in `app/src/donations/` (used by `fdroid` and `github`); Play-only code lives in `app/src/play/`.

## Testing

- Run the smallest check that matches the risk of your change, at least `./gradlew assembleFdroidDebug`, and the unit tests: `./gradlew testFdroidDebugUnitTest`.
- **Instrumented tests run only on an emulator, never on a personal phone.** Some tests reset app data, the lock or the disguise code. Set `ANDROID_SERIAL` to the emulator's serial before running them, so Gradle cannot pick a connected phone:

  ```sh
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedFdroidDebugAndroidTest
  ```

  The disguise "no bypass" tests are skipped unless the emulator has a screen lock (`adb -s emulator-5554 shell locksettings set-pin 1234`).
- Never claim a change works without building it.

## Conventions

- **Commits:** small and atomic, one logical change each, in English, following [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`, `ci:`).
- **Language:** code, identifiers, comments and documentation in English.
- **Strings:** every user-facing string goes in string resources, never hard-coded, in all 13 locales (`values`, `values-b+pt+BR`, `values-b+zh+Hans`, `values-b+zh+Hant`, `values-de`, `values-es`, `values-fr`, `values-id`, `values-it`, `values-ja`, `values-ko`, `values-ru`, `values-vi`). English and Spanish must be real translations; Spanish addresses the user with informal "tú". New features add their own `strings_<feature>.xml` instead of growing the inherited `strings.xml`.
- **License headers:** new source files start with `// SPDX-License-Identifier: GPL-3.0-or-later` (or the comment syntax of the file type). Do not add headers to unmodified upstream files.
- **RPClient's notice:** never delete or alter [LICENSES/MIT-RPClient.txt](LICENSES/MIT-RPClient.txt) or the RPClient section of [NOTICE](NOTICE).
- **Upstream sync:** the Kotlin package stays `me.kafuuneko.rpclient`. Prefer new files and modules over rewriting inherited ones, so upstream changes still merge.
- **Dependencies:** before adding one, state what it does, its exact license, whether it makes network calls and what obligations it brings. It must be GPLv3-compatible and must not phone home. Update `NOTICE` and the licenses table in `ROADMAP.md` in the same commit.
- **Privacy:** never log chat content, prompts, responses, characters or API keys, not even in debug builds. Secrets go in the encrypted database, never in Kotpref or plain files. See the full rules in CLAUDE.md.

## Versioning

Himitsu uses [Semantic Versioning](https://semver.org/) in the 0.x series. `versionName` is the plain version (`0.1.0`) and every release is tagged `v<versionName>` (`v0.1.0`).

`versionCode = 30000000 + major * 10000 + minor * 100 + patch`, so 0.1.0 is `30000100` and 0.20.0 is `30002000`. The offset keeps every Himitsu version code above the date-based codes inherited from RPClient (up to `20260301`), so existing installs can update and the inherited data upgrade steps still run.

## AI coding assistants

You may use AI assistants. [CLAUDE.md](CLAUDE.md) applies to them as it does to you, and most assistants read it automatically. **You are responsible for everything you submit**: review the code, build it, test it, and make sure you have the right to contribute it under the project license.

## Sensitive data in issues and pull requests

The repository is public. In issues, pull requests, commits, tests and fixtures, never post:

- API keys, tokens or passwords (use obviously fake ones such as `sk-test-0000`);
- private conversations or personal data;
- copyrighted character cards, lorebooks or artwork (write your own small test content instead).

Redact logs before posting them. Himitsu never writes chat content to Logcat, so if you ever see it there, that is a bug: report it privately as described in [SECURITY.md](SECURITY.md).

## License of contributions

Himitsu is licensed under the GNU General Public License v3.0 or later. By contributing, you agree that your contribution is licensed under `GPL-3.0-or-later`. Code taken from elsewhere must carry a GPLv3-compatible license, and its notice must be kept.
