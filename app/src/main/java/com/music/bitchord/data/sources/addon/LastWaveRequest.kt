/*
 * Lossless: LastWave HTTP add-on request compatibility.
 *
 * The wire format and public fallback signer are derived from
 * Clash-Projects/LastWave-Native, AddonClient.kt (GPL-3.0), commit
 * 24d2bf52c9c55f9067c273ce89a40e91a69ef433. See THIRD_PARTY_NOTICES.md.
 */
package com.music.bitchord.data.sources.addon

import java.net.URI
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.Request

/**
 * LastWave's HTTP add-ons put a token in `/a/{token}` and expect an HMAC over
 * the timestamp, method, decoded path and token. A request can otherwise
 * appear successful but return a decoy instead of music. No add-on URL or user
 * token is bundled here; those are supplied by the user in Sources.
 *
 * The fallback key is the one published by LastWave's open-source client. A
 * service using a different private key or an encrypted .lwp package is not
 * made compatible by this implementation.
 */
internal object LastWaveRequest {
    private const val FALLBACK_SECRET = "36d96a751b12ee481c281a8a8e64c482d0c1634a22061ab72f40175017818b85"
    private val tokenPath = Regex("/a/([^/]+)")

    fun isTokenUrl(url: String): Boolean = runCatching {
        tokenPath.containsMatchIn(URI(url).path.orEmpty())
    }.getOrDefault(false)

    /** Pure signing operation, split out so tests can use a fixed timestamp. */
    fun signature(url: String, method: String, timestamp: Long): String? = runCatching {
        val path = URI(url).path ?: "/"
        val token = tokenPath.find(path)?.groupValues?.get(1) ?: return null
        val message = "$timestamp\n${method.uppercase(Locale.ROOT)}\n$path\n$token"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(FALLBACK_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }.getOrNull()

    /** Adds LastWave headers only to token-shaped URLs; ordinary add-ons are unchanged. */
    fun apply(builder: Request.Builder, url: String, isStream: Boolean = false): Request.Builder {
        val timestamp = System.currentTimeMillis() / 1000L
        val signature = signature(url, "GET", timestamp) ?: return builder
        builder.header("X-LW-TS", timestamp.toString())
        builder.header("X-LW-Sign", signature)
        if (isStream) {
            builder.header("X-LW-Intent", "stream")
            builder.header("User-Agent", "LastWave-Player/1.0")
        }
        return builder
    }
}
