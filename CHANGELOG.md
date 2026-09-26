# Changelog

## Unreleased

- Replaced the phone player's text transport controls with large icon buttons for 10-second rewind, play/pause, 10-second forward, and a right-side subtitle language picker with an explicit Off option.
- Removed the technical playback-settings entry and video diagnostics from the phone overlay while retaining the full advanced panel for Android TV.

## 2.9.4 — 2026-09-26

- Added a complete touch-first smartphone interface while preserving the existing Android TV experience and feature set.
- Reworked phone Home around Continue Watching, horizontal Plex shelves, library shortcuts, quick header actions, and persistent bottom navigation inspired by familiar mobile streaming apps.
- Added responsive phone library grids, search with the software keyboard, collection browsing, title action sheets, detail pages, onboarding, Settings, and portrait/landscape layouts.
- Rebuilt phone movie and episode details around full-bleed artwork, centered metadata and ratings, a large Play/Resume action, compact circular actions, synopsis, stream details, seasons, episodes, and cast.
- Reworked phone Settings with a visible Back action, six always-visible category buttons, vertically stacked full-width controls, direct touch timer presets, and phone-sized Plex, Google Home, Tapo, and profile dialogs; changed Discover from a sparkle to a clear compass icon.
- Added tap-driven player controls with ten-second seek actions and phone-specific fullscreen system-bar handling.
- Made Leanback optional and added a standard Android launcher entry so the same application can install and launch on phones, tablets, and TVs.
- Updated DataStore and AndroidX Graphics Path for verified 16 KB page-size compatibility on a current Android emulator.
- Fixed Plex and Tapo LAN access on Android 17 by requesting the required local-network permission and retrying the saved Plex connection as soon as access is granted.
- Added a high-contrast checkmark and Watched label to watched episodes in the phone season list.
- Fixed HTTPS Plex addresses without an explicit port so Tailscale Serve and reverse-proxy URLs use standard port 443 instead of being rewritten to port 32400; connection errors now distinguish DNS, TLS, timeout, and refused-connection failures.
- Replaced remaining TV-only controls on phone paths with native touch controls across onboarding, Plex recovery, Settings, update dialogs, title actions, playback transport, Next Up, inactivity prompts, and in-player playback settings.
- Added responsive phone dialogs, tap-to-skip launch animation, device-neutral Plex identification and wording, and automated phone-player touch coverage while retaining the Android TV navigation branches.

## 2.9.3 — 2026-09-16

Version 2.9.2 was a local test build. This version includes the collection-poster update.

- Added a Collections tab between Series and Watchlist, with collection artwork, title counts, and movies and series in release order.
- Use the collection posters assigned in Plex, displayed in full on portrait cards. Collection identities stay separate across libraries, and titles are loaded from the selected collection on the server.
- Preserved collection scroll position and title focus when returning from details or switching tabs.
- Added remote navigation between collections, their titles, and the top navigation, including larger-text and empty-library coverage.
- Made the top tabs scroll when needed so all navigation remains reachable with larger text.

## 2.9.1 — 2026-09-13

Public release of the Cinema experience update. Version 2.9.0 was a local test build;
2.9.1 updates both those installations and earlier public releases.

- Fixed featured-carousel Left/Right navigation moving focus onto Watchlist. Play now stays selected while browsing; Down selects Watchlist, then Down enters the library. Added an on-screen navigation hint.

- Added combined watch-status, genre, year, resolution and audio-language filters, plus title, added-date, release-date and rating sorting.
- Added Movie Night selections by available time and genre, real Plex collection browsing in release order, and recently viewed titles reported by Plex.
- Added remembered language and per-title track choices, subtitle presentation controls, and a playback recovery dialog with retry/lower-quality actions that preserve position.
- Kept commentary and SDH choices distinct when remembering tracks; ambiguous language matches no longer save another stream or disable unmatched subtitles on the next play.
- Added Cinema Mode presets and a saved custom preset, independent trailer/bumper/lighting switches, configurable Tapo dim/restore levels, manual original-state restoration, and per-bulb command status.
- Added an optional end-of-movie screen with Plex ratings, collection/related suggestions and Home; it never starts another movie automatically.
- Added optional quiet playback of local Plex theme tracks while browsing (off by default).
- Added larger text, stronger focus outlines, higher contrast and reduced motion; the latter also stops automatic featured-title rotation.
- Added artwork loading/unavailable states, visible-load diagnostics and artwork-only cache clearing, separate from login and preferences.
- Extended real-window TV remote tests and discovery tests, including held buttons, carousel focus, profile isolation, and artwork-cache clearing.

