<p align="center">
  <img src="docs/assets/minova-cinema-wordmark.png" width="420" alt="Minova Cinema">
</p>

<p align="center">
  A cinema-first Plex client for Android TV, built in Kotlin with Compose for TV and Media3.
</p>

<p align="center">
  <a href="https://minova-chromium.github.io/Minova-Android-Tv-Cinema-Application/">Website</a> ·
  <a href="https://github.com/minova-chromium/Minova-Android-Tv-Cinema-Application/releases/download/v2.8.0/Minova-Cinema-2.8.0.apk">Download APK</a> ·
  <a href="https://github.com/minova-chromium/Minova-Android-Tv-Cinema-Application/issues">Issues</a>
</p>

## What it does

Minova Cinema connects directly to a Plex Media Server and presents personal movies and series in a D-pad-native television interface. It is an independent Minova project and is not affiliated with Plex, Inc.

- First-launch setup for a local Plex server address and token; no personal token is compiled into the app.
- Separate Home, Movies, and Series destinations with shelves, full-library grids, genre filters, and global search.
- Plex-synced watched state, watchlist, Continue Watching progress, manual mark watched/unwatched, and dismiss-from-continue actions.
- Show details with season artwork, episodes, cast and crew; movie details with trailers when Plex exposes them.
- Next-up experience after an episode finishes.
- Optional ten-second autoplay for the next episode, with a persistent TV setting.
- Plex-powered Skip Intro and Skip Credits when the server provides analyzed markers, plus chapter seeking.
- A configurable three-hour inactivity check that stops playback after an unanswered 30-second prompt.
- Direct D-pad playback: OK toggles play/pause, Left/Right seek, Down opens the bottom controls.
- Original-quality direct play plus Plex transcoding choices for 4K, 1080p, 720p, and 480p.
- Audio and subtitle track selection with language, codec, and channel details.
- Live Direct Play, Direct Stream, and Transcoding diagnostics with the Plex decision reason.
- Manual audio-delay and subtitle-delay correction for television synchronization issues.
- Media3 frame-rate matching and supported surround-audio passthrough.
- Private local metadata caching, paged large-library loading, and upcoming-artwork prefetching.
- Personalized Plex shelves using viewing progress, watched history, ratings, genres, and only server artwork.
- Plex Home profile switching for full, managed, and PIN-protected users.
- Android TV Home Continue Watching and Watchlist channels with title deep links.
- A local Plex speed test and TV codec report with an automatic quality recommendation.
- Automatic GitHub Release checks with a D-pad update dialog and secure APK installer hand-off.
- OLED-friendly ambient mode with a bouncing, color-shifting Minova logo after five idle minutes.
- Optional Cinema Mode queues user-toggleable unwatched-library trailers, a user-selected local 4K/Atmos bumper, and the feature in one preloaded Media3 playlist.
- Adjustable ambient screensaver and Continue Watching safety timers.
- Native Google Home theater-light assignment and four-second dim/restore fades.
- Local TP-Link Tapo Cinema Room discovery, encrypted login storage, per-light assignment, and synchronized dim/restore fades.
- Minova visual identity, launch sequence, TV banner, and round launcher mark.

> Playback capability is determined by the Android TV device, connected audio equipment, network, source codecs, subtitle format, and Plex server transcoding capacity. “Original” does not guarantee that every file will direct play on every TV.

## Install

