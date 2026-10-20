# xFIN Privacy Policy

_Effective October 20, 2026_

xFIN is a media player for Android phones, tablets and Android TV. It plays media from servers and playlists that you add yourself. This policy explains what the app does with your information.

## The short version

- xFIN does not collect, sell or share any personal data with the developer or anyone else.
- There are no ads, analytics, crash reporters or tracking of any kind.
- Everything you enter stays on your device, and the app only connects to servers you add.

## What is stored on your device

To work, xFIN keeps the following on your device:

- **Sources you add:** Jellyfin server addresses, playlist and program guide URLs, and the usernames, passwords or access tokens for them. Passwords and tokens are encrypted with a key held in the Android Keystore, and that key cannot be exported.
- **Library and guide data** downloaded from your sources, such as channel lists, program listings, titles and artwork.
- **Your activity in the app:** favorites, recently watched channels, playback positions and settings.

This data is excluded from Android cloud backup and device-to-device transfer, so it never leaves the device unless you export it yourself (see "Backups"). Uninstalling xFIN, or clearing its storage, deletes all of it.

## Network connections

xFIN connects only to the addresses you enter and to addresses those sources point to, such as video streams and channel logos listed in a playlist. These servers are run by you or by your provider, not by the developer, and their own privacy policies apply. The developer of xFIN runs no servers and receives no data from the app.

When you sign in to a Jellyfin server, xFIN sends that server a device name and a random device ID, as all Jellyfin apps do, so the server can list your signed-in devices.

Some servers only offer unencrypted (HTTP) connections. If you add a source with an `http://` address, data you exchange with it, including your login, is not encrypted in transit. Use `https://` addresses whenever your server supports them.

## Backups

You can export your sources and favorites to a backup file. The file is encrypted with a password you choose and saved only where you choose to save it. The developer never receives it.

## Children

xFIN is not directed at children and does not knowingly collect information from anyone.

## Changes

If this policy changes, the updated version will be posted at this address with a new effective date.

## Contact

Questions about this policy: phoramus@gmail.com
