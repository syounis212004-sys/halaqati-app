package com.mohammedsamir.halaqati.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mohammedsamir.halaqati.model.UserSession

class SecureSessionStore(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx,
        "halaqati_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(session: UserSession) {
        prefs.edit()
            .putString("a", session.accessToken)
            .putString("r", session.refreshToken)
            .putString("u", session.userId)
            .putString("e", session.email)
            .apply()
    }

    fun load(): UserSession? {
        val access = prefs.getString("a", null) ?: return null
        return UserSession(
            accessToken = access,
            refreshToken = prefs.getString("r", "").orEmpty(),
            userId = prefs.getString("u", "").orEmpty(),
            email = prefs.getString("e", "").orEmpty(),
        )
    }

    /** PKCE verifier of the Google sign-in in progress; one-shot, so a forged callback link cannot reuse it. */
    fun savePkce(verifier: String) { prefs.edit().putString("pkce", verifier).apply() }
    fun hasPkce(): Boolean = !prefs.getString("pkce", null).isNullOrBlank()
    fun takePkceFlag(): Boolean = hasPkce().also { if (it) prefs.edit().remove("pkce").apply() }
    fun takePkce(): String? = prefs.getString("pkce", null)?.takeIf { it.isNotBlank() }.also { prefs.edit().remove("pkce").apply() }

    /** The current FCM registration belongs to the physical app install, not the auth session. */
    fun savePushToken(token: String) {
        if (token.isBlank()) return
        prefs.edit().putString("push_token", token).apply()
    }

    fun loadPushToken(): String? = prefs.getString("push_token", null)?.takeIf { it.isNotBlank() }

    fun clearPushToken() {
        prefs.edit().remove("push_token").apply()
    }

    /** Clear only auth material; device registration is cleaned up explicitly during logout. */
    fun clear() {
        prefs.edit()
            .remove("a")
            .remove("r")
            .remove("u")
            .remove("e")
            .apply()
    }
}
