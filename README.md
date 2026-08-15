<p align="center">
  <img src="docs/assets/minova-cinema-wordmark.png" width="420" alt="Minova Cinema">
</p>

<p align="center">
  A cinema-first Plex client for Android TV, built in Kotlin with Compose for TV and Media3.
</p>

<p align="center">
  <a href="https://minova-chromium.github.io/Minova-Android-Tv-Cinema-Application/">Website</a> ·
  <a href="https://github.com/minova-chromium/Minova-Android-Tv-Cinema-Application/releases/download/v2.2.1/Minova-Cinema-2.2.1.apk">Download APK</a> ·
  <a href="https://github.com/minova-chromium/Minova-Android-Tv-Cinema-Application/issues">Issues</a>
</p>

## What it does

Minova Cinema connects directly to a Plex Media Server and presents personal movies and series in a D-pad-native television interface. It is an independent Minova project and is not affiliated with Plex, Inc.

- First-launch setup for a local Plex server address and token; no personal token is compiled into the app.
- Separate Home, Movies, and Series destinations with shelves, full-library grids, genre filters, and global search.
- Plex-synced watched state, watchlist, Continue Watching progress, manual mark watched/unwatched, and dismiss-from-continue actions.
- Show details with season artwork, episodes, cast and crew; movie details with trailers when Plex exposes them.
- Next-up experience after an episode finishes.
- Direct D-pad playback: OK toggles play/pause, Left/Right seek, Down opens the bottom controls.
- Original-quality direct play plus Plex transcoding choices for 4K, 1080p, 720p, and 480p.
- Audio and subtitle track selection with language, codec, and channel details.
- Media3 frame-rate matching and supported surround-audio passthrough.
- Minova visual identity, launch sequence, TV banner, and round launcher mark.

> Playback capability is determined by the Android TV device, connected audio equipment, network, source codecs, subtitle format, and Plex server transcoding capacity. “Original” does not guarantee that every file will direct play on every TV.

## Install

1. [Download the Minova Cinema 2.2.1 APK directly](https://github.com/minova-chromium/Minova-Android-Tv-Cinema-Application/releases/download/v2.2.1/Minova-Cinema-2.2.1.apk).
2. Transfer it to an Android TV device and allow installation from the sending app when Android asks.
3. Launch Minova Cinema and enter the Plex server address and token during setup.

The Plex server can be entered as `192.168.1.10:32400` or as a complete `http://`/`https://` URL. Keep the token private—it grants access to the server.

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

Release builds read signing values from an ignored `keystore.properties` file. See [`keystore.properties.example`](keystore.properties.example). The release keystore and its passwords must be backed up; Android updates must be signed with the same key.

## Architecture

```text
PlexPreferences -> CinemaViewModel -> PlexRepository -> Plex API services
                         |
                         +-> Browse / Search / Detail / Settings
                         +-> PlayerScreen -> Media3 ExoPlayer
```

- `data/local` — on-device connection settings and dismissed Continue Watching IDs.
- `data/remote` — Retrofit endpoints, Plex DTOs, authentication headers, and playback URL construction.
- `data/PlexRepository.kt` — library, metadata, watch-state, watchlist, stream-selection, and playback mapping.
- `presentation` — application state and coroutine-backed catalog/detail operations.
- `ui` — intro, onboarding, TV browsing, search, details, settings, and fullscreen playback.
- `docs` — the static GitHub Pages product website.

Core stack: Kotlin, Jetpack Compose for TV, AndroidX Media3, Retrofit/OkHttp/Gson, Coil, and Navigation Compose.

## Local-network security

Cleartext HTTP is allowed because Plex servers commonly expose an HTTP address on a trusted LAN. Do not expose port `32400` directly to the public internet. The application stores the configured server and token in its private app preferences; uninstalling or clearing app data removes those preferences.

See [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md) before publishing diagnostic information.

## Project status and source terms

Version 2.2.0 is the first public release candidate. Source is published for inspection and collaboration. No software license has been added yet, so default copyright terms apply until the project owner selects one.

Plex is a trademark of Plex, Inc. Minova Cinema is not endorsed by or affiliated with Plex, Inc.
