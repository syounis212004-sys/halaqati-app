package com.mohammedsamir.halaqati.ui.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.ui.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuardianHomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(GuardianHomeUiState())
    val state: StateFlow<GuardianHomeUiState> = _state.asStateFlow()
    private val memory = HashMap<String, GuardianOverview>()
    private var childrenJob: Job? = null
    private var overviewJob: Job? = null

    fun start() = loadChildren(explicitRefresh = false)

    fun refresh() = loadChildren(explicitRefresh = true)

    private fun loadChildren(explicitRefresh: Boolean) {
        if (childrenJob?.isActive == true) return
        childrenJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = explicitRefresh, message = null) }
            val online = AppGraph.repo.online()
            val children = runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    AppGraph.repo.guardianChildren(forceRefresh = online || explicitRefresh, requireFresh = online || explicitRefresh)
                        .filter { it.status == "active" }
                        .map { GuardianChild(it.id, it.fullName, it.track == "tathbit") }
                }
            }.getOrElse {
                _state.update {
                    it.copy(
                        loadingFirst = false,
                        refreshing = false,
                        stale = true,
                        message = if (online) "تعذر تحديث قائمة الأبناء" else "تحديث قائمة الأبناء يحتاج اتصالًا بالإنترنت",
                    )
                }
                return@launch
            }
            val allowedIds = children.map { it.id }.toSet()
            memory.keys.retainAll(allowedIds)
            val previous = _state.value.selectedId
            val next = previous?.takeIf { it in allowedIds } ?: children.firstOrNull()?.id
            overviewJob?.cancel()
            _state.update {
                it.copy(
                    children = children,
                    selectedId = next,
                    overview = if (next == previous) it.overview else next?.let(memory::get),
                    loadingFirst = next != null && it.loadingFirst,
                    refreshing = false,
                    message = if (!online && children.isEmpty()) "اتصل بالإنترنت للتحقق من ارتباط الأبناء" else null,
                )
            }
            if (next != null) select(next)
        }
    }

    fun select(studentId: String) {
        if (_state.value.children.none { it.id == studentId }) return
        overviewJob?.cancel()
        val cached = memory[studentId]
        _state.update {
            it.copy(
                selectedId = studentId,
                overview = cached,
                refreshing = true,
                message = null,
            )
        }
        overviewJob = viewModelScope.launch {
            if (cached == null) {
                val disk = withContext(Dispatchers.IO) {
                    AppGraph.repo.cachedGuardianChildOverview(studentId)?.let { GuardianOverview.parse(it) }
                }
                if (disk != null) {
                    memory[studentId] = disk
                    _state.update { it.copy(overview = disk, loadingFirst = false, stale = true) }
                }
            }
            val online = AppGraph.repo.online()
            val fresh = runCatchingCancellable {
                withContext(Dispatchers.IO) { AppGraph.repo.guardianChildOverview(studentId)?.let { GuardianOverview.parse(it) } }
            }.getOrNull()
            if (fresh != null) memory[studentId] = fresh
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
