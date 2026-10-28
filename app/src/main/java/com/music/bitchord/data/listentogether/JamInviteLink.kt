package com.music.bitchord.data.listentogether

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class ParsedJamInvite(
    val code: String,
    val serverUrl: String? = null,
)

/** Relays a party invite from [com.music.bitchord.MainActivity] to Compose. */
object JamInviteLink {

    // The old BitChord links remain readable, so existing invitations still work.
    const val ORIGIN = "https://bitchord.kushagrasingh.in"
    const val LSY_ORIGIN = "https://daddyyour0786-dev.github.io"

    private const val EXTRA_CONSUMED = "bitchord.jamInviteConsumed"
    private const val HOST = "bitchord.kushagrasingh.in"
    private const val LSY_HOST = "daddyyour0786-dev.github.io"
    private const val CUSTOM_SCHEME = "lsyfree"
    private const val LEGACY_SCHEME = "bitchord"
    private const val CUSTOM_HOST = "party"

    private val _pending = MutableStateFlow<ParsedJamInvite?>(null)
    val pending: StateFlow<ParsedJamInvite?> = _pending.asStateFlow()

    /** Reads a web invite from a cold launch or a new intent on the existing task. */
    fun consume(intent: Intent?): Boolean {
        if (
            intent == null ||
            intent.action != Intent.ACTION_VIEW ||
            intent.getBooleanExtra(EXTRA_CONSUMED, false)
        ) return false

        val invite = parseInvite(intent.dataString) ?: return false
        intent.putExtra(EXTRA_CONSUMED, true)
        _pending.value = invite
        return true
    }

    fun handled() {
        _pending.value = null
    }

    /** Returns the normalized party code only for the public invite URL shape. */
    fun parse(value: String?): String? = parseInvite(value)?.code

    /**
     * Accepts LSY Free web and app links, plus existing BitChord invites.
     * A server named by the link is still subject to the in-app confirmation.
     */
    fun parseInvite(value: String?): ParsedJamInvite? {
        val uri = runCatching { URI(value ?: return null) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val query = uri.rawQuery
        val server = extractQueryParam(query, "server")?.let { sanitizeServerUrl(it) }

        // App-only links are also used by the fallback web page. Keep the old
        // BitChord scheme so invitations shared before this update still work.
        if ((scheme == CUSTOM_SCHEME || scheme == LEGACY_SCHEME) && host == CUSTOM_HOST) {
            val pathPart = uri.path.orEmpty().trim('/').takeIf { it.isNotBlank() }
            val candidate = pathPart ?: extractQueryParam(query, "code") ?: return null
            val code = cleanCode(candidate) ?: return null
            return ParsedJamInvite(code = code, serverUrl = server)
        }

        // A regular HTTPS URL is clickable in WhatsApp. The query carries the
        // code because GitHub Pages serves one static /invite/ page for all parties.
        if ((scheme == "https" || scheme == "http") && host == LSY_HOST) {
            if (uri.rawUserInfo != null || uri.port != -1) return null
            if (uri.path != "/invite" && uri.path != "/invite/") return null
            val candidate = extractQueryParam(query, "code") ?: return null
            if (!CODE_PATTERN.matches(candidate)) return null
            return ParsedJamInvite(code = candidate.uppercase(), serverUrl = server)
        }

        // Existing BitChord web invite.
        if (scheme == "https" && host == HOST) {
            val match = INVITE_PATH.matchEntire(uri.path.orEmpty()) ?: return null
            val code = match.groupValues[1].uppercase()
            return ParsedJamInvite(code = code, serverUrl = server)
        }

        return null
    }

    fun url(code: String, customServer: String? = null): String {
        val link = "$LSY_ORIGIN/invite/?code=${code.uppercase()}"
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank() && !base.equals(ORIGIN, ignoreCase = true)) {
            val encoded = URLEncoder.encode(base, "UTF-8")
            "$link&server=$encoded"
        } else {
            link
        }
    }

    fun schemeUrl(code: String, customServer: String? = null): String {
        val normalizedCode = code.uppercase()
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank()) {
            val encoded = runCatching { URLEncoder.encode(base, "UTF-8") }.getOrDefault(base)
            "$CUSTOM_SCHEME://party/$normalizedCode?server=$encoded"
        } else {
            "$CUSTOM_SCHEME://party/$normalizedCode"
        }
    }

    private fun cleanCode(raw: String): String? {
        val cleaned = raw.filter { it.isLetterOrDigit() }.uppercase()
        return if (cleaned.length == ListenTogether.CODE_LENGTH) cleaned else null
    }

    private fun extractQueryParam(query: String?, paramName: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split('&').asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.isNotEmpty() && it[0].equals(paramName, ignoreCase = true) }
            ?.getOrNull(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    }

    private fun sanitizeServerUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('/') ?: return null
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return withScheme
    }

    private val CODE_PATTERN = Regex("""[A-Za-z0-9]{${ListenTogether.CODE_LENGTH}}""")
    private val INVITE_PATH = Regex("""/invite/([A-Za-z0-9]{${ListenTogether.CODE_LENGTH}})""")
}

