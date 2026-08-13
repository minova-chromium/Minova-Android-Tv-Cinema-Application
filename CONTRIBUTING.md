# Contributing to Minova Cinema

Thanks for helping improve the living-room experience.

## Before opening a change

- Search existing issues first.
- Use a feature request for behavior that changes navigation, playback, or Plex synchronization.
- Never commit or post a Plex token, keystore, `keystore.properties`, private server URL, or personal media metadata.
- Keep D-pad focus behavior and the 1280×720 TV safe area in mind for every interface change.

## Development setup

1. Install Android Studio, JDK 17, and Android SDK 37.
2. Clone the repository and open its root folder.
3. Let Gradle sync, then run the `app` configuration on an Android TV emulator or device.
4. Before opening a pull request, run:

```powershell
.\gradlew.bat lintDebug assembleDebug
```

## Pull requests

Keep changes focused and explain:

- the problem being solved;
- the remote-control path used to reach it;
- the TV device and Android version tested;
- direct play or transcoding details for playback changes;
- screenshots with private library information redacted for visual changes.

New user-visible behavior should update `README.md` and `CHANGELOG.md` where appropriate. Do not alter the Minova artwork or product palette without checking the published brand guide.
