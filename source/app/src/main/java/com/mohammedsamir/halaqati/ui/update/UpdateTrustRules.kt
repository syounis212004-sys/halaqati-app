package com.mohammedsamir.halaqati.ui.update

import java.net.URI

/**
 * Pure trust rules for the self-updater.
 *
 * The update manifest is the single root of trust, so the APK it points to must stay on a host we publish to
 * and must be pinned by a SHA-256. A manifest without a digest is treated as untrusted, never as "nothing to check".
 */
object UpdateTrustRules {
    private val releaseHosts = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /** HTTPS only, and only the manifest's own host or GitHub release downloads. */
    fun trustedApkUrl(apkUrl: String, manifestUrl: String): Boolean {
        val apk = runCatching { URI(apkUrl.trim()) }.getOrNull() ?: return false
        val manifest = runCatching { URI(manifestUrl.trim()) }.getOrNull() ?: return false
        if (!apk.scheme.equals("https", ignoreCase = true) || apk.userInfo != null) return false
        val host = apk.host?.lowercase() ?: return false
        return host == manifest.host?.lowercase() || host in releaseHosts
    }

    /** A usable digest is exactly 64 hex characters. */
    fun validSha256(value: String?): Boolean = value != null && value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** The download is accepted only when a digest was published and it matches. */
    fun digestMatches(expected: String?, actual: String?): Boolean =
        validSha256(expected) && actual != null && expected.equals(actual, ignoreCase = true)
}
