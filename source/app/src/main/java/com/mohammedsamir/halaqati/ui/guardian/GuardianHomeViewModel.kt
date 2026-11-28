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
    private var childrenJob: Job? = null
    private var overviewJob: Job? = null
    private val selectedActivities = HashMap<String, String>()

    fun start() = loadChildren(false)
    fun refresh() = loadChildren(true)

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
                overviewJob?.cancel()
                _state.update { it.copy(overview = null, loadingFirst = false, refreshing = false,
                    stale = true, message = "تعذر التحقق من قائمة الأبناء") }
                return@launch
            }
            val allowed = children.map { it.id }.toSet()
            selectedActivities.keys.retainAll(allowed)
            val next = _state.value.selectedId?.takeIf { it in allowed } ?: children.firstOrNull()?.id
            overviewJob?.cancel()
            _state.update { it.copy(children = children, selectedId = next, overview = null,
                activities = emptyList(), selectedActivity = null, loadingFirst = next != null,
                refreshing = false, message = null) }
            if (next != null) select(next)
        }
    }

    fun select(studentId: String) {
        if (_state.value.children.none { it.id == studentId }) return
        overviewJob?.cancel()
        _state.update { it.copy(selectedId = studentId, selectedActivity = null, activities = emptyList(),
            overview = null, refreshing = true, message = null) }
        overviewJob = viewModelScope.launch {
            val activities = runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val rows = AppGraph.repo.guardianActivities(studentId)
                    (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                        .map { GuardianActivity(it.optString("id"),it.optString("name"),it.optBoolean("primary")) }
                        .filter { it.id.isNotBlank() }
                }
            }.getOrElse {
                _state.update { it.copy(overview = null, loadingFirst = false, refreshing = false,
                    message = "تعذر تحميل عضويات الطالب. حدّث الصفحة أو تحقق من الخادم.") }
                return@launch
            }
            val next = selectedActivities[studentId]?.takeIf { id -> activities.any { it.id == id } }
                ?: activities.firstOrNull { it.primary }?.id ?: activities.singleOrNull()?.id
            _state.update { it.copy(activities = activities, selectedActivity = next, loadingFirst = false, refreshing = false) }
            if (next != null) loadOverview(studentId,next)
        }
    }

    fun selectActivity(id: String) {
        val studentId = _state.value.selectedId ?: return
        if (_state.value.activities.none { it.id == id }) return
        overviewJob?.cancel()
        overviewJob = viewModelScope.launch { loadOverview(studentId,id) }
    }

    private suspend fun loadOverview(studentId: String, halaqaId: String) {
        selectedActivities[studentId] = halaqaId
        val online = AppGraph.repo.online()
        _state.update { it.copy(selectedActivity = halaqaId, overview = null, refreshing = true, message = null) }
        val fresh = runCatchingCancellable {
            withContext(Dispatchers.IO) { AppGraph.repo.guardianActivityOverview(studentId,halaqaId)?.let { GuardianOverview.parse(it) } }
        }.getOrNull()
        if (_state.value.selectedId != studentId || _state.value.selectedActivity != halaqaId) return
        _state.update { it.copy(overview = fresh, loadingFirst = false, refreshing = false,
            stale = !online, message = if (fresh == null) "تعذر تحميل بيانات النشاط" else null) }
    }
}
