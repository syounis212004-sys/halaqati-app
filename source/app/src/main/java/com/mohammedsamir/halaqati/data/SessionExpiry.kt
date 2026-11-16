package com.mohammedsamir.halaqati.data

import kotlinx.coroutines.flow.MutableStateFlow

/** True after repeated failed token refreshes: the UI offers a clear sign-in prompt. Queued work is kept (sign-out never clears the local database). */
object SessionExpiry {
    val expired = MutableStateFlow(false)
}
