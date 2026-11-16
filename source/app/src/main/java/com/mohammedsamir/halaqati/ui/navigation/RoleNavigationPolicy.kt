package com.mohammedsamir.halaqati.ui.navigation

import com.mohammedsamir.halaqati.model.UserRole

data class MoreSection(val title: String, val items: List<AppDestination>)
data class DrawerNavItem(val destination: AppDestination, val label: String)

object RoleNavigationPolicy {
    private val admin = listOf(
        AppDestination.Home, AppDestination.Notifications, AppDestination.Halaqas,
        AppDestination.Community, AppDestination.Sessions, AppDestination.SessionToday, AppDestination.QuickMemorization,
        AppDestination.Students, AppDestination.Plans, AppDestination.Certificates, AppDestination.Exams, AppDestination.Awards, AppDestination.Support, AppDestination.DonorChats,
        AppDestination.Reports, AppDestination.Quran, AppDestination.Rankings, AppDestination.Insights,
        AppDestination.ImportExport, AppDestination.SyncCenter, AppDestination.Supervision, AppDestination.Visits,
        AppDestination.Users, AppDestination.Approvals, AppDestination.ObserverRequests, AppDestination.Org, AppDestination.Team, AppDestination.Settings,
    )
    // RC53: the supervisor has his own, smaller set of pages: follow-up of teachers and halaqat, no smart plans, certificates,
    // quick memorization or data import. Reports stay (he needs them).
    private val supervisor = listOf(
        AppDestination.Home, AppDestination.Notifications, AppDestination.Halaqas, AppDestination.Community,
        AppDestination.Sessions, AppDestination.Students, AppDestination.Exams, AppDestination.Reports, AppDestination.Rankings,
        AppDestination.Insights, AppDestination.Supervision, AppDestination.Visits, AppDestination.Approvals, AppDestination.Org, AppDestination.Team,
        AppDestination.SyncCenter, AppDestination.Settings,
    )
    private val teacher = admin.filterNot {
        it == AppDestination.Users || it == AppDestination.Org || it == AppDestination.Team || it == AppDestination.ObserverRequests || it == AppDestination.Awards ||
            it == AppDestination.Support || it == AppDestination.DonorChats || it == AppDestination.Supervision || it == AppDestination.Visits
    }
    private val guardian = listOf(
        AppDestination.Home, AppDestination.Notifications, AppDestination.Community,
        AppDestination.Plans, AppDestination.Exams, AppDestination.Reports, AppDestination.GuardianCompetition,
        AppDestination.GuardianAchievements, AppDestination.GuardianProfile,
        AppDestination.SyncCenter, AppDestination.Settings,
    )
    // RC32: the observer sees one read-only page (Home) plus notifications; all data comes from dedicated RPCs.
    private val observer = listOf(AppDestination.Home, AppDestination.DonorAwards, AppDestination.DonorSupport, AppDestination.DonorImpact, AppDestination.DonorStats, AppDestination.Notifications, AppDestination.SyncCenter, AppDestination.Settings)
    private val limited = listOf(AppDestination.Home, AppDestination.GuardianProfile, AppDestination.Settings)

    fun destinations(role: UserRole) = when (role) {
        UserRole.ADMIN -> admin
        UserRole.SUPERVISOR -> supervisor
        UserRole.TEACHER -> teacher
        UserRole.GUARDIAN -> guardian
        UserRole.OBSERVER -> observer
        else -> limited
    }

