# Himitsu roadmap

This file is a stub. The full roadmap (audit tasks, work tracks, flavors and merge order) will be added in a later commit.

## Pending from the initial rebranding

Inherited RPClient material that is kept as is for now and must be reworked before the first release (`chore/release-readiness`):

- [ ] `fastlane/metadata/android/`: store descriptions, changelogs and screenshots still describe RPClient. Rewrite them for Himitsu in every locale.
- [ ] `fdroid/me.kafuuneko.rpclient.yml` and `fdroid/README.md`: still declare `License: MIT`, the RPClient author and the upstream repository. Replace them with Himitsu metadata.
- [ ] `metadata/images/`: RPClient screenshots used by the old README.
- [ ] `README_ZH.md`: Chinese README of RPClient. Translate the Himitsu README or remove it.
- [ ] `.github/workflows/release.yml`: tag trigger disabled. Adapt signing secrets, artifact names (`RPClient-*.apk`), the `origin/master` branch check and the release title, then re-enable it.
- [ ] About screen: the repository and feedback links (`AppModel.GITHUB_REPO`, `feature/about/`) still point to RPClient. Point them to Himitsu, keep an explicit credit to RPClient, and add a real open source licenses screen.
- [ ] About screen: the Google Play link and "rate the app" card must depend on the distribution flavor (no Play links in the F-Droid build).
- [ ] `app/build.gradle.kts`: add `dependenciesInfo { includeInApk = false; includeInBundle = false }` for F-Droid.
