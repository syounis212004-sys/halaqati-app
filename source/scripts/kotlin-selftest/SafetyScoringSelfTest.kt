import com.mohammedsamir.halaqati.data.SafetyScoring

private fun near(a: Double, b: Double) = kotlin.math.abs(a - b) < 0.0001

// V7 (the circle's rule): at most one mistake (a phrase) and two prompts per page; over that is always "repeat".
fun main() {
    check(near(SafetyScoring.calculate(1.0, 0, 0), 100.0))
    check(near(SafetyScoring.calculate(1.0, 0, 1), 95.0))
    check(near(SafetyScoring.calculate(1.0, 0, 2), 90.0))
    check(near(SafetyScoring.calculate(1.0, 1, 0), 80.0))
    check(near(SafetyScoring.calculate(1.0, 1, 1), 75.0))
    check(near(SafetyScoring.calculate(1.0, 1, 2), 70.0))
    check(SafetyScoring.calculate(1.0, 0, 3) <= 69.9)
    check(SafetyScoring.inferredEvaluation(SafetyScoring.calculate(1.0, 2, 0)) == "repeat")
    check(SafetyScoring.inferredEvaluation(SafetyScoring.calculate(1.0, 0, 3)) == "repeat")
    // a page the mushaf marked as over its own limit is "repeat" even when the totals are within the allowance
    check(SafetyScoring.calculate(5.0, 1, 2) >= 90.0)
    check(SafetyScoring.calculate(5.0, 1, 2, overLimitPage = true) <= 69.9)
    val seven = SafetyScoring.calculate(7.0 / 15.0, 0, 1, heardLines = 7.0)
    val eight = SafetyScoring.calculate(8.0 / 15.0, 0, 1, heardLines = 8.0)
    check(near(seven, 89.3)) { seven }
    check(near(eight, 90.6)) { eight }
    check(near(SafetyScoring.points(1.0, 0, 2), 0.88))
    check(near(SafetyScoring.points(1.0, 1, 0), 0.75))
    check(near(SafetyScoring.points(1.0, 1, 1), 0.69)) { SafetyScoring.points(1.0, 1, 1) }
    check(near(SafetyScoring.points(2.0, 1, 1), 1.69))
    println("SafetyScoring self-tests passed")
}
