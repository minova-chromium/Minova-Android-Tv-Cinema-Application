# Changelog

## 2.7.0 — 2026-08-31

### Cinematic browsing and broader Tapo compatibility

- Rebuilt Home, Movies, and Series around a cinematic, full-screen Plex artwork experience with a featured carousel and smooth fade-through-black backdrop transitions.
- Added a cleaner two-stage Movies/Series flow: hero and Continue Watching first, then a dedicated D-pad genre and poster browser.
- Added stable compact genre controls, larger poster shelves, a full-library grid, and an alphabetical jump rail for fast TV navigation.
- Improved D-pad focus restoration, shelf transitions, artwork updates, title visibility, and Continue Watching card proportions.
- Added dual-protocol Tapo local control that tries modern KLAP first and falls back to legacy Secure Passthrough for compatible older lights.
- Improved Tapo discovery by preserving each device's advertised HTTP/HTTPS transport and port.
- Added regression coverage for Tapo protocol selection, cryptographic session handling, and fallback behavior.

## 2.6.4 — 2026-08-29

### Settings polish and more reliable Tapo discovery

- Rebuilt Settings into clean TV-sized cards with consistent white, gray, and cyan text hierarchy.
- Added D-pad sliders for the ambient screensaver delay and playback inactivity timer.
- Fixed the Tapo Cinema Room panel being clipped by using a true full-screen TV dialog with balanced controls.
- Added a larger, smoothly scrollable light list with compatible-light counts and clear D-pad guidance.
- Added a bounded same-network fallback scan for compatible Tapo lights that do not answer UDP discovery broadcasts.
- Improved discovery results so the app reports how many compatible lights were found and how many required fallback discovery.

> Local control remains limited to compatible KLAP v1/v2 Tapo bulbs and light strips on the same network.

## 2.6.3 — 2026-08-28

### Tapo Cinema Lights and Google Home recovery

- Added local TP-Link Tapo Cinema Lights as a fallback for televisions that do not expose the Google Home Permissions API.
- Added encrypted Tapo credential storage backed by Android Keystore, local UDP discovery, and authenticated friendly light names.
- Added persistent per-light Cinema Room assignment with synchronized four-second dimming and restoration to each light's previous brightness.
- Added automatic Tapo rediscovery after app restarts and a fully D-pad-accessible setup flow in Settings.
- Added explicit Google Play services Home module availability checks and installation requests before Google Home permission setup.
- Improved Google Home diagnostics to report the installed Google Play services version when TV firmware does not provide the required service.
- Updated the privacy policy and terms for local smart-light discovery and control.

> Tapo control currently supports compatible KLAP v1/v2 bulbs and light strips on the same local network.

## 2.6.2 — 2026-08-27

### Google Home availability and Settings

- Fixed the AGP 9 source-set configuration that accidentally omitted the Google Home controller from the v2.6.1 APK.
- Updated light control to the Google Home SDK 1.10 device-type trait API.
- Added dimmable, color-temperature, extended-color, and on/off light discovery with persistent per-light Cinema assignments.
- Moved Google Home setup to the top of Settings and added change-home and refresh actions.
- Made Settings headings and option labels explicitly white with gray supporting text.
- Replaced the raw Home Permissions API error shown on unsupported Android Studio TV emulators with actionable device guidance.
- Added Google Home privacy disclosures and public terms in preparation for OAuth verification.

## 2.6.1 — 2026-08-27

### Google Home theater lighting

- Enabled the native Google Home Cinema Mode integration in the signed production build.
- Added the Android OAuth production identity for `com.minova.cinema` using the established Minova Cinema release certificate.
- Added Google Play services Home module delivery metadata and Android 11+ package visibility declarations.
- Added OAuth branding, privacy links, and tester authorization for the initial Home APIs rollout.
- Raised the minimum supported platform to Android TV 10 (API 29), as required by the Google Home APIs SDK.

