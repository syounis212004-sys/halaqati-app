# جرد هوية الشاشات (Phase 1 — RC104)

المنهج: تحليل رسم الاستدعاءات (call graph) على كل دوال Kotlin في `ui/` (الدالة ← الدوال المعرّفة التي تستدعيها، إغلاق متعدٍّ)، مع التوقف عند شاشة أخرى. العلامات: `nourPattern/girih/islamicPattern/heroBackground/nourHeroFill/LatticeStrip`. الرسم المعرفي `.ua/knowledge-graph.json` فيه 48 حافة `calls` فقط فلم يكفِ للاستدعاءات، فاستُخدم للتوجيه فقط. القيم المعدودة (alpha/hex/حوارات…) محسوبة على الدوال المحلية المتاحة من الشاشة (قد تشمل مساعدات ملف مشترك مع شاشات أخرى؛ هي تقريبية).

## الحصيلة (45 شاشة)
| الفئة | العدد | المعنى |
|---|---|---|
| A | 8 | موحّدة مباشرة (hero/nourPattern في جسمها) |
| B | 10 | موحّدة عبر مكوّن مشترك (NourStatStrip ← nourHeroFill ← PatternLevel.Hero) |
| C | 18 | شريط الزخرفة فقط من AppTopBar؛ لا هوية في المحتوى |
| D | 9 | بلا AppTopBar المشترك (Auth ×5، SessionRoster، DonorChats، StudentFollowUp، StudentProfile) |

مشكلات بصرية مرجّحة من الكود: 3 (SupervisorVisits FAB، رأسا StudentProfile/StudentFollowUp). لا دليل على تداخل/قصّ في غيرها من الكود وحده؛ التأكيد بلقطات CI (Robolectric) في المرحلة 2.

## ما وُجد في النظام المشترك (أسباب التفاوت)
1. **أربعة أنظمة نقش متوازية**: `girih` (+PatternLevel) و`latticeOverlay` (Lattice.kt، يستخدمه LatticeStrip بألفا .5/خلية 12dp) و`islamicPattern`/`PatternBox` و`PatternTile.kt`. الموحَّد فعليًا هو girih+PatternLevel فقط؛ شريط الزخرفة تحت كل رأس يرسم نقشًا مختلفًا.
2. **EmptyState** (67 استخدامًا/23 ملفًا) و**StateNotice** (127/27) و**LoadingSkeleton** (64) بلا أي نقش — `PatternLevel.Subtle` معرَّف «لحالات الفراغ والحوارات» ولا يُستخدم.
3. **AlertDialog** مباشر: 82 استدعاء في 23 ملفًا (3 فقط داخل components)، لا غلاف موحّد → لا هوية موحّدة للحوارات؛ ModalBottomSheet 34 استخدامًا.
4. **AppTopBar** مستخدم في 18 ملفًا؛ رأسا StudentProfile/StudentFollowUp يستخدمان `TopAppBar` Material، والمصادقة بلا رأس مشترك.
5. فواصل `HorizontalDivider`/`Divider` عادية (27 في الشاشات)؛ لا فاصل زخرفي هادئ.
6. `nourPattern` لا يظهر في الشاشات إلا في موضعين (بلاطات ManagementScreens:793 وSupportScreens:647) — لا استدعاءات خام لـ girih/islamicPattern في الشاشات (جيد).
7. ألوان hex خام في الشاشات قليلة (Awards 4 ميداليات، Donor 1)؛ الخطوط: لا `fontFamily` خام في واجهات الشاشات (Cairo عبر النمط)، و`fontSize` خام نادر (≈5 مواضع).
8. alpha يدوي: Plans 23، SessionRoster 12، Students/Donor 7، Org 6 … → يحتاج رموز موحّدة في `HalaqatiUiTokens`.

## أهم المكوّنات المشتركة للتوسيع (بالأثر)
1. `LatticeStrip` (يغطي 36 شاشة): نقله إلى نمط girih (PatternLevel جديد Strip) ليتطابق مع الـhero.
2. `EmptyState` (23 ملف) + `StateNotice` (27): خلفية `PatternLevel.Subtle` هادئة.
3. غلاف حوار موحّد (`HalaqatiAlertDialog` أو ضبط Shape/Container في Theme) لـ82 حوارًا — الأثر الأكبر لكن يلمس مواقع الاستدعاء (استبدال ميكانيكي للاسم)؛ بديل آمن: ضبط `shapes.extraLarge`/ألوان الحوار في Theme.kt فقط.
4. `NourSectionTitle` / فاصل زخرفي هادئ `OrnamentDivider` (يُضاف دون تغيير التخطيط؛ يستبدل الفواصل في 12 ملفًا اختياريًا).
5. `NourCard`/`ProCards`/`MetricCard`: حافة/علامة موحّدة، بلا نقش كثيف على كل بطاقة.
6. رمز alpha موحّد في `HalaqatiUiTokens` وإحلاله تدريجيًا حيث يلزم فقط.

