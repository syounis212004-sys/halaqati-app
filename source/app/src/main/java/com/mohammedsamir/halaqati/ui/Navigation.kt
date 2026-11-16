package com.mohammedsamir.halaqati.ui
import com.mohammedsamir.halaqati.model.*
object RoleNavigation{
 private val all=listOf(
 NavItem("home","الرئيسية","home"),NavItem("notifications","الإشعارات","notifications"),NavItem("halaqas","الحلقات والفئات","group"),NavItem("community","الإعلانات والمجتمع","forum"),NavItem("sessions","الجلسات","event"),NavItem("students","الطلاب","person"),NavItem("plans","الخطط الذكية","timeline"),NavItem("certificates","الشهادات","award"),NavItem("reports","التقارير","report"),NavItem("quran","حاسبة المصحف","book"),NavItem("rankings","الترتيب والإحصائيات","leaderboard"),NavItem("importexport","الاستيراد والتصدير","sync"),NavItem("sync_center","مركز المزامنة","sync"),NavItem("supervision","متابعة المحفظين","supervisor"),NavItem("users","الحسابات والصلاحيات","users"),NavItem("profile","ملف الطالب","profile"),NavItem("settings","الإعدادات","settings"))
 fun items(role:UserRole)=when(role){
  UserRole.ADMIN->all.filter{it.route!="profile"}
  UserRole.SUPERVISOR->all.filter{it.route !in setOf("users","profile")}
  UserRole.TEACHER->all.filter{it.route !in setOf("users","supervision","profile")}
  UserRole.GUARDIAN->all.filter{it.route in setOf("home","notifications","community","plans","reports","rankings","certificates","sync_center","profile","settings")}
  else->all.filter{it.route in setOf("home","profile","settings")}
 }
 fun bottom(role:UserRole)=if(role==UserRole.GUARDIAN) listOf("home","reports","plans","profile","more") else listOf("home","sessions","students","plans","more")
}
