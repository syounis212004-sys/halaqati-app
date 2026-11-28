package com.mohammedsamir.halaqati.ui.sessions

object EducationScoreRules {
    const val GENERAL = "general"
    val detailed = listOf("commitment" to "الالتزام", "listening" to "الاستماع والانتباه",
        "participation" to "المشاركة والتفاعل", "behavior" to "السلوك", "comprehension" to "الاستيعاب")
    val keys = setOf(GENERAL) + detailed.map { it.first }
    fun valid(metric: String, score: Int) = metric in keys && score in 1..5
    fun eligible(attendance: String?) = attendance in setOf("present", "late")
    fun label(score: Int?): String = when (score) {
        1 -> "يحتاج متابعة"
        2 -> "مقبول"
        3 -> "جيد"
        4 -> "جيد جدًا"
        5 -> "ممتاز"
        else -> "غير مقيّم"
    }
    fun bulkCandidates(attendance: Map<String, String>, scores: Map<String, Map<String, Int>>): Set<String> =
        attendance.filter { (id, state) -> eligible(state) && scores[id]?.containsKey(GENERAL) != true }.keys
}