## 2.6.0 — 2026-08-27

### Cinema Mode and timers

- Added a preloaded Media3 sequence with up to two random trailers from unwatched Plex movies, an optional local 4K/Atmos bumper, and the main feature.
- Added a separate Cinema Mode switch for disabling Plex trailers while retaining the local bumper and theater-light behavior.
- Fixed existing Plex Watchlist imports by using reliable small-page pagination, retrying transient Discover failures, requesting local GUIDs, and safely matching legacy title/year entries.
- Added optional native Google Home light discovery, theater-light assignment, and four-second fades to 0%/15% in Home SDK builds.
- Added user-adjustable ambient screensaver and Continue Watching safety timers in Settings.

## 2.5.1 — 2026-08-26

### Plex Watchlist

- Fixed Plex Watchlist titles not appearing even when they are available on the configured server.
- Resolved account-wide Discover GUIDs through the local Plex Media Server, matching Plex's official client behavior.
- Added pagination, external-ID fallback matching, and preserved Plex Watchlist order.
- Added regression coverage for Watchlist resolution and encoded Plex GUID requests.

## 2.5.0 — 2026-08-15

### Ambient screensaver

- Added an OLED-friendly ambient mode after five minutes without D-pad input.
- Added smooth DVD-style Minova logo motion with exact edge bouncing and neon color changes.
- Suppressed ambient mode while Media3 is actively playing video.
- Made the first D-pad gesture dismiss ambient mode without activating the focused control underneath.

## 2.4.1 — 2026-08-15

### Automatic updates

- Fixed the Android TV package installer not appearing after an APK download completed.
- Moved install permission and package installer hand-offs into the foreground activity so Android cannot block them as background launches.
- Added visible download progress, paused/error reporting, and recovery after the app process restarts.
- Preserved pending downloads until the installer has actually opened successfully.

## 2.4.0 — 2026-08-15

### Playback

- Added an optional 10-second autoplay countdown to the Next Up screen.
- Added persistent Autoplay and Continue Watching safety toggles in Settings.
- Added a three-hour inactivity check with a 30-second response countdown.
- Added Skip Credits when Plex supplies analyzed credits markers for an episode.

## 2.3.0 — 2026-08-15

### Automatic updates

- Added automatic version checks against the latest Minova Cinema GitHub Release.
- Added a D-pad-native update dialog with release notes and Later/Update Now actions.
- Added background APK downloads and secure FileProvider hand-off to Android's package installer.
- Enforced the Minova release signing configuration for every release artifact.

## 2.2.2 — 2026-08-15

### Fixes and improvements

- Restored D-pad navigation from a show's title into its season posters.
- Fixed episode selection so choosing an episode starts playback directly.
- Added the active rendered video resolution beside the remaining time during playback.

## 2.2.1 — 2026-08-13

### Improvements

- Added a full-width playback timeline that appears while seeking with the TV remote.
- Shows elapsed and total playback time while rewinding or fast-forwarding.
- Keeps the seek overlay visible while Left or Right is held or pressed repeatedly.
- Replaced website mockups and the previous collection collage with direct application captures.
- Added dedicated Movies and Series website panels showing their separate Continue Watching shelves.

## 2.2.0 — 2026-08-13

First public release candidate.

### Highlights

- Movies, series, genre shelves, full grid browsing, genre filters, and server-wide search.
- Separate movie and series Continue Watching shelves with remaining-time labels.
- Plex-synced watched state and watchlist, plus manual watched/unwatched actions.
- Show, season, episode, cast and crew details; Plex extras and movie trailers when available.
- Fullscreen playback with immediate OK-button play/pause and bottom-first settings access.
- Original/4K/1080p/720p/480p playback options, audio tracks, subtitles, frame-rate matching, and supported passthrough audio.
- Next-episode screen and playback-progress synchronization.
- Updated Minova Cinema identity, intro, launcher artwork, TV banner, and responsive product website.