    fun bottom(role: UserRole, mode: com.mohammedsamir.halaqati.plans.WorkMode = com.mohammedsamir.halaqati.plans.WorkMode.REGULAR) = if (role == UserRole.OBSERVER) {
        listOf(AppDestination.Home, AppDestination.DonorAwards, AppDestination.DonorSupport, AppDestination.DonorImpact, AppDestination.More)
    } else if (role == UserRole.GUARDIAN) {
        listOf(AppDestination.Home, AppDestination.GuardianProfile, AppDestination.GuardianAchievements, AppDestination.Community, AppDestination.More)
    } else if (role == UserRole.SUPERVISOR) {
        listOf(AppDestination.Home, AppDestination.Halaqas, AppDestination.Add, AppDestination.Supervision, AppDestination.More)
    } else {
        // RC27.10: a program has no smart plans; its fourth tab is the ranking / statistics page.
        if (mode == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM) {
            listOf(AppDestination.Home, AppDestination.Sessions, AppDestination.Add, AppDestination.Community, AppDestination.More)
        } else {
            // RC40: Students and Plans moved to the Home shortcuts / More; the middle "＋" is the smart add-session button.
            listOf(AppDestination.Home, AppDestination.Sessions, AppDestination.Add, AppDestination.Community, AppDestination.More)
        }
    }

    /**
     * Golden-master drawer order and role-specific labels copied from Halaqati v2.44.
     * SyncCenter stays accessible from the compact sync badge instead of adding a new row
     * that did not exist in the 2.44 drawer.
     */
    fun drawerItems(role: UserRole, mode: com.mohammedsamir.halaqati.plans.WorkMode): List<DrawerNavItem> =
        drawerItems(role).filterNot { role != UserRole.GUARDIAN && mode == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM && it.destination == AppDestination.Plans }

    fun drawerItems(role: UserRole): List<DrawerNavItem> = when (role) {
        UserRole.ADMIN -> listOf(
            item(AppDestination.Home, "الرئيسية"),
            item(AppDestination.Notifications, "الإشعارات"),
            item(AppDestination.Halaqas, "إدارة الحلقات والفئات"),
            item(AppDestination.Community, "الإعلانات ومنتدى الحلقة"),
            item(AppDestination.Sessions, "الجلسات اليومية"),
            item(AppDestination.Students, "الطلاب"),
            item(AppDestination.Plans, "الخطط الذكية"),
            item(AppDestination.Certificates, "الشهادات"),
            item(AppDestination.Exams, "الاختبارات"),
            item(AppDestination.Awards, "التكريم الشهري"),
            item(AppDestination.Support, "الدعم والمانحون"),
            item(AppDestination.DonorChats, "محادثات المانحين"),
            item(AppDestination.ObserverRequests, "طلبات المانحين"),
            item(AppDestination.Reports, "التقارير"),
            item(AppDestination.Quran, "حاسبة القرآن"),
            item(AppDestination.Rankings, "الترتيب والإحصائيات"),
            item(AppDestination.Insights, "المتابعة والمقارنة"),
            item(AppDestination.ImportExport, "الاستيراد والتصدير"),
            item(AppDestination.Supervision, "متابعة المحفظين والمشرفين"),
            item(AppDestination.Visits, "زيارات المشرفين"),
            item(AppDestination.Approvals, "طلبات الاعتماد"),
            item(AppDestination.Org, "الهيكل الإداري"),
            item(AppDestination.Team, "كادر الحلقات"),
            item(AppDestination.Users, "الحسابات والصلاحيات"),
            item(AppDestination.Settings, "الإعدادات"),
        )
        UserRole.SUPERVISOR -> listOf(
            item(AppDestination.Home, "الرئيسية"),
            item(AppDestination.Notifications, "الإشعارات"),
            item(AppDestination.Halaqas, "الحلقات"),
            item(AppDestination.Supervision, "متابعة المحفظين"),
            item(AppDestination.Visits, "الزيارات"),
            item(AppDestination.Sessions, "الجلسات اليومية"),
            item(AppDestination.Students, "الطلاب"),
            item(AppDestination.Exams, "الاختبارات"),
            item(AppDestination.Reports, "التقارير"),
            item(AppDestination.Rankings, "الترتيب والإحصائيات"),
            item(AppDestination.Insights, "المتابعة والمقارنة"),
            item(AppDestination.Community, "الإعلانات والتواصل"),
            item(AppDestination.Approvals, "طلبات المحفّظين"),
            item(AppDestination.Org, "الهيكل الإداري"),
            item(AppDestination.Team, "كادر الحلقات"),
            item(AppDestination.Settings, "الإعدادات"),
        )
        UserRole.TEACHER -> listOf(
            item(AppDestination.Home, "الرئيسية"),
            item(AppDestination.Notifications, "الإشعارات"),
            item(AppDestination.Community, "الإعلانات ومنتدى الحلقة"),
            item(AppDestination.Halaqas, "حلقتي والفئات"),
            item(AppDestination.Sessions, "الجلسات اليومية"),
            item(AppDestination.Students, "طلابي"),
            item(AppDestination.Plans, "الخطط الذكية"),
            item(AppDestination.Certificates, "الشهادات"),
            item(AppDestination.Exams, "الاختبارات"),
            item(AppDestination.Reports, "التقارير"),
            item(AppDestination.Quran, "حاسبة القرآن"),
            item(AppDestination.Rankings, "الترتيب والإحصائيات"),
            item(AppDestination.Insights, "المتابعة والمقارنة"),
            item(AppDestination.ImportExport, "تصدير البيانات"),
            item(AppDestination.Approvals, "طلبات أولياء الأمور"),
            item(AppDestination.Settings, "الإعدادات"),
        )
        UserRole.GUARDIAN -> listOf(
            item(AppDestination.Home, "الرئيسية"),
            item(AppDestination.Notifications, "الإشعارات"),
            item(AppDestination.Community, "الإعلانات ومنتدى الحلقة"),
            item(AppDestination.Plans, "خطط أبنائي الذكية"),
            item(AppDestination.Exams, "اختبارات أبنائي"),
            item(AppDestination.Reports, "تقارير أبنائي"),
            item(AppDestination.GuardianCompetition, "إحصائيات ومنافسة"),
            item(AppDestination.GuardianAchievements, "الإنجازات"),
            item(AppDestination.GuardianProfile, "ملف الطالب"),
            item(AppDestination.Settings, "الإعدادات"),
        )
        UserRole.OBSERVER -> listOf(
            item(AppDestination.Home, "الرئيسية"),
            item(AppDestination.DonorAwards, "المكرَّمون"),
            item(AppDestination.DonorSupport, "الدعم"),
            item(AppDestination.DonorImpact, "أثري"),
            item(AppDestination.DonorStats, "الإحصاءات"),
            item(AppDestination.Notifications, "الإشعارات"),
            item(AppDestination.Settings, "الإعدادات"),
        )
        else -> listOf(
            item(AppDestination.Home, "الرئيسية"),
            item(AppDestination.GuardianProfile, "ملف الطالب"),
            item(AppDestination.Settings, "الإعدادات"),
        )
    }

