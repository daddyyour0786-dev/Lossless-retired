pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor is published via JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "Lossless"
include(":shared")
include(":sharedUi")

// The Android module only when there is an SDK to build it against. A machine
// set up for the desktop alone has none, and including it there fails at
// configuration time — before the desktop module it has nothing to do with can
// build at all.
// Read as a Properties file, not line by line: Android Studio writes the path
// escaped (`C\:\Users\...` on Windows), and only Properties undoes that.
val androidSdk: String? = file("local.properties")
    .takeIf { it.isFile }
    ?.let { f -> java.util.Properties().apply { f.inputStream().use(::load) } }
    ?.getProperty("sdk.dir")
    ?.trim()
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")

if (androidSdk != null && file(androidSdk).isDirectory) {
    include(":app")
    // TV and Wear OS are switched off for now. The modules are still in the
    // tree and still build on their own — the phone app simply carries neither,
    // so CI has two fewer modules and the APK has no watch APK inside it (see
    // the wear bundling that used to live in app/build.gradle.kts). To bring
    // either back, uncomment its include here and restore the matching block in
    // app/build.gradle.kts / the .github workflow.
    // include(":tv")
    // include(":wearos")
} else {
    logger.lifecycle("No Android SDK found — building the desktop targets only.")
}
