# جرد عناصر الحلقات والمحفّظين والمشرفين (RC104 — المرحلة 3)

المنهج: (1) تحليل رسم الاستدعاءات لكل دوال `ui/` (الشاشات الـ45 + المكوّنات + `guardian`)، (2) مطابقة حقول الكيانات (`h.name` و`teacherLine` و`teacherNames` و`halaqaName` وأسماء المحفّظ/المشرف بالعربية) وأدوات الصور (`NourAvatar` / `StudentAvatar` / `OrgLogo`) داخل كل دالة، (3) قراءة كل عنصر مرشَّح يدويًا. مساعدا الرسم المعرفي (`.ua`) والتحليل النصي لهما حدود: العناصر المبنية داخل جسم الشاشة (غير مسمّاة) لا تظهر في الرسم، فجُرّدت بالقراءة.
نظام الكيانات الموجود أصلًا: `EntityColors` (لون ثابت لكل كيان: حلقة/محفّظ/مشرف…) لكنه يُستخدم في 4 ملفات فقط، وهذا سبب التفاوت.

## الوحدات (عنصر = ما يعرض اسم حلقة أو محفّظ أو مشرف)
| # | الوحدة | الملف:السطر | الكيان | الحاوية الحالية | زخرفة اليوم | الشاشات (من رسم الاستدعاءات) |
|---|---|---|---|---|---|---|
| H1 | HalaqaManagementCard | components/HalaqaCard.kt:73 | حلقة | ElevatedCard + ترويسة | ✅ (RC103) | HalaqasManagementScreen |
| H2 | SupHalaqaCard | screens/SupervisorScreens.kt:153 | حلقة (+محفّظ) | NourCard | ❌ (لقطة المالك) | SupervisorHomeScreen، SupervisorHalaqasScreen |
| H3 | صفوف الجلسات (inline) | screens/SupervisorMoreScreens.kt (SupervisorSessionsScreen) | حلقة (+محفّظ) | NourCard | ❌ | SupervisorSessionsScreen |
| H4 | بطاقات الزيارات (inline) | screens/SupervisorScreens.kt (SupervisorVisitsScreen) | حلقة | Surface | ❌ | SupervisorVisitsScreen |
| H5 | HalaqaOverviewCard | components/HalaqaScopeUi.kt:108 | حلقة | Surface | ❌ | SessionsScreen |
| H6 | OrgHalaqaRow | screens/OrgScreen.kt:573 | حلقة + مشرفون + محفّظون | Surface + OrgLogo | ❌ | OrgScreen |
| H7 | ترويسات المجموعات «اسم الحلقة · N» وعناوين «المشرفون/المحفّظون/الحلقات» (Text / NourSectionTitle عادي) | CoreScreens.kt:353, :4453؛ TeamScreen.kt:125,129؛ ObserverScreens.kt:150؛ ManagementScreens.kt:697 | حلقة/محفّظ/مشرف | Text / NourSectionTitle | ❌ | StudentsScreen، PlansScreen، TeamScreen، ObserverScreen، HalaqasManagementScreen (حوار الطاقم) |
| H8 | GuardianProgramsSection | screens/ManagementScreens.kt:2451 | حلقة/برنامج | Surface | ❌ | GuardianProfileNativeScreen |
| H9 | AdminHalaqaTodayCard | components/AdminHalaqaTodayCard.kt:21 | حلقة (ملخص اليوم) | بطاقة مشتقة | يُراجع | DashboardScreen (إداري) |
| H10 | HalaqaComparisonCard / HalaqaScopeBar | components/OrgInsightCards.kt:281، HalaqaScopeUi.kt:27 | حلقة | شرائح (Chips) | — أدوات اختيار، خارج النطاق | InsightsScreen، Students/Plans/Sessions/Reports/Certificates/Rankings |
| H11 | حوارات الحلقة (Members/Staff/Dialog/Delete/Assign/SupVisit/SupervisorVisit/DeletedSessions) | ManagementScreens.kt:569–812، SupportScreens.kt:429، SupervisorScreens.kt:594 | حلقة/محفّظ/مشرف | HalaqatiAlertDialog | ✅ شريط الحوار (RC104) | HalaqasManagement، SupervisionNative، SupportAdmin، SupervisorVisits، Sessions |
| T1 | StaffCard | screens/TeamScreen.kt:138 | محفّظ/مشرف | Surface + شريط لوني جانبي | ❌ | TeamScreen |
| T2 | بطاقات المراقبة (inline) | screens/StaffMonitorScreen.kt:~175 | محفّظ/مشرف | NourCard (marker) | ❌ | StaffMonitorScreen |
| T3 | SupPersonRow | screens/SupervisorScreens.kt:62 | محفّظ | NourCard | ❌ | SupervisorInsightsScreen |
| T4 | PersonCard / PersonChip | screens/OrgScreen.kt:501/516 | محفّظ/مشرف | Surface | ❌ | OrgScreen |
| T5 | صفوف الحسابات | screens/CoreScreens.kt:6228 | حسابات المستخدمين (أدوار متعددة) | صفوف | خارج النطاق (إدارة حسابات) | UsersScreen |
| T6 | اختيار الدور/جمهور البث | AuthScreens، BroadcastCenter | دور | شرائح | خارج النطاق | Auth*, Broadcast |

