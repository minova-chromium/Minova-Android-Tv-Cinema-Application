# Changelog

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
