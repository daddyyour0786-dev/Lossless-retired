# LSY Free

LSY Free is an independent Android fork of [BitChord](https://github.com/kushagrasinghx/BitChord), with compatibility for the HTTP add-on protocol used by [LastWave](https://github.com/Clash-Projects/LastWave-Native). It retains BitChord's player, interface, and **Automix [BETA]** code.

## Supplied test APK

The supplied `LSY-Free-0.1.0-arm64-debug.apk` is a debug build for **64-bit ARM Android 8.0 or newer**. Allow your browser or file manager to install apps from unknown sources, then open the APK to install it. Its debug signing key is not included in the source; a later build signed with a different key cannot update it in place. Back up your data before replacing it.

## Add your LastWave add-on

1. Open **Settings → Audio quality → Sources → Add an addon**.
2. Paste your own LastWave add-on URL (either the root URL or its `manifest.json` URL), test it, and save it. Keep the URL private: the path can contain an access token.
3. Leave the add-on enabled. To prefer add-on playback where a match is found, choose **Lossless** for the relevant connection in **Settings → Audio quality**. You can also use the **Addons** search tab.
4. Turn on **Settings → Playback → Automix [BETA]**. Automix is off by default and may use extra battery or processing power.

The add-on link provided for this project is **not embedded in the source or APK**. A fresh install requires you to paste it into the app. The app's normal YouTube fallback remains in place when an add-on is unavailable or cannot supply a matching track.

## Compatibility and limitations

- Implements the LastWave HTTP request signature and streaming intent for token-style `/a/{token}` URLs, plus common LastWave search and stream JSON shapes.
- The supplied add-on was probed: its manifest and search responded, and a correctly signed stream request returned a DASH/FLAC manifest. An unsigned stream request returned a decoy, which this fork now rejects. This is a network-protocol check, **not a test of playback or Automix on an Android device**.
- This is compatibility for **HTTP/HTTPS add-ons with a playable HTTP(S) stream URL**. It does not support encrypted `.lwp` packages, private provider keys, DRM, or add-ons that only return inline DASH data. Availability and playback depend on the add-on server and the device's codecs.
- BitChord's Automix remains its upstream beta implementation. For ordinary catalogue results it may analyze a matching YouTube rendition even when playback uses an add-on. Tracks picked directly in the **Addons** search are deliberately not sent to YouTube for analysis; their transition may fall back to a basic crossfade. Matching, lossless playback, transitions, downloads, Android Auto, Cast and Wear OS need device testing for this fork.
- This fork does not offer BitChord releases as LSY Free updates, and it does not send the fork's open-app presence count to BitChord. Other inherited optional integrations and third-party services remain in the source.
- LSY Free is independent and is not affiliated with BitChord, LastWave, YouTube or other services. Use content and services only as permitted by their terms and applicable law.

## Build from source

The Android project is Kotlin/Compose with Gradle. This Android-focused copy omits the upstream web player. You need Android Studio, JDK 17, Android SDK platform 37.0, the NDK and CMake 3.22.1. Android Studio can install the required SDK tools on sync. The app targets Android 8.0 (API 26) or newer.

```bash
# From this directory
./gradlew :app:assembleProdDebug -Pbitchord.bundleWear=false
```

The debug APKs are written under `app/build/outputs/apk/prod/debug/`. Choose the universal APK or the APK matching your phone's CPU. A debug APK is for testing; a distributable release needs your own signing key. The Android application ID is `com.lsyfree.music`, so it can be installed alongside BitChord.

To run the compatibility regression tests on a configured Android development machine:

```bash
./gradlew :app:testProdDebugUnitTest \
  --tests com.music.bitchord.LastWaveCompatibilityTest \
  --tests com.music.bitchord.AddonSourceTest \
  -Pbitchord.bundleWear=false
```

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [LICENSE](LICENSE). The original BitChord README is preserved as [BIT_CHORD_UPSTREAM_README.md](BIT_CHORD_UPSTREAM_README.md).


### Optional: build with GitHub Actions

The included manual workflow builds debug APKs without a release key. Upload this source to your own GitHub repository, open **Actions → Build LSY Free debug APK**, select **Run workflow**, and download the `LSY-Free-debug-APKs` artifact after it completes. These are test builds, not signed production releases.
# Lossless
