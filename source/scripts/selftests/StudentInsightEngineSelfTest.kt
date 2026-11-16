import com.mohammedsamir.halaqati.data.StudentInsightEngine

fun main() {
    val healthy = StudentInsightEngine.build(40.0, 37.0, 94.0, 2, 1, 15, 0, 0, 0)
    check(healthy.performanceLevel == StudentInsightEngine.PerformanceLevel.VERY_GOOD)
    check(healthy.followUpReasons.any { it.kind == StudentInsightEngine.FollowUpKind.PLAN_DELAY })

    val quality = StudentInsightEngine.build(40.0, 40.0, 70.0, 15, 5, 15, 0, 0, 0)
    check(quality.performanceLevel == StudentInsightEngine.PerformanceLevel.NEEDS_FOLLOW_UP)
    check(quality.followUpReasons.any { it.kind == StudentInsightEngine.FollowUpKind.QUALITY })
    println("StudentInsightEngineSelfTest PASS")
}
