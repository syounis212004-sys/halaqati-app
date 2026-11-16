package com.mohammedsamir.halaqati.plans

/**
 * RC27 pure rules for the home "needs attention" card (admin / supervisor / teacher):
 * active students that no plan covers today, and active students with no linked guardian.
 */
object OrgAttentionRules {
    data class StudentInput(val id: String, val name: String, val status: String, val halaqaId: String?, val categoryId: String?)
    data class PlanInput(val scopeType: String, val studentId: String?, val categoryId: String?, val halaqaId: String?, val status: String, val startDate: String, val endDate: String)

    private val LIVE_STATUSES = setOf("active", "paused", "draft")

    private fun covers(plan: PlanInput, student: StudentInput, today: String): Boolean {
        if (plan.status !in LIVE_STATUSES) return false
        if (today < plan.startDate.take(10) || (plan.endDate.isNotBlank() && today > plan.endDate.take(10))) return false
        return when (plan.scopeType) {
            "category" -> plan.categoryId != null && plan.categoryId == student.categoryId
            "halaqa" -> plan.halaqaId != null && plan.halaqaId == student.halaqaId
            else -> plan.studentId == student.id
        }
    }

    fun studentsWithoutPlan(students: List<StudentInput>, plans: List<PlanInput>, today: String, halaqaId: String? = null): List<StudentInput> =
        students.filter { it.status == "active" && (halaqaId == null || it.halaqaId == halaqaId) }
            .filter { s -> plans.none { covers(it, s, today) } }

    fun studentsWithoutGuardian(students: List<StudentInput>, linkedStudentIds: Set<String>, halaqaId: String? = null): List<StudentInput> =
        students.filter { it.status == "active" && (halaqaId == null || it.halaqaId == halaqaId) && it.id !in linkedStudentIds }
}
