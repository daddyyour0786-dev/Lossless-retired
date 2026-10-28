# Contributing to LSY Free

Thank you for considering a contribution! Useful contributions include bug fixes, documentation improvements, compatibility fixes, test coverage, and carefully scoped features.

## Before you start

- Check existing issues and pull requests to avoid duplicating work.
- For a substantial feature, architecture change, or UI redesign, open an issue first to discuss the proposal.
- For small fixes and documentation corrections, a pull request is usually fine.
- Be respectful and follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Development environment

The Android project uses Kotlin, Jetpack Compose, and Gradle. You will generally need:

- Android Studio.
- JDK 17.
- Android SDK Platform 37 and the SDK components requested by the project.
- Android NDK and CMake 3.22.1.
- Git.

Some optional components or workflows may have additional requirements. Follow the repository's current Gradle configuration and CI workflow if they differ from this guide.

## Build

From the repository root:

```bash
./gradlew :app:assembleProdDebug -Pbitchord.bundleWear=false
```

On Windows:

```powershell
.\gradlew.bat :app:assembleProdDebug -Pbitchord.bundleWear=false
```

## Run compatibility tests

```bash
./gradlew :app:testProdDebugUnitTest \
  --tests com.music.bitchord.LastWaveCompatibilityTest \
  --tests com.music.bitchord.AddonSourceTest \
  -Pbitchord.bundleWear=false
```

If a test cannot be run in your environment, say so in the pull request and include the reason. Do not claim that a feature works on a physical device unless you have actually tested it on that device.

## Branch and pull request workflow

1. Fork the repository.
2. Create a descriptive branch, for example `fix/addon-timeout` or `docs/build-instructions`.
3. Keep the change focused on one issue or closely related set of changes.
4. Write clear commit messages.
5. Run relevant tests and review your diff for unrelated changes or accidentally included secrets.
6. Open a pull request against the appropriate branch and explain the problem, solution, testing performed, and any limitations.
7. Respond constructively to review feedback.

## Code and testing guidelines

- Follow existing Kotlin, Gradle, and Jetpack Compose conventions.
- Prefer small, maintainable changes that fit the current architecture.
- Add or update tests for changed behavior and edge cases.
- Avoid unnecessary dependencies. Explain why a new dependency is needed and verify its license and maintenance status.
- Keep documentation in sync with user-visible behavior.
- Do not bundle private service URLs, API keys, provider credentials, tokens, signing keys, or personal data.
- Redact secrets from logs, screenshots, sample configuration, and test fixtures.
- For protocol or playback changes, describe the test source and the exact level of verification.

### Audio and playback claims

When making audio-related changes, distinguish between metadata, decoder output, internal DSP format, Android `AudioTrack`, system audio/HAL state, advertised device capabilities, and the actual physical output. App telemetry alone is not proof of the format delivered by the physical audio endpoint.

Report what you measured and how you measured it. Be explicit about limitations.

## Licensing and third-party code

This project is licensed under GPL-3.0 and includes code derived from upstream projects. Contributions must be compatible with the project's license and must not remove existing copyright, license, or attribution notices.

When adding dependencies or adapting external code:

- Check the license and compatibility before including it.
- Preserve required notices and update `THIRD_PARTY_NOTICES.md` when appropriate.
- Identify copied or adapted code in the pull request.
- Do not submit code you do not have the right to contribute.

By submitting a contribution, you agree that it may be distributed under the project's applicable license. If you have questions about licensing, raise them before submitting.

## Security issues

Please do not report exploitable vulnerabilities in public issues or pull requests. Follow [SECURITY.md](SECURITY.md) to report them privately.

## Review

Pull requests are reviewed for correctness, maintainability, compatibility, licensing, and fit with the project's goals. A maintainer may request changes or decline a contribution that is out of scope or cannot be adequately tested.
