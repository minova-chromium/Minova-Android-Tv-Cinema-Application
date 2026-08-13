# Privacy notes

Minova Cinema is a local Plex client. It does not require a Minova account and the current application does not include a Minova-operated analytics service or advertising SDK.

## Data stored on the device

The Plex server address and token entered during setup are stored in the application’s private local preferences. The app also stores a local set of items dismissed from Continue Watching. Clearing app data or uninstalling the app removes these values.

## Network requests

The app contacts the Plex server configured by the user to request metadata, artwork, playback streams, progress updates, watched state, and stream selections. Plex watchlist features can also use Plex-operated endpoints. Those services are governed by Plex’s own terms and privacy policy.

## Protect the Plex token

A Plex token grants server access and should be handled like a password. Do not paste it into an issue, screenshot, log, or source file. If a token is exposed, revoke it through Plex and update Minova Cinema with a replacement.

For security-sensitive reports, follow [SECURITY.md](SECURITY.md).
