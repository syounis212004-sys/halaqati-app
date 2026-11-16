package com.mohammedsamir.halaqati.data

import java.security.MessageDigest
import java.util.UUID

data class GoogleNonce(val raw: String, val hashed: String)

object GoogleAuthRules {
    fun newNonce(): GoogleNonce {
        val raw = UUID.randomUUID().toString()
        return GoogleNonce(raw, sha256Hex(raw))
    }

    fun sha256Hex(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(java.util.Locale.US, it) }
}
