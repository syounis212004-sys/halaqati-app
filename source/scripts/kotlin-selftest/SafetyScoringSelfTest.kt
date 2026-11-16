import com.mohammedsamir.halaqati.data.SafetyScoring

fun main() {
    check(SafetyScoring.calculate(1.0, 0, 0) == 100.0)
    check(SafetyScoring.calculate(1.0, 0, 1) == 90.0)
    check(SafetyScoring.calculate(1.0, 0, 2) == 80.0)
    check(SafetyScoring.calculate(1.0, 1, 0) == 80.0)
    check(SafetyScoring.calculate(1.0, 1, 1) == 70.0)
    check(SafetyScoring.calculate(1.0, 0, 3) == 70.0)
    check(SafetyScoring.inferredEvaluation(SafetyScoring.calculate(1.0, 2, 0)) == "repeat")
    val seven = SafetyScoring.calculate(7.0/15.0, 0, 1, heardLines = 7.0)
    val eight = SafetyScoring.calculate(8.0/15.0, 0, 1, heardLines = 8.0)
    check(kotlin.math.abs(seven - 78.6) < 0.11) { seven }
    check(kotlin.math.abs(eight - 81.3) < 0.11) { eight }
    check(SafetyScoring.points(1.0, 0, 2) == 0.75)
    check(SafetyScoring.points(1.0, 1, 0) == 0.75)
    check(SafetyScoring.points(1.0, 1, 1) == 0.63) { SafetyScoring.points(1.0,1,1) }
    check(SafetyScoring.points(2.0, 1, 1) == 1.63)
    println("SafetyScoring self-tests passed")
}
