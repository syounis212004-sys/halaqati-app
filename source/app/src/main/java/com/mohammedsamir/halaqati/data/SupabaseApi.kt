package com.mohammedsamir.halaqati.data

import com.mohammedsamir.halaqati.BuildConfig
import com.mohammedsamir.halaqati.model.UserSession
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

class SupabaseException(message: String, val status: Int = 0, val sqlState: String? = null) : Exception(message)

class SupabaseApi(private val sessionStore: SecureSessionStore) {
    @Volatile private var refreshFailures = 0
    companion object {
        const val APP_DEEP_LINK = "com.mohammedsamir.halaqati://auth/callback"
        /** PostgREST returns at most this many rows per request unless the project raises "Max rows". */
        const val SELECT_PAGE = 1000
    }

    private fun connection(
        url: String,
        method: String,
        auth: Boolean = true,
        prefer: String? = null,
        bearerOverride: String? = null,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 12_000
        readTimeout = 20_000
        setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Type", "application/json")
        val bearer = bearerOverride ?: if (auth) sessionStore.load()?.accessToken else null
        bearer?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
        prefer?.let { setRequestProperty("Prefer", it) }
    }

    private fun call(
        url: String,
        method: String = "GET",
        body: String? = null,
        auth: Boolean = true,
        prefer: String? = null,
        bearerOverride: String? = null,
    ): String {
        val connection = connection(url, method, auth, prefer, bearerOverride)
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching {
                val json = JSONObject(text)
                json.optString("message")
                    .ifBlank { json.optString("msg") }
                    .ifBlank { json.optString("error_description") }
                    .ifBlank { text }
            }.getOrDefault(text)
            val sqlState = runCatching { JSONObject(text).optString("code").takeIf { it.isNotBlank() } }.getOrNull()
            throw SupabaseException(detail.ifBlank { "HTTP $code" }, code, sqlState)
        }
        return text
    }

    fun signIn(email: String, password: String): UserSession {
        val json = JSONObject(
            call(
                "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=password",
                "POST",
                JSONObject().put("email", email).put("password", password).toString(),
                auth = false,
            ),
        )
        return sessionFromAuthJson(json, email).also(sessionStore::save)
    }

    fun signInWithGoogleIdToken(idToken: String, rawNonce: String): UserSession {
        require(idToken.isNotBlank()) { "Google ID token فارغ" }
        val request = JSONObject()
            .put("provider", "google")
            .put("id_token", idToken)
            .put("nonce", rawNonce)
        val json = JSONObject(
            call(
                "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=id_token",
                "POST",
                request.toString(),
                auth = false,
            ),
        )
        return sessionFromAuthJson(json, "").also(sessionStore::save)
    }

    fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        requestedRole: String,
    ): UserSession? {
        val request = JSONObject()
            .put("email", email)
            .put("password", password)
            .put(
                "data",
                JSONObject()
                    .put("full_name", fullName)
                    .put("phone", phone)
                    .put("requested_role", requestedRole),
            )
        val json = JSONObject(
            call(
                "${BuildConfig.SUPABASE_URL}/auth/v1/signup",
                "POST",
                request.toString(),
                auth = false,
            ),
        )
        val accessToken = json.optString("access_token")
        if (accessToken.isBlank()) {
            // Email confirmation is required: remember which address the confirmation link is expected for.
            sessionStore.savePendingEmailLink(email)
            return null
        }
        return sessionFromAuthJson(json, email).also(sessionStore::save)
    }

    /** Revokes this device's refresh token on the server. Best effort: sign-out must also work offline. */
    fun logoutRemote() {
        if (sessionStore.load()?.accessToken.isNullOrBlank()) return
        runCatching { call("${BuildConfig.SUPABASE_URL}/auth/v1/logout?scope=local", "POST", "{}") }
    }

    /** The access token currently stored, so a caller can tell whether another thread already refreshed it. */
    fun currentAccessToken(): String? = sessionStore.load()?.accessToken

    private val refreshLock = Any()
    @Volatile private var refreshBlockedUntil = 0L
    @Volatile private var refreshBlockedError: SupabaseException? = null

    /**
     * RC51: one refresh at a time (single-flight) and a cool-down after a failure.
     * Supabase rotates refresh tokens: every concurrent caller that sent the same token after the first one got
     * "already used" (HTTP 400) and then the whole address was rate limited (HTTP 429). That stampede used to make
     * the sync worker mark queued work as "needs review". Now the first caller refreshes; the others reuse the result.
     * A failed refresh is reported as an expired session (401, retried later), never as invalid data.
     */
    fun refresh(staleAccessToken: String? = null): UserSession? = synchronized(refreshLock) {
        val old = sessionStore.load() ?: return null
        if (old.refreshToken.isBlank()) return null
        // Another caller refreshed while we waited for the lock: use its fresh session.
        if (staleAccessToken != null && old.accessToken != staleAccessToken) return old
        val now = System.currentTimeMillis()
        if (now < refreshBlockedUntil) throw (refreshBlockedError ?: SupabaseException("تعذر تجديد جلسة الدخول مؤقتًا. سيُعاد المحاولة.", 401))
        try {
            val json = JSONObject(
                call(
                    "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token",
                    "POST",
                    JSONObject().put("refresh_token", old.refreshToken).toString(),
                    auth = false,
                ),
            )
            refreshBlockedUntil = 0L
            refreshBlockedError = null
            refreshFailures = 0
            SessionExpiry.expired.value = false
            sessionFromAuthJson(json, old.email).also(sessionStore::save)
        } catch (e: SupabaseException) {
            val limited = e.status == 429
            val wrapped = if (e.status == 400 || e.status == 401 || e.status == 403 || limited) {
                SupabaseException("تعذر تجديد جلسة الدخول. أعد المحاولة بعد قليل أو سجّل الدخول من جديد.", 401, e.sqlState)
            } else e
            refreshBlockedUntil = now + if (limited) 90_000L else 30_000L
            if (!limited && (e.status == 400 || e.status == 401 || e.status == 403)) { refreshFailures++; if (refreshFailures >= 3) SessionExpiry.expired.value = true }
            refreshBlockedError = wrapped
            throw wrapped
        }
    }

    fun googleOAuthUrl(): String {
        // PKCE: the callback carries a one-time code that only this install can exchange, so a link forged by another app is useless.
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val enc = Base64.getUrlEncoder().withoutPadding()
        val verifier = enc.encodeToString(bytes)
        val challenge = enc.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        sessionStore.savePkce(verifier)
        return googleAuthorizeUrl() + "&code_challenge=$challenge&code_challenge_method=s256"
    }

    private fun googleAuthorizeUrl(): String = buildString {
        append(BuildConfig.SUPABASE_URL)
        append("/auth/v1/authorize?provider=google&redirect_to=")
        append(URLEncoder.encode(APP_DEEP_LINK, StandardCharsets.UTF_8.name()))
        append("&prompt=select_account")
    }

    /** Accepts Supabase OAuth or recovery callbacks delivered through the app deep link. */
    fun acceptOAuthCallback(url: String): UserSession? {
        val callback = AuthCallbackRules.parse(url, APP_DEEP_LINK) ?: return null
        callback.error?.takeIf { it.isNotBlank() }?.let { throw SupabaseException(it) }
        if (callback.code.isNotBlank()) {
            val verifier = sessionStore.takePkce() ?: throw SupabaseException("انتهت جلسة تسجيل الدخول. أعد المحاولة من زر Google.")
            val json = JSONObject(
                call(
                    "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=pkce",
                    "POST",
                    JSONObject().put("auth_code", callback.code).put("code_verifier", verifier).toString(),
                    auth = false,
                ),
            )
            return sessionFromAuthJson(json, "").also(sessionStore::save)
        }
        val access = callback.accessToken
        val refresh = callback.refreshToken
        if (access.isBlank() || refresh.isBlank()) return null
        // Tokens in a link are accepted only while this install is waiting for them: our own Google sign-in (no type)
        // or an email link we requested ourselves. The `type` in the link is chosen by its sender and proves nothing.
        val pendingEmail = sessionStore.pendingEmailLink()
        val pkcePending = callback.type.isBlank() && sessionStore.takePkceFlag()
        if (!AuthCallbackRules.tokenLinkAllowed(callback.type, pkcePending, pendingEmail, sessionStore.pendingEmailLinkAt(), System.currentTimeMillis())) {
            throw SupabaseException("رابط الدخول غير موثوق. ابدأ من داخل التطبيق: زر Google أو «نسيت كلمة المرور».")
        }
        // Validate the access token with Supabase before persisting it.  The JWT
        // payload is only used as a fallback for older auth responses; RLS will
        // still validate the bearer token on every data request.
        val verifiedUser = userForToken(access)
        val payload = decodeJwtPayload(access)
        val userId = verifiedUser.optString("id").ifBlank { payload.optString("sub") }
        if (userId.isBlank()) throw SupabaseException("تعذر تحديد حساب Google من جلسة Supabase")
        val email = verifiedUser.optString("email").ifBlank { payload.optString("email") }
        // The emailed link must belong to the account it was requested for; anything else is somebody else's session.
        if (!AuthCallbackRules.tokenEmailMatches(callback.type, pendingEmail, verifiedUser.optString("email"))) {
            throw SupabaseException("رابط الدخول لا يخص الحساب الذي طلبته من هذا الجهاز.")
        }
        if (callback.type.isNotBlank()) sessionStore.clearPendingEmailLink()
        return UserSession(access, refresh, userId, email).also(sessionStore::save)
    }

    fun callbackType(url: String): String = AuthCallbackRules.parse(url, APP_DEEP_LINK)?.type.orEmpty()

    fun requestPasswordReset(email: String) {
        val redirect = URLEncoder.encode(APP_DEEP_LINK, StandardCharsets.UTF_8.name())
        sessionStore.savePendingEmailLink(email)
        call(
            "${BuildConfig.SUPABASE_URL}/auth/v1/recover?redirect_to=$redirect",
            "POST",
            JSONObject().put("email", email).toString(),
            auth = false,
        )
    }

    fun updatePassword(newPassword: String) {
        require(newPassword.length >= 6) { "كلمة المرور يجب أن تكون 6 أحرف على الأقل" }
        call(
            "${BuildConfig.SUPABASE_URL}/auth/v1/user",
            "PUT",
            JSONObject().put("password", newPassword).toString(),
        )
    }

    fun userForToken(accessToken: String): JSONObject = JSONObject(
        call(
            "${BuildConfig.SUPABASE_URL}/auth/v1/user",
            auth = false,
            bearerOverride = accessToken,
        ),
    )

    private fun sessionFromAuthJson(json: JSONObject, fallbackEmail: String): UserSession {
        val user = json.optJSONObject("user") ?: JSONObject()
        return UserSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token"),
            userId = user.optString("id").ifBlank { decodeJwtPayload(json.getString("access_token")).optString("sub") },
            email = user.optString("email").ifBlank { fallbackEmail },
        )
    }

    private fun decodeJwtPayload(jwt: String): JSONObject = runCatching {
        val segment = jwt.split('.').getOrNull(1) ?: return@runCatching JSONObject()
        val padded = segment + "=".repeat((4 - segment.length % 4) % 4)
        JSONObject(String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8))
    }.getOrElse { JSONObject() }

    private fun selectOnce(table: String, query: String, auth: Boolean): JSONArray {
        val text = call("${BuildConfig.SUPABASE_URL}/rest/v1/$table?$query", auth = auth)
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    /**
     * RC27.41: Supabase/PostgREST silently caps every response at its "Max rows" setting (1000 by default), whatever
     * `limit=` the app asks for. Once recitations/attendance passed 1000 rows, the NEWEST rows were dropped (queries are
     * ordered oldest-first), so plans, rankings and reports showed old data. A query asking for more than one page is
     * now read page by page (offset) until the real end or the requested limit.
     */
    fun select(table: String, query: String = "select=*&limit=500", auth: Boolean = true): JSONArray {
        val wanted = Regex("(?:^|&)limit=(\\d+)").find(query)?.groupValues?.get(1)?.toIntOrNull()
        if (wanted == null || wanted <= SELECT_PAGE) return selectOnce(table, query, auth)
        var base = query.replace(Regex("(?:^|&)(limit|offset)=\\d+"), "").trimStart('&')
        val orderMatch = Regex("(?:^|&)order=([^&]*)").find(base)
        base = if (orderMatch == null) "$base&order=id.asc"
        else if (orderMatch.groupValues[1].split(',').any { it.substringBefore('.') == "id" }) base
        else base.replace(orderMatch.value, orderMatch.value + ",id.asc")
        return try {
            val all = JSONArray()
            var offset = 0
            while (all.length() < wanted) {
                val size = minOf(SELECT_PAGE, wanted - all.length())
                val page = selectOnce(table, "$base&limit=$size&offset=$offset", auth)
                for (i in 0 until page.length()) all.put(page.get(i))
                if (page.length() < size) break
                offset += page.length()
            }
            all
        } catch (e: Exception) {
            // A table without an id column cannot be ordered by id: keep the old single request.
            if (e.message.orEmpty().contains("id", ignoreCase = true) && e.message.orEmpty().contains("column", ignoreCase = true)) selectOnce(table, query, auth) else throw e
        }
    }

    fun insert(table: String, obj: JSONObject): JSONArray = JSONArray(
        call(
            "${BuildConfig.SUPABASE_URL}/rest/v1/$table",
            "POST",
            obj.toString(),
            prefer = "return=representation",
        ),
    )

    fun upsert(table: String, obj: JSONObject, onConflict: String? = null): JSONArray {
        val query = onConflict?.let { "?on_conflict=$it" }.orEmpty()
        return JSONArray(
            call(
                "${BuildConfig.SUPABASE_URL}/rest/v1/$table$query",
                "POST",
                obj.toString(),
                prefer = "resolution=merge-duplicates,return=representation",
            ),
        )
    }

    fun patch(table: String, filter: String, obj: JSONObject): JSONArray = JSONArray(
        call(
            "${BuildConfig.SUPABASE_URL}/rest/v1/$table?$filter",
            "PATCH",
            obj.toString(),
            prefer = "return=representation",
        ),
    )

    fun delete(table: String, filter: String): JSONArray = JSONArray(
        call(
            "${BuildConfig.SUPABASE_URL}/rest/v1/$table?$filter",
            "DELETE",
            prefer = "return=representation",
        ),
    )

    fun rpc(name: String, args: JSONObject = JSONObject()): String = call(
        "${BuildConfig.SUPABASE_URL}/rest/v1/rpc/$name",
        "POST",
        args.toString(),
    )

    fun function(name: String, args: JSONObject = JSONObject(), auth: Boolean = true): String = call(
        "${BuildConfig.SUPABASE_URL}/functions/v1/$name",
        "POST",
        args.toString(),
        auth,
    )

    /**
     * Fire-and-forget mutation used by the durable Outbox.
     *
     * Nothing in the queue consumer needs the full inserted/updated row. Asking PostgREST for
     * `return=representation` made every attendance/recitation upload download the row again and
     * amplified latency on weak mobile links. `return=minimal` keeps the request idempotent while
     * making foreground queue flushes substantially lighter.
     */
    fun raw(method: String, path: String, body: String?): String {
        val normalized = method.uppercase()
        val prefer = when {
            normalized == "POST" && path.contains("on_conflict=") -> "resolution=merge-duplicates,return=minimal"
            normalized in setOf("POST", "PATCH", "DELETE") -> "return=minimal"
            else -> null
        }
        return call(
            "${BuildConfig.SUPABASE_URL}$path",
            normalized,
            body,
            prefer = prefer,
        )
    }
}
