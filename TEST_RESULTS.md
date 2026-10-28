# Verification notes

Last checked: 27 October 2026.

> **Note (Lossless rebrand build):** the results below describe the previous
> `LSY Free` build. The application ID is now `com.daddyyour0786.lossless`, the
> display name is `Lossless`, `minSdk` is 31, and the Liquid Glass, artist
> signature and app-language features have been removed. Re-run the build and
> these checks against the current source before quoting them for a release.

## Passed

- `:app:assembleProdDebug` completed successfully for the Android app. The APK produced by that run was an Android debug-signed build for 64-bit ARM devices, version `0.1.0`, and the Android SDK's `apksigner verify` accepted its signature. That run predates the rebrand, so the package, display name and minimum API it carried are not the ones in the tree now (`com.daddyyour0786.lossless`, `Lossless`, API 31): rebuild before quoting this line for a release.
- SHA-256 of that APK: `700066c3ad0c2f37fff3fe58cd388ab24a7b4d2302c31a5bdbc0da6cd277cf2f`.
- 79 targeted Android/JVM add-on tests passed: 59 in `AddonSourceTest`, 12 in `SourceFormatsTest`, 7 in `LastWaveCompatibilityTest`, and the opt-in `LastWaveLiveSmokeTest` against the supplied service. The live test retrieved the DASH manifest, not audio. The private add-on URL is not part of the source or APK.
- 18 targeted AutoMix JVM tests passed: 3 in `AutomixAnalysisSourceTest`, 11 in `AdvancedAutomixPlannerTest`, and 4 in `PhraseBarAutomixTest`.
- Separately, an isolated JVM harness compiled the actual add-on client/parser sources with minimal platform stubs and passed 4 signature, parser, mock-server and live-service checks.

## Not yet verified

- The APK has not been installed or played on an Android device. Real playback, AutoMix transitions, downloads, Android Auto, Cast, Wear OS and other optional integrations remain unverified.
- Track rows picked directly from the Addons search are not sent to YouTube for AutoMix analysis in this inherited implementation; transitions may fall back to a basic crossfade. Other catalogue tracks can be analyzed using a matching YouTube rendition even when played from an add-on.
- Live-server availability, content, authentication policy and supported formats can change independently of this code. APK signature verification confirms the package was signed, not that its contents or behavior have been independently audited.