## مصفوفة الشاشات الـ45
شاشات فيها وحدات كيانية: HalaqasManagementScreen (H1، H11)، SupervisorHomeScreen/SupervisorHalaqasScreen (H2)، SupervisorSessionsScreen (H3)، SupervisorVisitsScreen (H4، H11)، SessionsScreen (H5، H7، H11)، OrgScreen (H6، T4)، StudentsScreen/PlansScreen (H7)، GuardianProfileNativeScreen (H8)، DashboardScreen (H9)، TeamScreen (T1)، StaffMonitorScreen (T2)، SupervisorInsightsScreen (T3)، SupervisionNativeScreen/SupportAdminScreen (H11).
الشاشات الباقية (الدخول ×5، Approvals، Awards، SessionRoster، QuickMemorization، Users، DonorChats، DonorHome، Exams، Guardian×2، Insights، Community، Notifications، Observer×2، Reports، Certificates، QuranCalculator، Rankings، ImportExport، Settings، StudentFollowUp، StudentProfile، Sync) لا تعرض بطاقة كيان؛ فيها شرائح اختيار أو أسماء داخل نص فقط.

## الخطة (من النظام المركزي)
1. مكوّن جديد `EntityBand(entity)`: شريط زخرفي رفيع بلون الكيان (حلقة/محفّظ/مشرف) بنمط girih (`PatternLevel.Strip`) فوق بطاقة الكيان.
2. `NourCard(entity = …)` و`NourSectionTitle(entity = …)` لإدخاله دون تغيير ترتيب المحتوى.
3. تطبيقه على H2–H8 وT1–T4 فقط. لا تغيير في تخطيط الأزرار والنصوص.

## التنفيذ (ما طُبّق فعليًا في RC104)
`EntityBand` و`EntityRule` (components/EntityKit.kt)، و`NourCard(entity)` و`NourSectionTitle(entity)` و`BorderedCard(entity)`. طُبّقت على: H2 (SupHalaqaCard)، H3 (صفّا الجلسات)، H4 (بطاقة الزيارة)، H5 (HalaqaOverviewCard)، H6 (OrgHalaqaRow)، H7 (4 مواضع + ترويستا الفريق)، H8 (GuardianProgramsSection)، T1 (StaffCard)، T2 (StaffMonitor)، T3 (SupPersonRow)، T4 (PersonCard). لم تُغيَّر: H9 (AdminHalaqaTodayCard) لأنها بطاقة ملخص لا اسم كيان، وH10 وT5 وT6 (أدوات اختيار وحسابات).