## جدول الشاشات (الفئات: A/B/C/D أعلاه)
عمود «تعديل مشترك؟» = هل يكفي تعديل مكوّن مشترك؛ «لمسة شاشة؟» = هل يلزم تعديل الشاشة نفسها.

| الشاشة | الملف:السطر | الفئة | ما يحتاج توحيدًا | تعديل مشترك؟ | لمسة شاشة؟ | مشكلة بصرية حقيقية؟ |
|---|---|---|---|---|---|---|
| ApprovalsScreen | ApprovalsScreen.kt:49 | C | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| AuthScreen | AuthScreens.kt:50 | D | alpha ×3, حوارات/Sheets مادية عادية, رأس غير موحّد | جزئيًا (شريط/خلفية AuthBand جديدة) | اختياري | لا يوجد دليل (يحتاج لقطة CI) |
| PendingApprovalScreen | AuthScreens.kt:341 | D | حوارات/Sheets مادية عادية, رأس غير موحّد | جزئيًا (شريط/خلفية AuthBand جديدة) | اختياري | لا يوجد دليل (يحتاج لقطة CI) |
| PasswordRecoveryScreen | AuthScreens.kt:400 | D | رأس غير موحّد | جزئيًا (شريط/خلفية AuthBand جديدة) | اختياري | لا يوجد دليل (يحتاج لقطة CI) |
| CompleteSignupScreen | AuthScreens.kt:496 | D | حوارات/Sheets مادية عادية, رأس غير موحّد | جزئيًا (شريط/خلفية AuthBand جديدة) | اختياري | لا يوجد دليل (يحتاج لقطة CI) |
| FrozenAccountScreen | AuthScreens.kt:627 | D | رأس غير موحّد | جزئيًا (شريط/خلفية AuthBand جديدة) | اختياري | لا يوجد دليل (يحتاج لقطة CI) |
| AwardsScreen | AwardsScreen.kt:62 | C | alpha ×2, hex ×4, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | ألوان الميداليات hex ثابتة (4) — تُنقل إلى رموز theme |
| StudentsScreen | CoreScreens.kt:100 | B | alpha يدوي ×7, fontSize خام ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SessionsScreen | CoreScreens.kt:1320 | C | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SessionRosterScreen | CoreScreens.kt:2168 | D | alpha يدوي ×12, fontSize خام ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية, رأس غير موحّد | نعم | اختياري | لا يوجد دليل (يحتاج لقطة CI) |
| QuickMemorizationScreen | CoreScreens.kt:3966 | C | alpha يدوي ×5, fontSize خام ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| PlansScreen | CoreScreens.kt:4193 | C | alpha يدوي ×23, fontSize خام ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية | نعم | لا | 23 قيمة alpha يدوية؛ شاشة كبيرة (حوارات كثيرة) تحتاج فحص تباين بصري |
| UsersScreen | CoreScreens.kt:6228 | C | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| DashboardScreen | DashboardScreen.kt:52 | A | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| DonorChatsScreen | DonorChatsScreen.kt:48 | D | alpha ×2, حوارات/Sheets مادية عادية, فواصل عادية, رأس غير موحّد | نعم | اختياري | لا يوجد دليل (يحتاج لقطة CI) |
| DonorHomeScreen | DonorScreen.kt:59 | A | alpha يدوي ×7, hex ×5 | جزئيًا | لا | لا يوجد دليل (يحتاج لقطة CI) |
| ExamsScreen | ExamsScreen.kt:104 | B | alpha ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| GuardianCompetitionNativeScreen | GuardianParityScreens.kt:29 | A | EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| GuardianAchievementsNativeScreen | GuardianParityScreens.kt:161 | A | alpha ×1, بطاقات Material عادية | جزئيًا | لا | لا يوجد دليل (يحتاج لقطة CI) |
| InsightsScreen | InsightsScreen.kt:40 | C | — | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| HalaqasManagementScreen | ManagementScreens.kt:55 | A | alpha ×2, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| CommunityNativeScreen | ManagementScreens.kt:1152 | C | alpha ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| NotificationsNativeScreen | ManagementScreens.kt:1735 | C | alpha ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SupervisionNativeScreen | ManagementScreens.kt:2112 | C | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| GuardianProfileNativeScreen | ManagementScreens.kt:2487 | C | alpha ×1, EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| ObserverScreen | ObserverScreens.kt:55 | C | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| ObserverRequestsAdminScreen | ObserverScreens.kt:305 | B | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| OrgScreen | OrgScreen.kt:108 | A | alpha يدوي ×6, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| ReportsScreen | ReportsAndTools.kt:159 | C | alpha ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| CertificatesScreen | ReportsAndTools.kt:908 | C | alpha ×2, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| QuranCalculatorScreen | ReportsAndTools.kt:3328 | C | alpha ×2, fontSize خام ×1, حوارات/Sheets مادية عادية, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| RankingsScreen | ReportsAndTools.kt:3413 | C | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| ImportExportScreen | ReportsAndTools.kt:3714 | C | alpha ×1, حوارات/Sheets مادية عادية, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SettingsScreen | ReportsAndTools.kt:4767 | A | alpha ×2, حوارات/Sheets مادية عادية, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| StaffMonitorScreen | StaffMonitorScreen.kt:68 | B | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, فواصل عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| StudentFollowUpScreen | StudentFollowUpScreen.kt:34 | D | EmptyState بلا زخرفة, رأس غير موحّد | نعم | نعم | رأس Material `TopAppBar` عادي بلا شريط الزخرفة |
| StudentProfileScreen | StudentProfileScreen.kt:48 | D | alpha ×3, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية, فواصل عادية, رأس غير موحّد | نعم | نعم | رأس Material `TopAppBar` عادي بلا شريط الزخرفة (تباين رأس مع بقية الصفحات) |
| SupervisorSessionsScreen | SupervisorMoreScreens.kt:46 | B | EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SupervisorInsightsScreen | SupervisorMoreScreens.kt:183 | A | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SupervisorHomeScreen | SupervisorScreens.kt:231 | B | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SupervisorHalaqasScreen | SupervisorScreens.kt:338 | B | حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SupervisorVisitsScreen | SupervisorScreens.kt:393 | B | alpha ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | نعم | FAB: `padding(16.dp)` ثابت + `bottom=96.dp` بدل FabMargin/FabClearance/CollapsingFab (احتمال تغطية آخر بطاقة/تفاوت مع باقي الشاشات) |
| SupportAdminScreen | SupportScreens.kt:86 | B | alpha ×3, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| SyncCenterScreen | SyncCenterScreen.kt:33 | C | alpha ×1, حوارات/Sheets مادية عادية, EmptyState بلا زخرفة, بطاقات Material عادية | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |
| TeamScreen | TeamScreen.kt:53 | B | alpha ×3, fontSize خام ×1, EmptyState بلا زخرفة | نعم | لا | لا يوجد دليل (يحتاج لقطة CI) |

## شاشات فرعية/حوارات تتبع الشاشات
الحوارات والـSheets تُعرَّف داخل ملفات الشاشات نفسها (Dialog/Sheet كدوال خاصة)، وأهمها: `DeletedSessionsDialog.kt`، `PlanRangeRebaseDialog.kt`، `QuickActionSheet.kt`، `QuranPicker.kt`، `SmartPlanPhasesUi.kt`/`SmartPlanBulkCycleUi.kt`، `ReportStudioControls.kt`، `StudentIdentitySection.kt`، `IdentityImport.kt`، `BroadcastCenter.kt`، `SupportExtras.kt`، `DonorImpactScreen.kt` (شاشة المتبرع الفرعية)، `SettingsParitySections.kt`. كلها تُوحَّد بالغلاف المشترك للحوارات/EmptyState دون لمس تخطيطها.

## قيود التحليل
- لا لقطات شاشة في هذه المرحلة: «مشكلة بصرية حقيقية» مبنية على الكود فقط.
- الأعداد تقريبية (مطابقة نصية على دوال متاحة؛ أسماء دوال متكررة بين ملفات قد تضخّمها).
