package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.OfflineQueue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** RC53: data layer shared by the supervisor / administrator staff-monitoring screens. */

/** A text field that is empty when the JSON value is missing or null (optString would return the word "null"). */
internal fun JSONObject.supStr(key: String): String = if (!has(key) || isNull(key)) "" else optString(key).let { if (it == "null") "" else it }

internal fun JSONArray.sRows(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/**
 * Supervisor visits stay usable without internet: a visit saved offline sits in the outbox until it uploads.
 * This overlay lays the not-yet-uploaded creations / edits / deletions over the last server copy, and marks pending rows
 * with `_pending = true` so the list can show "بانتظار الرفع".
 */
object VisitOverlay {
    private val idInPath = Regex("(?:^|[?&])id=eq\\.([^&]+)")

    fun apply(server: List<JSONObject>, queue: List<OfflineQueue.SnapshotItem>): List<JSONObject> {
        val rows = LinkedHashMap<String, JSONObject>()
        server.forEach { rows[it.optString("id")] = JSONObject(it.toString()) }
        val pending = HashSet<String>()
        queue.filter { it.path.startsWith("/rest/v1/supervisor_visits") }.sortedBy { it.created }.forEach { q ->
            val pathId = idInPath.find(q.path)?.groupValues?.getOrNull(1)
            when (q.method) {
                "POST" -> {
                    val body = runCatching { JSONObject(q.body ?: "") }.getOrNull()
                    val id = body?.optString("id").orEmpty()
                    if (body != null && id.isNotBlank()) {
                        val merged = rows[id] ?: JSONObject()
                        body.keys().forEach { k -> merged.put(k, body.opt(k)) }
                        rows[id] = merged
                        pending.add(id)
                    }
                }
                "PATCH" -> {
                    val body = runCatching { JSONObject(q.body ?: "") }.getOrNull()
                    val target = if (pathId != null) rows[pathId] else null
                    if (body != null && target != null && pathId != null) {
                        body.keys().forEach { k -> target.put(k, body.opt(k)) }
                        pending.add(pathId)
                    }
                }
                "DELETE" -> if (pathId != null) {
                    rows.remove(pathId)
                    pending.remove(pathId)
                }
            }
        }
        pending.forEach { id -> rows[id]?.put("_pending", true) }
        return rows.values.sortedWith(
            compareByDescending<JSONObject> { it.optString("visit_date") }.thenByDescending { it.optString("created_at") },
        )
    }
}

enum class TeacherDayState { NOT_STARTED, IN_PROGRESS, CLOSED }

data class SupHalaqa(
    val id: String,
    val name: String,
    val teacherIds: List<String>,
    val teacherNames: List<String>,
    /** RC63: saved photo (data URL) of each teacher, same order as [teacherNames]; null when none. */
    val teacherPhotos: List<String?> = emptyList(),
    val students: Int,
    val state: TeacherDayState,
    val present: Int,
    val absent: Int,
    val excused: Int,
    val recited: Int,
    val monthSessions: Int,
    val lastVisitDate: String?,
    val lastVisitScore: Double?,
    /** Nour: a programme (not a regular halaqa); counted in the supervisor's figures like a halaqa. */
    val isProgram: Boolean = false,
    /** Nour: the halaqa logo (data URL) when one is saved. */
    val logo: String? = null,
) {
    val stateLabel: String get() = when (state) {
        TeacherDayState.NOT_STARTED -> "بانتظار الجلسة"
        TeacherDayState.IN_PROGRESS -> "الجلسة جارية"
        TeacherDayState.CLOSED -> "أنهى الجلسة"
    }
    val teacherLine: String get() = teacherNames.joinToString("، ").ifBlank { "بلا محفّظ" }
    val recitedPercent: Int get() = if (students <= 0) 0 else ((recited * 100.0) / students).toInt().coerceIn(0, 100)
}

/**
 * Nour: one teacher's composite evaluation. Five indicators, each 0..1 or null when there is nothing to measure yet.
 * Weights (owner-approved): commitment 30, students' attendance 20, recitation coverage 25, ended sessions 10, supervisor visits 15.
 * An indicator without data is left out and the remaining weights are rescaled.
 */
data class TeacherScore(
    val commitment: Double?,
    val studentAttendance: Double?,
    val recitationCoverage: Double?,
    val endedSessions: Double?,
    val visits: Double?,
) {
    val composite: Int? get() {
        val parts = listOf(commitment to 30.0, studentAttendance to 20.0, recitationCoverage to 25.0, endedSessions to 10.0, visits to 15.0)
            .filter { it.first != null }
        if (parts.isEmpty()) return null
        val w = parts.sumOf { it.second }
        return ((parts.sumOf { (it.first ?: 0.0) * it.second } / w) * 100).toInt().coerceIn(0, 100)
    }

    companion object {
        fun band(v: Int?): Pair<String, String> = when {
            v == null -> "لا بيانات" to "neutral"
            v >= 80 -> "أداء ممتاز" to "success"
            v >= 65 -> "جيد" to "success"
            v >= 50 -> "يحتاج تحسينًا" to "warning"
            else -> "يحتاج متابعة" to "danger"
        }
    }
}

data class SupBoard(
    /** Nour: today is a day off for these halaqat (no session today and none on earlier same weekdays this month), so "not started" must not alarm. */
    val offDay: Boolean = false,
    val halaqas: List<SupHalaqa>,
    val visits: List<JSONObject>,
    val staff: List<JSONObject>,
    /** Per teacher: sessions of the teacher's own halaqat (plus any session the teacher created). */
    val sessionsByCreator: Map<String, Int>,
    val daysByCreator: Map<String, Int>,
    val closedByCreator: Map<String, Int>,
    val lastSessionByCreator: Map<String, String>,
    /** Number of distinct days this month on which any session was held (the yardstick for a teacher's commitment). */
    val monthDays: Int,
    val programs: List<SupHalaqa> = emptyList(),
    val scores: Map<String, TeacherScore> = emptyMap(),
    val recitationsByTeacher: Map<String, Int> = emptyMap(),
)

internal fun staffProfiles(): List<JSONObject> {
    val repo = AppGraph.repo
    // RC61: the list itself (names, roles) is always read from the server when online, so a newly approved staff member shows up at once.
    val fresh = repo.online()
    val people = repo.table("profiles", "select=id,full_name,role,phone,active,updated_at&role=in.(teacher,supervisor)&order=full_name.asc&limit=500", forceRefresh = fresh).sRows()
    // RC79: a person's photo is downloaded ONCE and kept on the device (owner-scoped Room cache). It is downloaded again only when that
    // person's row changed on the server (updated_at differs from the stamp saved with the stored photo).
    val owner = repo.currentUserId() ?: return people
    val cache = AppGraph.cache
    fun stored(id: String): String? = cache.get(owner, "staff-photo:$id")?.takeIf { it.isNotBlank() && it != "-" }
    fun stamp(id: String): String? = cache.get(owner, "staff-photo-stamp:$id")
    if (fresh) {
        val need = people.filter { row -> val id = row.optString("id"); stamp(id) != row.optString("updated_at") }
        need.map { it.optString("id") }.filter { it.isNotBlank() }.chunked(40).forEach { ids ->
            runCatching {
                repo.api.select("profiles", "select=id,avatar_path&id=in.(${ids.joinToString(",")})")
            }.onSuccess { arr ->
                val got = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.associate { it.optString("id") to it.optString("avatar_path") }
                ids.forEach { id ->
                    val photo = got[id]?.takeIf { it.isNotBlank() && it != "null" }
                    cache.put(owner, "staff-photo:$id", photo ?: "-")
                    cache.put(owner, "staff-photo-stamp:$id", people.firstOrNull { it.optString("id") == id }?.optString("updated_at").orEmpty().ifBlank { "-" })
                }
            }
        }
    }
    return people.map { row -> row.also { r -> stored(r.optString("id"))?.let { r.put("avatar_path", it) } } }
}

/** Short-lived shared copy so the five supervisor screens do not each repeat ten network calls. */
private object SupBoardStore {
    @Volatile var board: SupBoard? = null
    @Volatile var key: String = ""
    @Volatile var at: Long = 0L
}

/** The server cuts any answer at 1000 rows, so id lists are fetched in small batches. */
private fun fetchBatched(table: String, select: String, column: String, ids: List<String>): List<JSONObject> {
    val repo = AppGraph.repo
    return ids.chunked(15).flatMap { chunk ->
        repo.table(table, "select=$select&$column=in.(${chunk.joinToString(",")})&limit=1000").sRows()
    }
}

/** Reads everything the supervisor home needs from the local / server tables (cached tables; works offline). */
internal fun loadSupBoard(supervisorOnly: Boolean = false): SupBoard {
    val repo = AppGraph.repo
    val cacheKey = "$supervisorOnly:${repo.localMutationEpoch.value}:${AppGraph.connectivity.syncEpoch.value}:${LocalDate.now()}"
    SupBoardStore.board?.let { b ->
        if (SupBoardStore.key == cacheKey && System.currentTimeMillis() - SupBoardStore.at < 30_000L) return b
    }
    val board = buildSupBoard(supervisorOnly)
    SupBoardStore.board = board; SupBoardStore.key = cacheKey; SupBoardStore.at = System.currentTimeMillis()
    return board
}

private fun buildSupBoard(supervisorOnly: Boolean): SupBoard {
    val repo = AppGraph.repo
    val now = LocalDate.now()
    val today = now.toString()
    val monthStart = now.withDayOfMonth(1).toString()
    val online = repo.online()
    // RC84: the halaqa list and its teachers are read fresh when online (a deleted halaqa must disappear at once).
    val allActive = repo.table("halaqas", "select=id,name,active,halaqa_type,supervisor_id&active=eq.true&order=name.asc&limit=200", forceRefresh = online).sRows()
    fun isProgramRow(r: JSONObject) = r.optString("halaqa_type", "regular").let { t -> !(t.isBlank() || t == "regular" || t == "null") }
    // A halaqa supervisor sees only the halaqat assigned to him: either through halaqa_supervisors or halaqas.supervisor_id.
    val myId = repo.currentUserId().orEmpty()
    val mine: Set<String>? = if (!supervisorOnly) null else {
        val viaLinks = repo.table("halaqa_supervisors", "select=halaqa_id,supervisor_id&supervisor_id=eq.$myId&limit=200", forceRefresh = online)
            .sRows().map { it.optString("halaqa_id") }
        val viaColumn = allActive.filter { it.optString("supervisor_id") == myId }.map { it.optString("id") }
        (viaLinks + viaColumn).toSet()
    }
    val scoped = if (mine == null) allActive else allActive.filter { it.optString("id") in mine }
    val regularRows = scoped.filterNot(::isProgramRow)
    val programRows = scoped.filter(::isProgramRow)
    val scopedIds = scoped.map { it.optString("id") }
    val links = repo.table("halaqa_teachers", "select=halaqa_id,teacher_id&limit=1000", forceRefresh = online).sRows()
    val myTeacherIds = if (mine == null) null else links.filter { it.optString("halaqa_id") in scopedIds }.map { it.optString("teacher_id") }.toSet()
    val staff = staffProfiles().let { all -> if (myTeacherIds == null) all else all.filter { it.optString("id") in myTeacherIds } }
    val staffById = staff.associateBy { it.optString("id") }
    // logos are fetched only for the halaqat on screen, and only if the column exists in this database
    val logos: Map<String, String> = if (scopedIds.isEmpty()) emptyMap() else runCatching {
        repo.table("halaqas", "select=id,logo_path&id=in.(${scopedIds.joinToString(",")})&limit=200").sRows()
            .mapNotNull { r -> r.supStr("logo_path").takeIf { it.startsWith("data:image/") }?.let { r.optString("id") to it } }.toMap()
    }.getOrDefault(emptyMap())
    val programMembers: Map<String, Int> = if (programRows.isEmpty()) emptyMap() else runCatching {
        repo.table("student_halaqa_memberships", "select=halaqa_id,student_id&limit=1000").sRows()
            .groupBy { it.optString("halaqa_id") }.mapValues { e -> e.value.map { it.optString("student_id") }.distinct().size }
    }.getOrDefault(emptyMap())

    val sessionsQuery = if (mine != null && scopedIds.isNotEmpty())
        "select=id,halaqa_id,session_date,status,created_by&session_date=gte.$monthStart&halaqa_id=in.(${scopedIds.joinToString(",")})&order=session_date.desc&limit=1000"
    else "select=id,halaqa_id,session_date,status,created_by&session_date=gte.$monthStart&order=session_date.desc&limit=1000"
    val sessions = (if (mine != null && scopedIds.isEmpty()) emptyList() else repo.table("sessions", sessionsQuery).sRows())
        .filter { it.optString("status") != "cancelled" }
        .distinctBy { it.optString("id") }
    val todaySessions = sessions.filter { it.optString("session_date") == today }
    // month attendance / recitation, batched; for the administrator on a very large month the per-teacher indicators are skipped
    val withIndicators = sessions.size <= 150
    val monthIds = if (withIndicators) sessions.map { it.optString("id") }.filter { it.isNotBlank() } else todaySessions.map { it.optString("id") }.filter { it.isNotBlank() }
    val attendance = if (monthIds.isEmpty()) emptyList() else fetchBatched("attendance_records", "session_id,student_id,status", "session_id", monthIds)
    val recitations = if (monthIds.isEmpty()) emptyList() else fetchBatched("recitation_entries", "session_id,student_id", "session_id", monthIds)
    // RC84: the student list is refreshed from the server once per cycle (a stale copy showed 14 students instead of 44).
    val studentsDue = online && com.mohammedsamir.halaqati.data.RefreshGate.due("sup-students")
    val students = repo.students(forceRefresh = studentsDue).filter { it.status == "active" }
    if (studentsDue) com.mohammedsamir.halaqati.data.RefreshGate.mark("sup-students")
    val visits = VisitOverlay.apply(
        repo.table("supervisor_visits", "select=*&order=visit_date.desc&limit=300", forceRefresh = repo.online()).sRows(),
        repo.queueSnapshot(500),
    ).let { all -> if (mine == null) all else all.filter { it.optString("halaqa_id") in scopedIds || it.optString("supervisor_id") == myId } }

    fun sessionEnded(r: JSONObject) = r.optString("status") == "closed" || r.optString("session_date") < today
    val sessionHalaqa = sessions.associate { it.optString("id") to it.optString("halaqa_id") }

    fun rowToHalaqa(h: JSONObject, program: Boolean): SupHalaqa {
        val hid = h.optString("id")
        val teacherIds = links.filter { it.optString("halaqa_id") == hid }.map { it.optString("teacher_id") }.distinct()
        val todayOfHalaqa = todaySessions.filter { it.optString("halaqa_id") == hid }
        val state = when {
            todayOfHalaqa.isEmpty() -> TeacherDayState.NOT_STARTED
            todayOfHalaqa.all { it.optString("status") == "closed" } -> TeacherDayState.CLOSED
            else -> TeacherDayState.IN_PROGRESS
        }
        val ids = todayOfHalaqa.map { it.optString("id") }.toSet()
        val att = attendance.filter { it.optString("session_id") in ids }
        val lastVisit = visits.firstOrNull { it.optString("halaqa_id") == hid }
        val score = lastVisit?.let(::visitAverage)
        return SupHalaqa(
            id = hid,
            name = h.optString("name", "حلقة"),
            teacherIds = teacherIds,
            teacherNames = teacherIds.map { id -> staffById[id]?.optString("full_name")?.takeIf { n -> n.isNotBlank() } ?: "محفّظ (لم يُحمَّل الاسم)" },
            teacherPhotos = teacherIds.map { id -> staffById[id]?.optString("avatar_path")?.takeIf { p -> p.isNotBlank() && p != "null" } },
            students = if (program) (programMembers[hid] ?: 0) else students.count { it.halaqaId == hid },
            state = state,
            present = att.count { it.optString("status") == "present" || it.optString("status") == "late" },
            absent = att.count { it.optString("status") == "absent" },
            excused = att.count { it.optString("status") == "excused" },
            recited = recitations.filter { sessionHalaqa[it.optString("session_id")] == hid && it.optString("session_id") in ids }.map { it.optString("student_id") }.distinct().size,
            monthSessions = sessions.count { it.optString("halaqa_id") == hid },
            lastVisitDate = lastVisit?.optString("visit_date")?.takeIf { it.isNotBlank() },
            lastVisitScore = score,
            isProgram = program,
            logo = logos[hid],
        )
    }
    val halaqas = regularRows.map { rowToHalaqa(it, false) }
    val programs = programRows.map { rowToHalaqa(it, true) }
    val every = halaqas + programs

    // ---- Nour: per-teacher figures from the teacher's halaqat (a session counts for the teacher even if someone else created it) ----
    val scopeSessions = if (mine == null) sessions else sessions.filter { it.optString("halaqa_id") in scopedIds }
    val monthDays = scopeSessions.map { it.optString("session_date") }.distinct().size
    val sessionsOf = HashMap<String, List<JSONObject>>()
    val scores = HashMap<String, TeacherScore>()
    val recitationsOf = HashMap<String, Int>()
    for (st in staff) {
        val tid = st.optString("id")
        if (st.optString("role") != "teacher") continue
        val tHalaqas = every.filter { tid in it.teacherIds }
        val tIds = tHalaqas.map { it.id }.toSet()
        val tSessions = scopeSessions.filter { it.optString("halaqa_id") in tIds || it.optString("created_by") == tid }.distinctBy { it.optString("id") }
        sessionsOf[tid] = tSessions
        val tSessionIds = tSessions.map { it.optString("id") }.toSet()
        val att = attendance.filter { it.optString("session_id") in tSessionIds }
        val present = att.count { it.optString("status") == "present" || it.optString("status") == "late" }
        val absent = att.count { it.optString("status") == "absent" }
        val recitedStudents = recitations.filter { it.optString("session_id") in tSessionIds }.map { it.optString("student_id") }.distinct().size
        recitationsOf[tid] = recitations.count { it.optString("session_id") in tSessionIds }
        val roster = tHalaqas.sumOf { it.students }
        val receivedVisits = visits.filter { it.optString("halaqa_id") in tIds && it.optString("visit_date") >= monthStart }.mapNotNull { visitAverage(it) }
        scores[tid] = TeacherScore(
            commitment = if (monthDays > 0 && tHalaqas.isNotEmpty()) (tSessions.map { it.optString("session_date") }.distinct().size.toDouble() / monthDays).coerceIn(0.0, 1.0) else null,
            studentAttendance = if (withIndicators && present + absent > 0) present.toDouble() / (present + absent) else null,
            recitationCoverage = if (withIndicators && roster > 0 && tSessions.isNotEmpty()) (recitedStudents.toDouble() / roster).coerceIn(0.0, 1.0) else null,
            endedSessions = if (tSessions.isNotEmpty()) tSessions.count(::sessionEnded).toDouble() / tSessions.size else null,
            visits = if (receivedVisits.isNotEmpty()) (receivedVisits.average() / 5.0).coerceIn(0.0, 1.0) else null,
        )
    }
    // for people who created sessions but are not in scope as teachers (e.g. the administrator), keep the old attribution
    val byCreator = scopeSessions.groupBy { it.optString("created_by") }
    fun <T> perPerson(f: (List<JSONObject>) -> T): Map<String, T> =
        (byCreator.keys + sessionsOf.keys).associateWith { id -> f(sessionsOf[id] ?: byCreator[id].orEmpty()) }

    // day off: no session today in these halaqat, none on earlier same weekdays this month, but sessions exist on other days
    val todayDate = java.time.LocalDate.parse(today)
    val sameWeekdayHadSessions = scopeSessions.any { r ->
        runCatching { java.time.LocalDate.parse(r.optString("session_date")) }.getOrNull()?.let { d -> d.dayOfWeek == todayDate.dayOfWeek } == true
    }
    val offDay = todaySessions.none { it.optString("halaqa_id") in scopedIds || mine == null } && !sameWeekdayHadSessions &&
        generateSequence(todayDate.minusWeeks(1)) { it.minusWeeks(1) }.takeWhile { !it.isBefore(java.time.LocalDate.parse(monthStart)) }.any() &&
        scopeSessions.isNotEmpty()
    return SupBoard(
        offDay = offDay,
        halaqas = halaqas,
        visits = visits,
        staff = staff,
        sessionsByCreator = perPerson { it.size },
        daysByCreator = perPerson { l -> l.map { it.optString("session_date") }.distinct().size },
        closedByCreator = perPerson { l -> l.count(::sessionEnded) },
        lastSessionByCreator = perPerson { l -> l.maxOfOrNull { it.optString("session_date") }.orEmpty() }.filterValues { it.isNotBlank() },
        monthDays = monthDays,
        programs = programs,
        scores = scores,
        recitationsByTeacher = recitationsOf,
    )
}

internal fun visitAverage(v: JSONObject): Double? {
    val xs = listOf("organization_score", "teaching_score", "followup_score").filter { !v.isNull(it) && v.has(it) }.map { v.optInt(it).toDouble() }
    return if (xs.isEmpty()) null else xs.average()
}

internal fun oneDecimal(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)
