package com.mohammedsamir.halaqati.ui.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.mohammedsamir.halaqati.BuildConfig
import com.mohammedsamir.halaqati.data.GoogleAuthRules

object NativeGoogleSignIn {
    data class Result(val idToken: String, val rawNonce: String)

    suspend fun launch(context: Context): Result {
        val clientId = resolveWebClientId(context)
        require(clientId.isNotBlank()) {
            "معرّف Google Web Client غير موجود. تأكد من إعداد google-services.json وGoogle Auth."
        }
        val nonce = GoogleAuthRules.newNonce()
        // Primary path: Credential Manager bottom sheet. googleid 1.1.1 keeps API 23 support.
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setNonce(nonce.hashed)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = CredentialManager.create(context).getCredential(
            request = request,
            context = context,
        )
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("لم يُرجع Google بيانات اعتماد صالحة")
        }
        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            error("تعذر قراءة حساب Google: ${e.message ?: "بيانات غير صالحة"}")
        }
        return Result(googleCredential.idToken, nonce.raw)
    }

    /**
     * Secure fallback for devices/configurations where Credential Manager has no eligible
     * Google credential. This keeps the OAuth flow visually attached to Halaqati as a
     * resizable Partial Custom Tab instead of launching a separate full Chrome task.
     * Supabase redirects back to the exact app deep link and MainActivity completes the session.
     */
    fun launchOAuthFallback(context: Context, oauthUrl: String) {
        require(oauthUrl.startsWith("https://")) { "رابط Google OAuth غير صالح" }
        val height = (context.resources.displayMetrics.heightPixels * 0.72f).toInt()
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .setInitialActivityHeightPx(height, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
            .build()
        intent.launchUrl(context, Uri.parse(oauthUrl))
    }

    fun shouldFallbackToOAuth(error: Throwable): Boolean {
        val name = error::class.java.simpleName
        val message = error.message.orEmpty()
        return name == "NoCredentialException" ||
            message.contains("No credentials available", ignoreCase = true) ||
            message.contains("no credential", ignoreCase = true) ||
            message.contains("معرّف Google Web Client غير موجود", ignoreCase = true) ||
            message.contains("Web Client", ignoreCase = true)
    }

    private fun resolveWebClientId(context: Context): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val generated = if (id != 0) context.getString(id).trim() else ""
        return generated.ifBlank { BuildConfig.GOOGLE_WEB_CLIENT_ID.trim() }
    }
}
