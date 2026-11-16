package com.mohammedsamir.halaqati.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * RC42: the "＋" sheet asks the destination page to open one of its add dialogs ("session", "student", "announcement").
 * The request survives the page being created late and is consumed once by the page that owns it.
 */
object AddActionBus {
    private val kind = MutableStateFlow<String?>(null)
    val requested: StateFlow<String?> get() = kind
    fun request(what: String) { kind.value = what }
    fun consume() { kind.value = null }
}
