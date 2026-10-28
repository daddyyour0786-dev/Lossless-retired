package com.music.bitchord

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.DeviceCodecs
import com.music.bitchord.data.sources.addon.AddonClient
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Optional live integration check. Skipped unless a user supplies LOSSLESS_ADDON_URL. */
class LastWaveLiveSmokeTest {
    @Before fun setup() {
        DeviceCodecs.forced = false
        AppSettings.dolbyAtmos.value = false
    }

    @After fun tearDown() {
        DeviceCodecs.forced = null
        AppSettings.dolbyAtmos.value = true
    }

    @Test fun `user provided LastWave endpoint returns a non-decoy HTTP stream`() = runBlocking {
        val url = System.getenv("LOSSLESS_ADDON_URL").orEmpty()
        assumeTrue("Set LOSSLESS_ADDON_URL to run the optional network test", url.isNotBlank())
        val addon = AddonClient(url)
        assertTrue(addon.manifest().getOrThrow().isPlayable)
        val tracks = addon.search("Daft Punk Get Lucky", AddonClient.TIER_LOSSLESS).getOrThrow()
        assertTrue("The test search returned no tracks", tracks.isNotEmpty())
        val stream = addon.stream(tracks.first().id, AddonClient.TIER_LOSSLESS).getOrThrow()
        val mediaUrl = stream.url
        assertNotNull("The service did not provide an HTTP stream URL", mediaUrl.toHttpUrlOrNull())
        assertFalse("The service returned a decoy", mediaUrl.contains("pranks-cdn", ignoreCase = true))
        assertFalse("The service returned a decoy", mediaUrl.contains("definatelynagato", ignoreCase = true))
        if (stream.transport == "dash") {
            // Request the manifest, not any copyrighted audio segment. Do not
            // print the returned URL: it may include temporary credentials.
            OkHttpClient().newCall(Request.Builder().url(mediaUrl).build()).execute().use { response ->
                assertTrue("DASH manifest was not reachable", response.isSuccessful)
                val body = response.body?.string().orEmpty()
                assertTrue("The stream did not contain a DASH manifest", body.contains("<MPD"))
            }
        }
    }
}
