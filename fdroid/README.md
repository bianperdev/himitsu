# F-Droid submission notes

This directory holds a draft [fdroiddata](https://gitlab.com/fdroid/fdroiddata) metadata file for Himitsu: [`dev.bianperdev.himitsu.yml`](dev.bianperdev.himitsu.yml). F-Droid builds the `fdroid` flavor from source and signs it with its own key.

## Release 0.1.0

| Field | Value |
|---|---|
| Application ID | `dev.bianperdev.himitsu` |
| Version name | `0.1.0` |
| Version code | `30000100` (`30000000 + major * 10000 + minor * 100 + patch`, see CONTRIBUTING.md) |
| Git tag | `v0.1.0` |
| Gradle flavor | `fdroid` |
| License | GPL-3.0-or-later (code inherited from RPClient keeps its MIT notice) |

Before submitting, check that the tag `v0.1.0` exists on the public repository and that `app/build.gradle.kts` at that tag has `versionName = "0.1.0"` and `versionCode = 30000100`. fdroiddata reviewers usually ask for the full commit hash in `commit:` instead of the tag name.

## What the recipe does and assumes

- `submodules: true`: the native engine is built from the git submodules in `third_party/` (llama.cpp, OpenCL-Headers, OpenCL-ICD-Loader). Nothing is downloaded at build time.
- `ndk: 30.0.16248370`: the exact NDK the project pins in `app/build.gradle.kts`.
- `prebuild: sdkmanager 'cmake;3.31.6'`: the project pins CMake 3.31.6. This assumes the build server does not have that CMake package and that its `sdkmanager` can install it. If the server already provides it, the line can go; if `sdkmanager` cannot install it, an alternative is a `sudo:` step that installs CMake 3.31 from Debian, plus pointing `cmake.dir` in `local.properties` to it.
- `scandelete`: llama.cpp ships an example Android project with a `gradle-wrapper.jar`, and test vocabulary files (`models/*.gguf`). Neither is used by the build (llama.cpp examples and tests are off), so they are deleted before the scan.
- JDK: the build needs JDK 17 or later (Gradle 9 and AGP 9); the project and CI use JDK 21. If the build server's default JDK is too old, add a `sudo:` step that installs a newer one.
- `AutoUpdateMode: Version` with `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$`: new releases are picked up from tags named `v<versionName>`.
- No `Donate` or `Liberapay` fields: no donation platform is set up yet.

## Prebuilt native libraries from Maven

Two dependencies ship prebuilt native libraries inside their AARs from Maven Central (they are not in the source tree, so the source scanner does not see them):

- `net.zetetic:sqlcipher-android` (BSD-3-Clause): `libsqlcipher.so`.
- `androidx.graphics:graphics-path` (Apache-2.0, pulled in by Jetpack Compose): `libandroidx.graphics.path.so`.

Both are free software from a repository F-Droid trusts, which the inclusion policy accepts. Reviewers may still ask for SQLCipher to be built from source (for example as a srclib); be ready to discuss it in the merge request.

## Possible anti-features

To be confirmed with the reviewers, and stated in the merge request:

- **NonFreeNet** may be applied because the provider presets point to proprietary AI services (OpenAI, Gemini, Claude, DeepSeek, OpenRouter, xAI). Himitsu does not depend on them: it works fully offline with on-device models and with self-hosted servers.
- **NSFW** may be applied because role-play with user-chosen models can produce adult content. Himitsu ships no such content itself.
- Not expected: Ads, Tracking, NonFreeAssets, NonFreeDep, NonFreeAdd, TetheredNet (any endpoint can be configured).

## Submit

1. Fork and clone `https://gitlab.com/fdroid/fdroiddata`.
2. Create a branch named `dev.bianperdev.himitsu`.
3. Copy `fdroid/dev.bianperdev.himitsu.yml` to `fdroiddata/metadata/dev.bianperdev.himitsu.yml`.
4. In the fdroiddata checkout, run:

   ```bash
   fdroid readmeta
   fdroid rewritemeta dev.bianperdev.himitsu
   fdroid checkupdates --allow-dirty dev.bianperdev.himitsu
   fdroid lint dev.bianperdev.himitsu
   fdroid build -v -l dev.bianperdev.himitsu
   ```

5. Commit with `New App: Himitsu` and open a merge request against fdroiddata. In the description, mention the disguise feature and its limits, the prebuilt libraries above and the expected anti-features.

The store description, short description, changelogs and (later) screenshots come from `fastlane/metadata/android/` in this repository.