1. [Download the Minova Cinema 2.8.0 APK directly](https://github.com/minova-chromium/Minova-Android-Tv-Cinema-Application/releases/download/v2.8.0/Minova-Cinema-2.8.0.apk).
2. Transfer it to an Android TV device and allow installation from the sending app when Android asks.
3. Launch Minova Cinema and enter the Plex server address and token during setup.

The Plex server can be entered as `192.168.1.10:32400` or as a complete `http://`/`https://` URL. Keep the token private—it grants access to the server.

Versions 2.3.0 and 2.4.0 have an Android TV installer hand-off bug. Install 2.4.1 manually once if you are using either version; automatic updates work normally from 2.4.1 onward.

Android does not allow a normal third-party application to power off the television. When the inactivity prompt expires, Minova Cinema stops playback, releases its keep-screen-on player, and returns to Android TV Home so the television's configured screen-saver and sleep policy can take over.

## Remote controls

| Location | Control | Action |
|---|---|---|
| Browse | D-pad | Move between navigation, shelves, cards, filters, and grids |
| Browse | OK / Enter | Open the focused item or activate the focused action |
| Player | OK / Enter | Play or pause immediately |
| Player | Left / Right | Seek backward or forward |
| Player | Down, Menu, or Settings | Reveal the bottom playback controls |
| Player | Back | Close playback settings first, then leave playback |

## Build from source

Requirements: Android Studio with JDK 17 and Android SDK 37.

```powershell
.\gradlew.bat lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. Open the repository root in Android Studio to run it on a TV emulator or a physical Android TV device.

Release builds read signing values from the ignored `local.properties` file. See [`local.properties.example`](local.properties.example), keep the existing release keystore, and add these entries below the normal `sdk.dir` value:

```properties
MINOVA_RELEASE_STORE_FILE=release-signing/minova-cinema-release.jks
MINOVA_RELEASE_STORE_PASSWORD=replace_with_store_password
MINOVA_RELEASE_KEY_ALIAS=minova-cinema
MINOVA_RELEASE_KEY_PASSWORD=replace_with_key_password
```

Never commit `local.properties`, the keystore, or either password. Back up the release keystore and credentials securely: Android will reject an in-place update if it is signed by a different certificate. The previous ignored `keystore.properties` format remains a temporary migration fallback, but new environments should use `local.properties`.

### Google Home SDK setup

Google distributes the Home APIs Android SDK through its signed-in developer portal rather than public Maven. Download SDK 1.10.0, extract its `com` directory into `%USERPROFILE%/.m2/repository`, and configure an Android OAuth client with:

- Package: `com.minova.cinema`
- Release certificate SHA-1: `2B:A1:EB:AA:B6:8D:34:8A:92:43:4A:66:A6:76:EC:8A:87:C1:51:ED`

Then add this to the ignored `local.properties` file:

```properties
MINOVA_HOME_SDK_ENABLED=true
```

For a one-off command-line build, the same switch can be supplied as `-PMINOVA_HOME_SDK_ENABLED=true`.

Minova Cinema 2.6.1 and newer require Android TV 10 or newer. Public release builds enable this property and include theater-light control. Google Home access always requires explicit per-home permission from each user. The Home service is an optional Google Play services module: Android TV certification and OS version alone do not guarantee that a TV firmware exposes the Home Permissions API. Minova checks and requests the module at runtime and reports the installed Play services version when it is unavailable. Tapo Cinema Lights provide a local-network fallback on unsupported TVs.

Google currently limits unregistered Home APIs applications to at most 100
OAuth allowlisted testers. Google Home Developer Console registration and the
public Play Store launch path are still marked “Coming soon” in Google's Home
APIs documentation. Until Google opens that registration, add each tester on
the Google Auth Platform Audience page; switching ordinary OAuth publishing
status alone does not remove the Home APIs product-level restriction.

## Architecture

```text
PlexPreferences -> CinemaViewModel -> PlexRepository -> Plex API services
                         |
                         +-> Browse / Search / Detail / Settings
                         +-> PlayerScreen -> Media3 ExoPlayer
                         +-> Cinema Mode -> Plex extras / local bumper
                                        -> optional Google Home lights
                                        -> optional local Tapo lights

GitHub releases/latest -> UpdateViewModel -> TV update dialog
                                             |
                                             +-> DownloadManager -> FileProvider -> Package Installer
```

- `data/local` — on-device connection settings and dismissed Continue Watching IDs.
- `data/remote` — Retrofit endpoints, Plex DTOs, authentication headers, and playback URL construction.
- `data/PlexRepository.kt` — library, metadata, watch-state, watchlist, stream-selection, and playback mapping.
- `presentation` — application state and coroutine-backed catalog/detail operations.
- `update` — GitHub release checks, semantic version comparison, APK download, and installer hand-off.
- `ui` — intro, onboarding, TV browsing, search, details, settings, and fullscreen playback.
- `docs` — the static GitHub Pages product website.

Core stack: Kotlin, Jetpack Compose for TV, AndroidX Media3, Retrofit/OkHttp/Gson, Coil, and Navigation Compose.

## Local-network security

Cleartext HTTP is allowed because Plex servers commonly expose an HTTP address on a trusted LAN. Do not expose port `32400` directly to the public internet. The application stores the configured server and token in its private app preferences; uninstalling or clearing app data removes those preferences.

See [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md) before publishing diagnostic information.

## Project status and source terms

Version 2.8.0 is the current public release. Source is published for inspection and collaboration. No software license has been added yet, so default copyright terms apply until the project owner selects one.

Plex is a trademark of Plex, Inc. Minova Cinema is not endorsed by or affiliated with Plex, Inc.
