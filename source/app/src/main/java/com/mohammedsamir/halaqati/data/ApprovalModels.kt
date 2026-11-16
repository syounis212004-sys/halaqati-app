package com.mohammedsamir.halaqati.data

/** RC27.42: one row of the approval inbox returned by rc_pending_requests (account request or guardian link request). */
data class ApprovalRequest(
    val kind: String,
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val student: String,
    val halaqa: String,
    val stage: String,
    val teacherName: String,
    val createdAt: String,
)
