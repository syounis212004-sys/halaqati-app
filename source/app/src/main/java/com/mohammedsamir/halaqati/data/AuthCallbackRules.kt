package com.mohammedsamir.halaqati.data

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure parser for Supabase implicit-flow auth callbacks.
 *
 * Supabase can return auth session values in either the URL query or fragment. The callback URI
 * itself is matched by scheme + host + exact path rather than a string prefix so look-alike links
 * such as `/callback.evil` can never be accepted as an authentication response.
 */
object AuthCallbackRules {
    data class Callback(
        val type: String = "",
        val accessToken: String = "",
        val refreshToken: String = "",
        val code: String = "",
        val error: String? = null,
    )

    fun parse(url: String?, expectedPrefix: String): Callback? {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank()) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return Callback(error = "رابط المصادقة غير صالح")
        val expected = runCatching { URI(expectedPrefix.trim()) }.getOrNull() ?: return null
        if (!sameCallbackEndpoint(uri, expected)) return null

        val params = linkedMapOf<String, String>()
        for (segment in listOfNotNull(uri.rawQuery, uri.rawFragment)) {
            segment.split('&').forEach { item ->
                val pos = item.indexOf('=')
                if (pos <= 0) return@forEach
                val key = decode(item.substring(0, pos))
                val value = decode(item.substring(pos + 1))
                params[key] = value
            }
        }
        val error = sequenceOf(params["error_description"], params["error"], params["error_code"])
            .firstOrNull { !it.isNullOrBlank() }
        return Callback(
            type = params["type"].orEmpty(),
            accessToken = params["access_token"].orEmpty(),
            refreshToken = params["refresh_token"].orEmpty(),
            code = params["code"].orEmpty(),
            error = error,
        )
    }

    private fun sameCallbackEndpoint(actual: URI, expected: URI): Boolean {
        if (!actual.scheme.equals(expected.scheme, ignoreCase = true)) return false
        if (!actual.host.equals(expected.host, ignoreCase = true)) return false
        if (actual.userInfo != null || actual.port != -1) return false
        return normalizePath(actual.path) == normalizePath(expected.path)
    }

    private fun normalizePath(path: String?): String = path.orEmpty().ifBlank { "/" }.trimEnd('/').ifBlank { "/" }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
