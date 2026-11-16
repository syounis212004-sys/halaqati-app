package com.mohammedsamir.halaqati.ui.navigation

sealed interface AppDestination {
    val route: String
    val label: String
    val iconKey: String

    data object Home : AppDestination { override val route="home"; override val label="الرئيسية"; override val iconKey="home" }
    data object Notifications : AppDestination { override val route="notifications"; override val label="الإشعارات"; override val iconKey="notifications" }
    data object Halaqas : AppDestination { override val route="halaqas"; override val label="الحلقات والفئات"; override val iconKey="group" }
    data object Community : AppDestination { override val route="community"; override val label="الإعلانات ومنتدى الحلقة"; override val iconKey="forum" }
    data object Sessions : AppDestination { override val route="sessions"; override val label="الجلسات"; override val iconKey="sessions" }
    data object SessionToday : AppDestination { override val route="session_today"; override val label="جلسة اليوم"; override val iconKey="sessions" }
    data object QuickMemorization : AppDestination { override val route="quick_memorization"; override val label="التحفيظ السريع"; override val iconKey="plans" }
    data object Students : AppDestination { override val route="students"; override val label="الطلاب"; override val iconKey="students" }
    data object Plans : AppDestination { override val route="plans"; override val label="الخطط"; override val iconKey="plans" }
    data object Certificates : AppDestination { override val route="certificates"; override val label="الشهادات"; override val iconKey="award" }
    data object Reports : AppDestination { override val route="reports"; override val label="التقارير"; override val iconKey="reports" }
    data object Quran : AppDestination { override val route="quran"; override val label="حاسبة القرآن"; override val iconKey="book" }
    data object Rankings : AppDestination { override val route="rankings"; override val label="الترتيب والإحصائيات"; override val iconKey="leaderboard" }
    data object ImportExport : AppDestination { override val route="importexport"; override val label="الاستيراد والتصدير"; override val iconKey="importexport" }
    data object SyncCenter : AppDestination { override val route="sync_center"; override val label="مركز المزامنة"; override val iconKey="sync" }
    data object Supervision : AppDestination { override val route="supervision"; override val label="متابعة المحفظين"; override val iconKey="supervisor" }
    data object Visits : AppDestination { override val route="visits"; override val label="الزيارات"; override val iconKey="supervisor" }
    data object Users : AppDestination { override val route="users"; override val label="الحسابات والصلاحيات"; override val iconKey="users" }
    data object Approvals : AppDestination { override val route="approvals"; override val label="طلبات الاعتماد"; override val iconKey="users" }
    data object ObserverRequests : AppDestination { override val route="observer_requests"; override val label="طلبات المانحين"; override val iconKey="users" }
    data object GuardianProfile : AppDestination { override val route="guardian_profile"; override val label="أبنائي"; override val iconKey="profile" }
    data object GuardianCompetition : AppDestination { override val route="guardian_competition"; override val label="إحصائيات ومنافسة"; override val iconKey="competition" }
    data object GuardianAchievements : AppDestination { override val route="guardian_achievements"; override val label="الإنجازات"; override val iconKey="award" }
    data object Settings : AppDestination { override val route="settings"; override val label="الإعدادات"; override val iconKey="settings" }
    data object Insights : AppDestination { override val route="insights"; override val label="المتابعة والمقارنة"; override val iconKey="leaderboard" }
    data object Awards : AppDestination { override val route="awards"; override val label="التكريم الشهري"; override val iconKey="award" }
    data object Support : AppDestination { override val route="support"; override val label="الدعم والمانحون"; override val iconKey="award" }
 data object Exams : AppDestination { override val route="exams"; override val label="الاختبارات"; override val iconKey="reports" }
 data object Org : AppDestination { override val route="org"; override val label="الهيكل الإداري"; override val iconKey="users" }
    data object Team : AppDestination { override val route="team"; override val label="كادر الحلقات"; override val iconKey="users" }
    data object DonorChats : AppDestination { override val route="donor_chats"; override val label="محادثات المانحين"; override val iconKey="forum" }
    data object DonorAwards : AppDestination { override val route="donor_awards"; override val label="المكرَّمون"; override val iconKey="award" }
    data object DonorSupport : AppDestination { override val route="donor_support"; override val label="الدعم"; override val iconKey="support" }
    data object DonorImpact : AppDestination { override val route="donor_impact"; override val label="أثري"; override val iconKey="impact" }
    data object DonorStats : AppDestination { override val route="donor_stats"; override val label="الإحصاءات"; override val iconKey="stats" }
    data object Add : AppDestination { override val route="add"; override val label="جلسة جديدة"; override val iconKey="add" }
    data object More : AppDestination { override val route="more"; override val label="المزيد"; override val iconKey="more" }

    companion object {
        val navigable: List<AppDestination> = listOf(Home,Notifications,Halaqas,Community,Sessions,SessionToday,QuickMemorization,Students,Plans,Certificates,Exams,Org,Team,Awards,Support,DonorChats,DonorAwards,DonorSupport,DonorImpact,DonorStats,Reports,Quran,Rankings,Insights,ImportExport,SyncCenter,Supervision,Visits,Users,Approvals,ObserverRequests,GuardianProfile,GuardianCompetition,GuardianAchievements,Settings)
        fun fromRoute(route: String?): AppDestination? = when(route?.trim()) {
            "profile" -> GuardianProfile
            else -> navigable.firstOrNull { it.route == route }
        }
    }
}
