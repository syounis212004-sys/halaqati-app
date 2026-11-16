package com.mohammedsamir.halaqati.ui.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohammedsamir.halaqati.data.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RC17 guardian home: stale-while-revalidate.
 * Children are loaded once; switching child shows its cached overview instantly (memory, then disk)
 * and refreshes only that child with ONE RPC. The screen never drops to a skeleton when data exists.
 */
class GuardianHomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(GuardianHomeUiState())
    val state: StateFlow<GuardianHomeUiState> = _state.asStateFlow()
    private val memory = HashMap<String, GuardianOverview>()
    private var childrenLoaded = false
    private var refreshJob: Job? = null

    fun start() {
        if (childrenLoaded) return
        childrenLoaded = true
        viewModelScope.launch {
            val children = com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    AppGraph.repo.guardianChildren().filter { it.status == "active" }
                        .map { GuardianChild(it.id, it.fullName, it.track == "tathbit") }
                        .sortedBy { it.name }
                }
            }.getOrElse { e ->
                _state.update { it.copy(loadingFirst = false, message = e.message ?: "تعذر تحميل الأبناء") }
                childrenLoaded = false
                return@launch
            }
            _state.update { it.copy(children = children) }
            val first = children.firstOrNull()?.id
            if (first == null) _state.update { it.copy(loadingFirst = false) } else select(first)
        }
    }

    fun select(studentId: String) {
        val cachedInMemory = memory[studentId]
        _state.update { it.copy(selectedId = studentId, overview = cachedInMemory, message = null) }
        refreshJob?.cancel()
        viewModelScope.launch {
            if (cachedInMemory == null) {
                val disk = withContext(Dispatchers.IO) {
                    AppGraph.repo.cachedGuardianChildOverview(studentId)?.let { GuardianOverview.parse(it) }
                }
                if (disk != null && _state.value.selectedId == studentId && _state.value.overview == null) {
                    memory[studentId] = disk
                    _state.update { it.copy(overview = disk, loadingFirst = false, stale = true) }
                }
            }
        }
        refresh(studentId)
    }

    fun refresh(studentId: String? = _state.value.selectedId) {
        val id = studentId ?: return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val online = AppGraph.repo.online()
            val fresh = com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) { AppGraph.repo.guardianChildOverview(id)?.let { GuardianOverview.parse(it) } }
            }.getOrNull()
            if (_state.value.selectedId != id) return@launch
            if (fresh != null) memory[id] = fresh
            _state.update {
                it.copy(
                    overview = fresh ?: it.overview,
                    loadingFirst = false,
                    refreshing = false,
                    stale = !online || fresh == null,
                    message = if (fresh == null && it.overview == null && online) "تعذر تحميل بيانات الطالب" else null,
                )
            }
        }
    }
}
