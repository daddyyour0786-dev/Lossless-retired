package com.music.bitchord

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.AddonSource
import com.music.bitchord.data.sources.DeviceCodecs
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.StreamRequest
import com.music.bitchord.data.sources.addon.AddonClient
import com.music.bitchord.data.sources.addon.DetectedFormat
import com.music.bitchord.data.sources.addon.LastWaveRequest
import com.music.bitchord.data.sources.addon.LastWaveResponses
import com.music.bitchord.data.sources.addon.SourceFormats
import com.music.bitchord.playback.StreamContainer
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Regression tests for LastWave's published HTTP protocol; no real user token is used. */
class LastWaveCompatibilityTest {
    private lateinit var server: MockWebServer
    private val seen = mutableListOf<RecordedRequest>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private var streamResponse = STREAM

    @Before fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen.add(request) }
                if (!validSignature(request)) return MockResponse().setResponseCode(401)
                val path = request.requestUrl?.encodedPath.orEmpty()
                val body = when {
                    path.endsWith("/manifest.json") -> MANIFEST
                    path.endsWith("/search") -> SEARCH
                    path.endsWith("/stream/42") -> streamResponse
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        DeviceCodecs.forced = false
        AppSettings.dolbyAtmos.value = false
    }

    @After fun tearDown() {
        server.shutdown()
        DeviceCodecs.forced = null
        AppSettings.dolbyAtmos.value = true
    }

    @Test fun `signature matches the LastWave protocol test vector`() {
        assertEquals(
            "6d920371ff170df9daac0da049cfa914aa0c04e4d9a10c0272009a51ace54dd8",
            LastWaveRequest.signature("https://example.invalid/a/test-token/stream/42?quality=lossless", "GET", 1700000000),
        )
        assertNull(LastWaveRequest.signature("https://example.invalid/ordinary/stream/42", "GET", 1700000000))
    }

    @Test fun `source detection signs its manifest request`() = runBlocking {
        val identified = SourceFormats.identify("${base()}/manifest.json").getOrThrow()
        assertTrue(identified is DetectedFormat.Addon)
        assertTrue(synchronized(seen) { seen.any { it.requestUrl?.encodedPath?.endsWith("/manifest.json") == true } })
    }

    @Test fun `signed LastWave search and stream become a DASH lossless source`() = runBlocking {
        val source = AddonSource(SourceConfig(kind = SourceKind.ADDON, baseUrl = base()))
        val songs = source.search("Example Song", limit = 10)
        assertEquals(1, songs.size)
        assertEquals("Example Song", songs.single().title)
        assertEquals("LOSSLESS", songs.single().sourceQuality)

        val stream = source.stream("42", StreamRequest.Lossless)
        assertNotNull(stream)
        assertEquals("flac", stream!!.format.codec)
        assertEquals(44100, stream.format.sampleRateHz)
        assertEquals(24, stream.format.bitDepth)
        assertEquals("application/dash+xml", StreamContainer.manifestMimeOf(stream.url))

        val search = requestEnding("/search")
        assertEquals("lossless", search.requestUrl!!.queryParameter("quality"))
        assertEquals("none", search.requestUrl!!.queryParameter("atmos"))
        val request = requestEnding("/stream/42")
        assertEquals("stream", request.getHeader("X-LW-Intent"))
        assertEquals("LastWave-Player/1.0", request.getHeader("User-Agent"))
        assertTrue(validSignature(request))
    }

    @Test fun `a LastWave decoy is never selected for playback`() = runBlocking {
        streamResponse = """{"url":"https://cdn.example/pranks-cdn/not-music.mp4","format":"mp4"}"""
        val source = AddonSource(SourceConfig(kind = SourceKind.ADDON, baseUrl = base()))
        assertNull(source.stream("42", StreamRequest.Lossless))
    }

    @Test fun `plain non-LastWave URLs do not receive LastWave signing headers`() {
        val req = LastWaveRequest.apply(okhttp3.Request.Builder().url("https://example.invalid/search"), "https://example.invalid/search").build()
        assertNull(req.header("X-LW-TS"))
        assertNull(req.header("X-LW-Sign"))
        assertFalse(LastWaveRequest.isTokenUrl("https://example.invalid/search"))
    }

    @Test fun `all LastWave search wrappers and camelcase direct URL are accepted`() {
        val row = """{"id":"42","title":"Song","artist":"Artist","streamUrl":"https://example.invalid/a.flac"}"""
        for (body in listOf("[$row]", """{"tracks":[$row]}""", """{"data":[$row]}""", """{"results":[$row]}""", """{"items":[$row]}""", """{"data":{"tracks":[$row]}}""")) {
            val answer = LastWaveResponses.search(json, body)
            assertEquals("42", answer.tracks.single().id)
            assertEquals("https://example.invalid/a.flac", answer.tracks.single().directStreamUrl)
        }
    }

    @Test fun `stream parser handles root and nested payloads with numeric aliases`() {
        val inner = """{"url":"https://example.invalid/test.mpd","format":"dash","sample_rate":"44.1","bit_depth":"24"}"""
        for (body in listOf(inner, """{"stream":$inner}""", """{"data":$inner}""")) {
            val stream = LastWaveResponses.stream(json, body)
            assertEquals("dash", stream.transport)
            assertEquals(44100, stream.sampleRateHz)
            assertEquals(24, stream.bits)
        }
    }

    private fun base() = server.url("/a/test-token").toString().trimEnd('/')
    private fun requestEnding(path: String): RecordedRequest = synchronized(seen) {
        seen.first { it.requestUrl?.encodedPath?.endsWith(path) == true }
    }

    /** An independent server-side verifier, not the method the client calls. */
    private fun validSignature(request: RecordedRequest): Boolean {
        val url = request.requestUrl?.toString() ?: return false
        val path = URI(url).path ?: return false
        val ts = request.getHeader("X-LW-TS") ?: return false
        if (ts.toLongOrNull() == null) return false
        val token = Regex("/a/([^/]+)").find(path)?.groupValues?.get(1) ?: return false
        val message = "$ts\nGET\n$path\n$token"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(TEST_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return expected == request.getHeader("X-LW-Sign")
    }

    companion object {
        private const val TEST_SECRET = "36d96a751b12ee481c281a8a8e64c482d0c1634a22061ab72f40175017818b85"
        private const val MANIFEST = """{"id":"com.lastwave.addon","name":"LastWave","version":"1.0.0","resources":["search","stream"],"settings":[{"key":"quality","default":"lossless","options":[{"value":"lossless"},{"value":"high"},{"value":"low"}]}]}"""
        private const val SEARCH = """{"data":[{"id":"42","title":"Example Song","artist":"Example Artist","duration":200,"format":"flac","audioQuality":"LOSSLESS"}]}"""
        private const val STREAM = """{"data":{"url":"https://cdn.example.invalid/song.mpd","format":"dash","codec":"flac","manifest":"dash","encrypted":false,"sample_rate":"44.1","bit_depth":"24"}}"""
    }
}
