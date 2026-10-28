# Verification notes

Last checked: 27 October 2026.

## Passed

- `:app:assembleProdDebug` completed successfully for the Android app. The supplied `LSY-Free-0.1.0-arm64-debug.apk` is an Android debug-signed build for 64-bit ARM devices, with application ID `com.lsyfree.music`, display name `LSY Free`, version `0.1.0`, and minimum Android API 26. The Android SDK's `apksigner verify` accepted its signature.
- SHA-256 of that APK: `700066c3ad0c2f37fff3fe58cd388ab24a7b4d2302c31a5bdbc0da6cd277cf2f`.
- 79 targeted Android/JVM add-on tests passed: 59 in `AddonSourceTest`, 12 in `SourceFormatsTest`, 7 in `LastWaveCompatibilityTest`, and the opt-in `LastWaveLiveSmokeTest` against the supplied service. The live test retrieved the DASH manifest, not audio. The private add-on URL is not part of the source or APK.
- 18 targeted AutoMix JVM tests passed: 3 in `AutomixAnalysisSourceTest`, 11 in `AdvancedAutomixPlannerTest`, and 4 in `PhraseBarAutomixTest`.
- Separately, an isolated JVM harness compiled the actual add-on client/parser sources with minimal platform stubs and passed 4 signature, parser, mock-server and live-service checks.

## Not yet verified

- The APK has not been installed or played on an Android device. Real playback, AutoMix transitions, downloads, Android Auto, Cast, Wear OS and other optional integrations remain unverified.
- Track rows picked directly from the Addons search are not sent to YouTube for AutoMix analysis in this inherited implementation; transitions may fall back to a basic crossfade. Other catalogue tracks can be analyzed using a matching YouTube rendition even when played from an add-on.
- Live-server availability, content, authentication policy and supported formats can change independently of this code. APK signature verification confirms the package was signed, not that its contents or behavior have been independently audited.
