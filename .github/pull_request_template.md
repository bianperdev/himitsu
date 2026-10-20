## What and why

<!-- What does this change do, and why? Link the issue it closes, if any (for example "Closes #12"). -->

## How it was tested

<!-- Builds and tests you ran, for example:
- ./gradlew assembleFdroidDebug assembleGithubDebug assemblePlayDebug
- ./gradlew testFdroidDebugUnitTest
- connectedFdroidDebugAndroidTest on an emulator (ANDROID_SERIAL set; never on a personal phone)
- manual steps on an emulator or device -->

## New dependencies

<!-- Only if you added one: what it does, its exact license, whether it makes network calls, and its obligations. Otherwise write "None". -->

## Checklist

- [ ] I read [CLAUDE.md](../CLAUDE.md) and [CONTRIBUTING.md](../CONTRIBUTING.md), and the change follows them.
- [ ] The change builds, and I ran the tests that apply.
- [ ] No API keys, tokens, private chats, personal data or copyrighted character cards in the code, tests, fixtures, screenshots or this description. Logs are redacted.
- [ ] Nothing new writes chat content, prompts, responses, characters or keys to Logcat, plain files, preferences or backups.
- [ ] No new network connections, analytics or third-party SDKs that make network calls.
- [ ] User-facing strings are in string resources, in all 13 locales (Spanish uses "tú").
- [ ] New source files start with `// SPDX-License-Identifier: GPL-3.0-or-later`; the RPClient MIT notice is untouched.
- [ ] The `play` flavor gains no donation links, direct or indirect; the `fdroid` flavor gains no Google services and no GenieX.
- [ ] Commits follow Conventional Commits, in English.

By opening this pull request, I agree that my contribution is licensed under GPL-3.0-or-later.
