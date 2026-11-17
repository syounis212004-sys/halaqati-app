package com.mohammedsamir.halaqati.ui.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTrustRulesTest {
    private val manifest = "https://syounis212004-sys.github.io/halaqati-web/version.json"
    private val sha = "23ef2bf046dd51efe6d0187a552a4b7949f6ecace491f14eccb643c868d72606"

    @Test fun apkMustComeFromThePublishingHostOverHttps() {
        assertTrue(UpdateTrustRules.trustedApkUrl("https://syounis212004-sys.github.io/halaqati-web/Halaqati-latest.apk", manifest))
        assertTrue(UpdateTrustRules.trustedApkUrl("https://github.com/o/r/releases/download/v1/a.apk", manifest))
        assertFalse(UpdateTrustRules.trustedApkUrl("http://syounis212004-sys.github.io/halaqati-web/Halaqati-latest.apk", manifest))
        assertFalse(UpdateTrustRules.trustedApkUrl("https://evil.example/Halaqati-latest.apk", manifest))
        assertFalse(UpdateTrustRules.trustedApkUrl("https://syounis212004-sys.github.io@evil.example/a.apk", manifest))
        assertFalse(UpdateTrustRules.trustedApkUrl("https://syounis212004-sys.github.io.evil.example/a.apk", manifest))
        assertFalse(UpdateTrustRules.trustedApkUrl("not a url", manifest))
    }

    @Test fun missingDigestIsNeverAPass() {
        // The audit finding: a manifest without sha256 used to count as "verified".
        assertFalse(UpdateTrustRules.digestMatches(null, sha))
        assertFalse(UpdateTrustRules.digestMatches("", sha))
        assertFalse(UpdateTrustRules.digestMatches("abc", "abc"))
        assertFalse(UpdateTrustRules.digestMatches(sha, null))
    }

    @Test fun digestComparisonIgnoresCaseOnly() {
        assertTrue(UpdateTrustRules.digestMatches(sha, sha.uppercase()))
        assertFalse(UpdateTrustRules.digestMatches(sha, sha.replaceFirst('2', '3')))
        assertFalse(UpdateTrustRules.validSha256("z".repeat(64)))
    }
}
