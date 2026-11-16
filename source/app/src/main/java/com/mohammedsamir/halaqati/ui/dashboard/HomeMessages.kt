package com.mohammedsamir.halaqati.ui.dashboard

/**
 * RC24.3: the home hero is no longer one fixed sentence. The title, sentence and icon depend on
 * the role (teacher vs administration), on the state of the day, and rotate by date so the same
 * wording does not greet the user every day. Pure Kotlin: no Android types, covered by a self-test.
 */
data class HomeMessage(val title: String, val subtitle: String, val icon: String)

object HomeMessages {
    enum class State { LOADING, OFF_DAY, WAITING, ACTIVE, DONE }

    private val honorifics = setOf("dr", "prof", "د", "أ", "م", "الشيخ", "الأستاذ", "الأستاذة", "الدكتور", "الدكتورة", "المعلم", "المعلمة", "الحاج")

    fun state(loaded: Boolean, offDay: Boolean, waitingForSession: Boolean, heard: Int, waiting: Int): State = when {
        !loaded -> State.LOADING
        offDay -> State.OFF_DAY
        waitingForSession -> State.WAITING
        heard > 0 && waiting == 0 -> State.DONE
        else -> State.ACTIVE
    }

    /** "Dr. Ahmad Ali" -> "Dr. Ahmad"; "محمد سمير أبو يونس" -> "محمد"; never returns just a title. */
    fun displayName(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "بك"
        val first = parts[0]
        val isTitle = first.trimEnd('.').lowercase() in honorifics
        return if (isTitle && parts.size >= 2) "$first ${parts[1]}" else if (isTitle) "بك" else first
    }

    fun pick(role: String, state: State, seed: Int, waiting: Int = 0, absent: Int = 0, heard: Int = 0): HomeMessage {
        val teacher = role.equals("teacher", true)
        val list: List<HomeMessage> = when (state) {
            State.LOADING -> listOf(HomeMessage("نجهّز يومك", "نعرض آخر بيانات محفوظة ريثما تكتمل المزامنة.", "sync"))
            State.OFF_DAY -> if (teacher) listOf(
                HomeMessage("يوم راحة مبارك", "اليوم إجازة، وسجلات الأيام السابقة بين يديك متى شئت.", "rest"),
                HomeMessage("استرح قليلًا", "لا جلسة اليوم؛ يمكنك مراجعة الخطط وتجهيز المقررات القادمة.", "rest"),
                HomeMessage("تجديد النشاط", "إجازة اليوم فرصة لمراجعة متابعات الطلاب بهدوء.", "rest"),
            ) else listOf(
                HomeMessage("إجازة اليوم", "لا جلسات اليوم، والتقارير والخطط متاحة للمراجعة.", "rest"),
                HomeMessage("يوم هادئ", "فرصة لمراجعة الحلقات والخطط والشهادات.", "rest"),
            )
            State.WAITING -> if (teacher) listOf(
                HomeMessage("جلسة اليوم بانتظارك", "افتح الجلسة، سجّل الحضور، ثم ابدأ التسميع.", "play"),
                HomeMessage("يوم جديد مع كتاب الله", "الحضور أولًا ثم التسميع، والبقية تتبعه بإذن الله.", "book"),
                HomeMessage("بارك الله في وقتك", "ثلاث خطوات اليوم: الحضور، التسميع، المتابعة.", "play"),
                HomeMessage("ابدأ على بركة الله", "المقررات جاهزة، والعدادات تبدأ بعد فتح الجلسة.", "play"),
            ) else listOf(
                HomeMessage("متابعة اليوم", "لم تبدأ جلسات اليوم بعد؛ العدادات تبدأ مع أول جلسة.", "play"),
                HomeMessage("صباح العمل", "راجع الخطط المتأخرة قبل أن تبدأ الجلسات.", "book"),
                HomeMessage("نظرة على الحلقات", "الخطط والمتابعات جاهزة للمراجعة.", "book"),
            )
            State.ACTIVE -> if (teacher) listOf(
                HomeMessage(if (waiting > 0) "بقي $waiting من طلابك" else "الجلسة جارية", "أكمل تسميع من بقي، وبارك الله في جهدك.", "book"),
                HomeMessage("أحسنت المتابعة", "سمّع $heard طالبًا حتى الآن" + if (absent > 0) "، وهناك $absent بحاجة لمتابعة غياب." else ".", "check"),
                HomeMessage("واصل بثبات", "كل تسميع اليوم أثر يبقى في صدر طالب.", "book"),
            ) else listOf(
                HomeMessage("الجلسات جارية", "أتمّ $heard طالبًا التسميع" + if (waiting > 0) "، وبقي $waiting." else ".", "check"),
                HomeMessage("متابعة مباشرة", "تابع الحضور والتسميع من لوحة واحدة.", "check"),
            )
            State.DONE -> listOf(
                HomeMessage("أتممتم تسميع اليوم", "أنجز $heard طالبًا اليوم، جزاك الله خيرًا.", "star"),
                HomeMessage("يوم موفّق", "اكتمل تسميع الحاضرين؛ راجع المتابعات إن بقي شيء.", "star"),
                HomeMessage("ما شاء الله", "لا أحد بانتظار التسميع الآن.", "star"),
            )
        }
        return list[Math.floorMod(seed, list.size)]
    }
}
