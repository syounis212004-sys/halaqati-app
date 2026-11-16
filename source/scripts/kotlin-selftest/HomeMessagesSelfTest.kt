import com.mohammedsamir.halaqati.ui.dashboard.HomeMessages

fun main() {
    check(HomeMessages.displayName("Dr. Mohammed Ali") == "Dr. Mohammed")
    check(HomeMessages.displayName("محمد سمير أبو يونس") == "محمد")
    check(HomeMessages.displayName("الشيخ أحمد") == "الشيخ أحمد")
    check(HomeMessages.displayName("Dr.") == "بك")
    check(HomeMessages.displayName("") == "بك")
    check(HomeMessages.state(false, false, false, 0, 0) == HomeMessages.State.LOADING)
    check(HomeMessages.state(true, true, false, 0, 0) == HomeMessages.State.OFF_DAY)
    check(HomeMessages.state(true, false, true, 0, 0) == HomeMessages.State.WAITING)
    check(HomeMessages.state(true, false, false, 5, 0) == HomeMessages.State.DONE)
    check(HomeMessages.state(true, false, false, 5, 2) == HomeMessages.State.ACTIVE)
    // every role/state returns something for any seed (including negative) and rotates across days
    for (role in listOf("teacher", "admin", "supervisor")) for (st in HomeMessages.State.values()) for (seed in -3..400) {
        val m = HomeMessages.pick(role, st, seed, 3, 1, 4)
        check(m.title.isNotBlank() && m.subtitle.isNotBlank() && m.icon.isNotBlank())
    }
    check((0..10).map { HomeMessages.pick("teacher", HomeMessages.State.WAITING, it).title }.toSet().size >= 3)
    println("HomeMessages self-test passed")
}
