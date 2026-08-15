# Releasing Minova Cinema

This checklist is mandatory for every public release. It exists because the in-app updater relies on a predictable GitHub Release and Android requires every update to retain the same package name and signing certificate.

## Release invariants

- Keep the application ID `com.minova.cinema`.
- Increase `versionCode` for every APK. Android compares this integer when installing updates.
- Set `versionName` to the release tag without the leading `v`.
- Sign with the existing Minova Cinema release keystore. Never generate a replacement key for this application.
- Verify certificate SHA-256 `fdf10675aaacaeaf93aedf4a068f71dfc42dc4a7badf9ef68022ba6b9ddaaf52` before publishing.
- Publish a normal GitHub Release—not a draft or prerelease—so `/releases/latest` can discover it.
- Attach the production APK as `Minova-Cinema-<version>.apk`. The updater selects an `.apk` asset from the official repository release URL.

## Checklist

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add the dated release notes to `CHANGELOG.md`.
3. Update the version and direct APK fallback links in `README.md`, `docs/index.html`, and `docs/site-config.js`.
4. Confirm the ignored `local.properties` contains all four `MINOVA_RELEASE_*` signing values.
5. Run `./gradlew clean lintDebug testDebugUnitTest lintRelease assembleRelease bundleRelease`.
6. Inspect the APK with `aapt dump badging` and verify its signature with `apksigner verify --verbose --print-certs`.
7. Confirm no Plex token, private server address, signing password, or keystore is staged.
8. Commit, tag `v<version>`, and push `main` plus the tag.
9. Create a public GitHub Release and upload:
   - `Minova-Cinema-<version>.apk`
   - `Minova-Cinema-<version>.aab`
10. Confirm both GitHub Actions workflows pass, the website download resolves, and GitHub's `releases/latest` API returns the new tag and APK.

Users must manually install version 2.3.0 once because earlier builds do not contain the updater. Starting with 2.3.0, the app checks GitHub automatically and offers each newer release. Android still requires user confirmation in the system package installer.
