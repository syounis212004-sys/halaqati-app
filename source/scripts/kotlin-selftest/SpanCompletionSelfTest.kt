import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import java.time.LocalDate

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

private fun rec(date: String, ss: Int, sa: Int, es: Int, ea: Int, eval: String = "excellent") =
    SmartPlanEngine.Recitation(
        id = "$date-$es-$ea", studentId = "s", activityType = "new_hifz", date = LocalDate.parse(date),
        startSurah = ss, startAyah = sa, endSurah = es, endAyah = ea, evaluation = eval, createdAt = date,
    )

fun main() {
    val rev = "reverse_surahs_forward_ayahs"
    val plan = SmartPlanEngine.Plan(
        id = "p", halaqaId = "h", studentId = "s", planType = "hifz", targetPages = 30.0,
        startDate = LocalDate.parse("2026-10-01"), endDate = LocalDate.parse("2026-10-31"),
        settings = SmartPlanEngine.Settings(routeDirection = rev),
    )
    val recs = listOf(
        rec("2026-10-04", 33, 51, 33, 73),
        rec("2026-10-05", 32, 1, 31, 29),
        rec("2026-10-06", 33, 60, 33, 65),                 // goes back over old ground: adds nothing
        rec("2026-10-07", 30, 42, 29, 16, eval = "repeat"), // failed: does not move the frontier
    )
    val today = LocalDate.parse("2026-10-14")

    // No counter installed (pure tests / older path): the old counting is used, no crash.
    SmartPlanEngine.spanLineCounter = null
    val old = SmartPlanEngine.calculateProgress(plan, "s", recs, emptyList(), today)

    // Fake counter: one "line" per ayah slot along the route.
    SmartPlanEngine.spanLineCounter = { s, e, r ->
        (PlanRangeRebaseRules.position(e, r) - PlanRangeRebaseRules.position(s, r) + 1).toInt()
    }
    val span = SmartPlanEngine.calculateProgress(plan, "s", recs, emptyList(), today)
    val expected = SmartPlanEngine.round(1979 / 15.0)   // 33:51 .. 31:29
    check(span.completed == expected, "span completed ${span.completed} expected $expected")
    check(span.completed > old.completed, "span counts the whole stretch, old counted ${old.completed}")

    // Failed recitations alone count nothing.
    val onlyFailed = SmartPlanEngine.calculateProgress(plan, "s", listOf(rec("2026-10-07", 30, 42, 29, 16, eval = "repeat")), emptyList(), today)
    check(onlyFailed.completed == 0.0, "failed only -> 0")

    // Counter failing (null) falls back to the old counting.
    SmartPlanEngine.spanLineCounter = { _, _, _ -> null }
    val fallback = SmartPlanEngine.calculateProgress(plan, "s", recs, emptyList(), today)
    check(fallback.completed == old.completed, "counter null -> old counting")

    // Only hifz plans are affected.
    SmartPlanEngine.spanLineCounter = { s, e, r -> (PlanRangeRebaseRules.position(e, r) - PlanRangeRebaseRules.position(s, r) + 1).toInt() }
    val sardPlan = plan.copy(planType = "sard")
    val sard = SmartPlanEngine.calculateProgress(sardPlan, "s", recs, emptyList(), today)
    check(sard.completed == 0.0, "non-hifz plan keeps its own counting")
    // RC27.10: the incremental (line-key) path gives the same completed amount and daily amounts as the counter path.
    SmartPlanEngine.spanLineCounter = { s, e, r -> (PlanRangeRebaseRules.position(e, r) - PlanRangeRebaseRules.position(s, r) + 1).toInt() }
    val viaCounter = SmartPlanEngine.calculateProgress(plan, "s", recs, emptyList(), today)
    SmartPlanEngine.spanLineKeyProvider = { s, e, r ->
        (PlanRangeRebaseRules.position(s, r)..PlanRangeRebaseRules.position(e, r)).map { it.toString() }
    }
    val viaKeys = SmartPlanEngine.calculateProgress(plan, "s", recs, emptyList(), today)
    check(viaKeys.completed == viaCounter.completed, "incremental ${viaKeys.completed} vs ${viaCounter.completed}")
    SmartPlanEngine.spanLineKeyProvider = { _, _, _ -> null }
    val viaNullKeys = SmartPlanEngine.calculateProgress(plan, "s", recs, emptyList(), today)
    check(viaNullKeys.completed == viaCounter.completed, "key provider null falls back to the counter")
    SmartPlanEngine.spanLineKeyProvider = null
    println("SpanCompletionSelfTest PASS")
}