## 2.8.3 — 2026-09-07

- Fixed releasing a held OK button automatically selecting Play after the title-actions popup opens. The popup now ignores inherited key repeats and releases until a fresh activation press starts inside it.
- Added real Android-window input tests for held OK with and without repeat events, verifying that playback starts only after a separate new press, plus unit coverage for canceled and mismatched key gestures.

## 2.8.2 — 2026-09-06

### Artwork loading, browsing continuity, and personal settings

- Prioritized visible artwork, replaced catalog-wide prefetching with cancellable two-title look-ahead, and retained the previous decoded backdrop until its replacement loads.
- Moved metadata cache work off the UI thread and isolated cached catalogs by Plex connection credentials.
- Preserved browse position, selected genres, and focused titles across tabs and detail navigation, including refreshed shelves with newly inserted titles.
- Added per-profile Home shelf ordering and visibility, plus a preferred opening tab.
- Added hold-OK title actions for playback, restarting, watched state, Watchlist, and details.
- Added a Tapo dim-and-restore test with per-light results and original-brightness restoration, including cancellation cleanup.
- Added aggregate artwork diagnostics and expanded automated coverage for large libraries, D-pad focus restoration, cache reuse, Home customization, and Tapo settings.
- Added the Minova theatrical pre-roll; promotional capture-only text remains disabled in release builds.

## 2.8.1 — 2026-09-02

### Focus reliability, playback completion, profiles, and Settings polish

- Rebuilt Settings as focused category-detail pages with cleaner cards, stronger text hierarchy, category icons, saved confirmations, and deterministic D-pad entry and return behavior.
- Fixed Home shelf navigation so moving between Recently Added, New Releases, and other rows restores the exact title previously focused in each shelf instead of jumping back to the beginning.
- Added multi-shelf Android TV stress tests using larger title sets, repeated horizontal movement, and rapid Up/Down traversal to prevent header lockups and lost focus.
- Fixed Skip Credits and natural movie completion so playback exits cleanly to Home instead of remaining on a black frame.
- Ensured Cinema Mode lights return to their captured pre-playback on/off state and brightness when playback finishes or Skip Credits is used.
- Fixed Plex Home profile discovery for servers that return users inside wrapped JSON objects while retaining compatibility with the earlier array response.

## 2.8.0 — 2026-09-01

### Playback intelligence, profiles, TV Home, and performance

- Added live Plex playback diagnostics showing Direct Play, Direct Stream, or Transcoding, the converted stream components, and the active decision reason.
- Added Plex-powered Skip Intro, chapter seeking, positive subtitle timing correction, and manual audio-delay correction with a clear passthrough warning.
- Added paged 200-title Plex library requests, a private token-free metadata cache, and Coil artwork prefetching to reduce first-load waits and artwork flashes.
- Added personalized Top Picks, Because You Watched, and Finish Your Series shelves using Plex watch history, ratings, genres, and real server artwork.
- Added Android TV Home Continue Watching and Plex Watchlist channels with title deep links and a foreground Settings action for launcher approval.
- Added Plex Home profile discovery and switching, including managed users and four-digit PIN-protected profiles.
- Added a Plex connection-speed and TV codec assistant with an automatic quality recommendation.
- Expanded Tapo L630 support with KLAP v2/SHIP discovery handling and clear guidance when Tapo's Third-Party Compatibility switch is disabled.
- Smoothed Tapo Cinema Room fades with 100 ms synchronized cosine easing and exact deadline compensation.
- Added large-library pagination, playback-decision, marker/chapter, lighting-ramp, and emulator D-pad regression tests.

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
