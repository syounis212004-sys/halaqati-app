package com.mohammedsamir.halaqati.data

import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules
import org.json.JSONObject

/** RC26: one student's monthly hifz plan whose stored range no longer matches reality. */
data class PlanRebasePreview(
    val planId: String,
    val studentId: String,
    val studentName: String,
    val halaqaId: String,
    val status: PlanRangeRebaseRules.Status,
    val oldLabel: String,
    val newLabel: String,
    val latestLabel: String,
    /** PATCH body for smart_plans, or null when no corrected range could be computed. */
    val patch: JSONObject?,
) {
    val canApply: Boolean get() = patch != null
}
