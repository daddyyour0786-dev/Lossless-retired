# LSY Free

**LSY Free** is an independent Android fork of [BitChord](https://github.com/kushagrasinghx/BitChord) that adds compatibility with the HTTP add-on protocol used by [LastWave-Native](https://github.com/Clash-Projects/LastWave-Native). It retains the upstream player, interface, and **Automix (beta)** implementation.

> **Status:** This is an independent fork and is not affiliated with BitChord, LastWave, YouTube, or any other service. Some functionality still requires testing on real devices; see [Compatibility and limitations](#compatibility-and-limitations).

## Contents

- [Highlights](#highlights)
- [Requirements](#requirements)
- [Build from source](#build-from-source)
- [Install and configure a LastWave add-on](#install-and-configure-a-lastwave-add-on)
- [Tests](#tests)
- [Compatibility and limitations](#compatibility-and-limitations)
- [Privacy and security](#privacy-and-security)
- [Contributing](#contributing)
- [License and acknowledgements](#license-and-acknowledgements)

## Highlights

- Android music player based on the BitChord codebase.
- Compatibility with supported LastWave HTTP/HTTPS add-ons.
- Add-on search and streaming for supported response formats.
- Existing YouTube fallback when an add-on cannot provide a matching track.
- Optional Automix beta functionality inherited from BitChord.
- Android-focused project; the upstream web player is not included in this fork.

## Requirements

For building locally, use:

- Android Studio.
- JDK 17.
- Android SDK Platform 37 (install the SDK components requested by Gradle/Android Studio).
- Android NDK and CMake 3.22.1.
- Git.

The app targets Android 8.0 (API 26) or newer. Android Studio can install many of the required SDK components when the project is synced.

## Build from source

Clone this repository and enter its directory:

```bash
git clone https://github.com/daddyyour0786-dev/Lossless.git
cd Lossless
```

Build a debug APK:

```bash
./gradlew :app:assembleProdDebug -Pbitchord.bundleWear=false
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleProdDebug -Pbitchord.bundleWear=false
```

Generated APKs are placed under:

```text
app/build/outputs/apk/prod/debug/
```

Choose the universal APK or the APK matching your device's CPU architecture. Debug APKs are for development and testing, not production releases. A distributable release should be built and signed with your own securely managed release key. The application ID is `com.lsyfree.music`.

## Install and configure a LastWave add-on

1. Install a compatible build on your Android device.
2. Open **Settings → Audio quality → Sources → Add an addon**.
3. Enter your own LastWave add-on URL. The add-on may accept its root URL or its `manifest.json` URL. Test the URL and save it.
4. Keep the add-on enabled. To prefer add-on playback when a match is available, select **Lossless** for the relevant connection in **Settings → Audio quality**. You can also use the **Addons** search tab.
5. **Automix** is optional and disabled by default. Enable it under **Settings → Playback → Automix [BETA]** if you want to test it.

**Keep add-on URLs private.** A URL path may contain an access token. Do not post private URLs, tokens, or provider credentials in issues, screenshots, logs, or pull requests.

The add-on URL is not embedded in this repository or the supplied APK. Users must configure their own add-on after installation.

## Tests

Run the LastWave compatibility regression tests on a configured Android development machine:

```bash
./gradlew :app:testProdDebugUnitTest \
  --tests com.music.bitchord.LastWaveCompatibilityTest \
  --tests com.music.bitchord.AddonSourceTest \
  -Pbitchord.bundleWear=false
```

Tests and protocol checks do not, by themselves, prove successful audio playback on a physical device. When reporting results, distinguish automated tests from device testing and describe the device, Android version, source, and steps used.

## Compatibility and limitations

- Implements the LastWave HTTP request signature and streaming intent for token-style `/a/{token}` URLs, along with common LastWave search and stream JSON shapes.
- Protocol checks have returned a DASH/FLAC manifest for a correctly signed request and rejected an unsigned decoy response. This is a protocol-level check, not proof of end-to-end playback.
- Supports HTTP/HTTPS add-ons that return a playable HTTP(S) stream URL.
- Does **not** support encrypted `.lwp` packages, private provider keys, DRM, or add-ons that return only inline DASH data.
- Availability and playback depend on the add-on server, network connection, and device codecs.
- Automix remains the upstream beta implementation. For ordinary catalogue results, it may analyze a matching YouTube rendition even if playback uses an add-on. Tracks selected directly from Addons search are not sent to YouTube for analysis; transitions may fall back to a basic crossfade.
- Matching, lossless playback, transitions, downloads, Android Auto, Cast, and Wear OS behavior should be tested on the relevant physical devices before being described as verified.
- This fork does not provide BitChord releases as LSY Free updates and does not send the fork's open-app presence count to BitChord. Other inherited optional integrations and third-party services may still exist in the source.

Use the app, add-ons, and third-party services only in ways permitted by their terms and applicable law.

## Privacy and security

- Do not commit API keys, signing keys, passwords, access tokens, private add-on URLs, or local configuration files.
- Treat logs and screenshots as potentially sensitive; redact account details, tokens, and personal information before sharing them.
- Review third-party integrations and permissions before using a build.
- Report suspected vulnerabilities privately. See [SECURITY.md](SECURITY.md).

## Contributing

Bug reports, focused fixes, documentation improvements, and tested compatibility reports are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and follow the project's [Code of Conduct](CODE_OF_CONDUCT.md) before participating.

## License and acknowledgements

This project is distributed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for the full license text and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for upstream and third-party attribution. The project contains modified code derived from BitChord and protocol compatibility work based on LastWave-Native; their notices and applicable licenses must be preserved.

LSY Free is an independent project. Product names and trademarks belong to their respective owners.
