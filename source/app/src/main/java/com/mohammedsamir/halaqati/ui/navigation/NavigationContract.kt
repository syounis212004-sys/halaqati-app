package com.mohammedsamir.halaqati.ui.navigation
import com.mohammedsamir.halaqati.model.UserRole
object NavigationContract {
    private val actions=mapOf(
        "open_home" to AppDestination.Home,"open_notifications" to AppDestination.Notifications,"open_halaqas" to AppDestination.Halaqas,
        "open_community" to AppDestination.Community,"open_sessions" to AppDestination.Sessions,"open_today_session" to AppDestination.SessionToday,"open_students" to AppDestination.Students,
        "open_plans" to AppDestination.Plans,"open_certificates" to AppDestination.Certificates,"open_reports" to AppDestination.Reports,
        "open_quran" to AppDestination.Quran,"open_rankings" to AppDestination.Rankings,"open_importexport" to AppDestination.ImportExport,
        "open_sync_center" to AppDestination.SyncCenter,"open_supervision" to AppDestination.Supervision,"open_users" to AppDestination.Users,
        "open_guardian_profile" to AppDestination.GuardianProfile,"open_profile" to AppDestination.GuardianProfile,
        "open_competition" to AppDestination.GuardianCompetition,"open_achievements" to AppDestination.GuardianAchievements,"open_settings" to AppDestination.Settings,"open_contests" to AppDestination.Contests)
    fun resolve(role:UserRole,actionId:String):AppDestination?=actions[actionId]?.takeIf{RoleNavigationPolicy.canOpen(role,it)}
}
