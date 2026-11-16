package com.mohammedsamir.halaqati.data

import com.mohammedsamir.halaqati.plans.WorkMode
import com.mohammedsamir.halaqati.plans.WorkModeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RC27.10: the app-wide work mode (regular halaqa / program / mixed), derived from the halaqat the person can reach
 * and the halaqa chosen in the scope bar. Screens read [state]; HalaqatiApp keeps it fresh.
 */
data class WorkModeState(
    val mode: WorkMode = WorkMode.REGULAR,
    val selectedId: String? = null,
    /** halaqa id -> halaqa_type, for filtering students by kind. */
    val typeById: Map<String, String?> = emptyMap(),
    val activeIds: List<String> = emptyList(),
    val nameById: Map<String, String> = emptyMap(),
) {
    /** Ids of active program halaqat inside the current selection (the selected one, or all reachable ones). */
    val programIds: Set<String>
        get() = (if (selectedId != null) listOf(selectedId) else activeIds).filter { WorkModeRules.isProgramType(typeById[it]) }.toSet()
}

object WorkModeHolder {
    private val _state = MutableStateFlow(WorkModeState())
    val state: StateFlow<WorkModeState> = _state.asStateFlow()

    private val _kind = MutableStateFlow<WorkMode?>(null)
    /** MIXED mode only: which kind the person is looking at on ranking / reports / certificates / students. */
    val kind: StateFlow<WorkMode?> = _kind.asStateFlow()
    fun setKind(kind: WorkMode?) { _kind.value = kind }

    /** [typeById] covers every halaqa (so inactive ones still classify students); [activeIds] decide the mode. */
    fun update(typeById: Map<String, String?>, activeIds: Collection<String>, selectedId: String?, nameById: Map<String, String> = emptyMap()) {
        val selType = selectedId?.let { typeById[it] }
        val mode = WorkModeRules.effective(selType, selectedId != null, activeIds.map { typeById[it] })
        val next = WorkModeState(mode, selectedId, typeById, activeIds.toList(), nameById)
        if (next != _state.value) _state.value = next
    }
}