    fun moreSections(role: UserRole): List<MoreSection> {
        val allowed = destinations(role).toSet()
        fun sec(title: String, vararg c: AppDestination): MoreSection? {
            val v = c.filter { it in allowed && it !in bottom(role) }
            return v.takeIf { it.isNotEmpty() }?.let { MoreSection(title, it) }
        }
        return listOfNotNull(
            sec("إدارة الحلقة", AppDestination.Halaqas, AppDestination.Students, AppDestination.Plans),
            sec("المانح", AppDestination.DonorStats),
            sec("المتابعة والإنجاز", AppDestination.Insights, AppDestination.GuardianCompetition, AppDestination.GuardianAchievements, AppDestination.Rankings, AppDestination.Supervision, AppDestination.Visits, AppDestination.Certificates, AppDestination.Exams, AppDestination.Awards),
            sec("الدعم والمانحون", AppDestination.Support, AppDestination.DonorChats, AppDestination.ObserverRequests),
            sec("التقارير والأدوات", AppDestination.Reports, AppDestination.Quran, AppDestination.ImportExport),
            sec("التواصل", AppDestination.Notifications, AppDestination.Community),
            sec("الإدارة والصلاحيات", AppDestination.Approvals, AppDestination.Org, AppDestination.Team, AppDestination.Users),
            sec("النظام", AppDestination.SyncCenter, AppDestination.Settings),
        )
    }

    fun canOpen(role: UserRole, destination: AppDestination) = destination in destinations(role)

    private fun item(destination: AppDestination, label: String) = DrawerNavItem(destination, label)
}
