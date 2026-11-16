package com.mohammedsamir.halaqati.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.messaging.FirebaseMessaging
import androidx.lifecycle.viewModelScope
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.SupabaseApi
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class Pending(val profile: Profile) : AuthState
    /** RC27.42: a new account that has not yet chosen its name / type (and student, for a guardian). */
    data class NeedsSetup(val profile: Profile) : AuthState
    data class Recovery(val email: String) : AuthState
    /** RC53: approved but frozen by an administrator (for example until a payment is made). */
    data class Frozen(val profile: Profile) : AuthState
    data class Ready(val profile: Profile) : AuthState
    data class Error(val message: String) : AuthState
}

class AppViewModel : ViewModel() {
    private val repo = AppGraph.repo
    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth = _auth.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync = _sync.asStateFlow()

    init {
        bootstrap()
        viewModelScope.launch {
            AppGraph.connectivity.online.collectLatest {
                refreshSync()
            }
        }
        viewModelScope.launch {
            AppGraph.connectivity.syncEpoch.collectLatest { epoch ->
                // Never touch Room from the Main dispatcher. refreshSync() performs every
                // database read on Dispatchers.IO. This is especially important after login,
                // when the account-scoped Outbox is no longer empty/null.
                if (epoch > 0L) refreshSync()
            }
        }
        viewModelScope.launch {
            repo.localMutationEpoch.collectLatest { epoch ->
                if (epoch > 0L) refreshSync()
            }
        }
        // RC62: a locally edited profile (new account name) must show in the header and greeting immediately.
        viewModelScope.launch {
            repo.profileRevision.collectLatest { rev ->
                if (rev > 0) withContext(Dispatchers.IO) {
                    runCatching { repo.cachedProfile() }.getOrNull()?.let { p ->
                        if (_auth.value is AuthState.Ready) _auth.value = authStateFor(p)
                    }
                }
            }
        }
        // RC31: keep the "unsent changes" warning honest while the app just sits open (no event would refresh it).
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                refreshSync()
            }
        }
    }

    private fun authStateFor(profile: Profile): AuthState =
        if (profile.approvalStatus == "approved" && profile.active && profile.frozen && profile.role != com.mohammedsamir.halaqati.model.UserRole.ADMIN) AuthState.Frozen(profile)
        else if (profile.approvalStatus == "approved" && profile.active) AuthState.Ready(profile)
        else if (profile.approvalStatus == "pending" && !profile.onboarded) AuthState.NeedsSetup(profile)
        else AuthState.Pending(profile)

    fun bootstrap() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repo.loggedIn()) {
                _auth.value = AuthState.SignedOut
                refreshSync()
                return@launch
            }

            val cached = runCatching { repo.cachedProfile() }.getOrNull()
            if (cached != null) {
                // RC9.3 FIX3: open from the trusted account-scoped cache immediately.
                // Network profile validation continues after the shell is already usable.
                _auth.value = authStateFor(cached)
                if (_auth.value is AuthState.Ready) registerCurrentPushToken()
                refreshSync()
                runCatching { repo.profileWithRefresh() }
                    .onSuccess { refreshed -> _auth.value = authStateFor(refreshed) }
                return@launch
            }

            try {
                val profile = repo.profileWithRefresh()
                _auth.value = authStateFor(profile)
                if (_auth.value is AuthState.Ready) registerCurrentPushToken()
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                _auth.value = AuthState.Error(e.message ?: "تعذر بدء التطبيق")
            }
            refreshSync()
        }
    }

    /** RC14: explicit pending-approval refresh must bypass the stale-cache fallback. */
    fun refreshApproval() {
        viewModelScope.launch(Dispatchers.IO) {
            val previous = (_auth.value as? AuthState.Pending)?.profile
            try {
                val profile = repo.profileStrictRefresh()
                _auth.value = authStateFor(profile)
                if (_auth.value is AuthState.Ready) registerCurrentPushToken()
            } catch (_: Exception) {
                if (previous != null) _auth.value = AuthState.Pending(previous)
            }
        }
    }

    /** RC27.42: finishes sign-up (name, phone, account type; a guardian also gives the student's id + birth date). */
    fun completeSignup(name: String, phone: String, role: String, nationalId: String, birthDate: String, result: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val error = try {
                repo.completeSignup(name, phone, role, nationalId, birthDate)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "تعذر إكمال الحساب"
            }
            if (error == null) {
                runCatching { repo.profileStrictRefresh() }.onSuccess { _auth.value = authStateFor(it) }
            }
            withContext(Dispatchers.Main) { result(error) }
        }
    }

    fun login(email: String, password: String) {
        _auth.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.login(email, password)
                bootstrap()
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                _auth.value = AuthState.Error("فشل تسجيل الدخول: ${e.message ?: "خطأ غير معروف"}")
            }
        }
    }

    fun register(email: String, password: String, name: String, phone: String, role: String) {
        _auth.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = repo.register(email, password, name, phone, role)
                if (session == null) {
                    _auth.value = AuthState.SignedOut
                } else {
                    bootstrap()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                _auth.value = AuthState.Error("فشل إنشاء الحساب: ${e.message ?: "خطأ غير معروف"}")
            }
        }
    }

    fun googleOAuthUrl(): String = repo.googleOAuthUrl()

    fun loginWithGoogleIdToken(idToken: String, rawNonce: String) {
        _auth.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.loginWithGoogleIdToken(idToken, rawNonce)
                bootstrap()
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                _auth.value = AuthState.Error("فشل تسجيل الدخول بحساب Google: ${e.message ?: "خطأ غير معروف"}")
            }
        }
    }

    fun handleDeepLink(url: String?) {
        if (url.isNullOrBlank() || !url.startsWith(SupabaseApi.APP_DEEP_LINK)) return
        _auth.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val type = repo.authCallbackType(url)
                val session = repo.acceptOAuthCallback(url)
                    ?: error(if (type == "recovery") "رابط الاسترجاع غير صالح أو انتهت صلاحيته" else "لم تصل جلسة Google من Supabase")
                if (session.userId.isBlank()) error("جلسة Supabase غير صالحة")
                if (type == "recovery") {
                    _auth.value = AuthState.Recovery(session.email)
                } else {
                    bootstrap()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                _auth.value = AuthState.Error("تعذر إكمال رابط الدخول: ${e.message ?: "خطأ غير معروف"}")
            }
        }
    }


    fun completePasswordRecovery(newPassword: String, result: (String?) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val error = try {
                repo.updatePassword(newPassword)
                repo.signOut()
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                e.message ?: "تعذر تغيير كلمة المرور"
            }
            withContext(Dispatchers.Main) {
                if (error == null) {
                    _auth.value = AuthState.SignedOut
                    result(null)
                } else {
                    result(error)
                }
            }
        }
    }

    fun cancelPasswordRecovery() {
        repo.signOut()
        _auth.value = AuthState.SignedOut
    }

    fun requestPasswordReset(email: String, result: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                repo.requestPasswordReset(email.trim())
                "تم إرسال رابط استرجاع كلمة المرور إلى بريدك."
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                "تعذر إرسال رابط الاسترجاع: ${e.message ?: "خطأ"}"
            }
            withContext(Dispatchers.Main) { result(message) }
        }
    }

    private fun registerCurrentPushToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isBlank()) return@addOnSuccessListener
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { repo.registerPushToken(token) }
                }
            }
    }

    fun logout() {
        _auth.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.unregisterPushDevice() }
            repo.signOut()
            withContext(Dispatchers.Main) {
                // Rotate the FCM token after logout so another account on the same phone
                // never inherits a token that was registered to the previous user.
                FirebaseMessaging.getInstance().deleteToken()
                _auth.value = AuthState.SignedOut
            }
            refreshSync()
        }
    }

    fun sync() {
        AppGraph.connectivity.requestImmediateSync()
        viewModelScope.launch(Dispatchers.IO) {
            _sync.value = _sync.value.copy(syncing = true, error = null)
            var success = false
            try {
                repo.syncNow()
                success = true
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                val raw = e.message.orEmpty()
                val friendly = if (!repo.online() || raw.contains("resolve host", ignoreCase = true) || raw.contains("hostname", ignoreCase = true)) {
                    "وضع دون اتصال — تغييراتك محفوظة محليًا وستتم مزامنتها تلقائيًا عند عودة الإنترنت"
                } else {
                    "تعذر إكمال المزامنة الآن؛ ستتم إعادة المحاولة تلقائيًا"
                }
                _sync.value = _sync.value.copy(error = friendly)
            }
            refreshSync(lastSync = if (success) repo.lastSyncInstant() else null)
        }
    }

    fun refreshSync(lastSync: String? = null) {
        // pendingQueueSize(), blockedQueueSize() and lastSyncInstant() use synchronous Room DAOs.
        // Calling them from a StateFlow collector on Main crashes Room after an account is stored.
        // Keep the public API fire-and-forget, but do all account/database reads on IO.
        val previousLastSync = _sync.value.lastSync
        val previousError = _sync.value.error
        viewModelScope.launch(Dispatchers.IO) {
            val state = SyncState(
                online = repo.online(),
                pending = repo.pendingQueueSize(),
                blocked = repo.blockedQueueSize(),
                stalePendingMinutes = repo.oldestPendingMinutes(),
                syncing = false,
                lastSync = lastSync ?: repo.lastSyncInstant() ?: previousLastSync,
                error = previousError,
            )
            _sync.value = state
        }
    }
}
