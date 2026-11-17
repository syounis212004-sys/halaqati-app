# تدقيق الصلاحيات وحماية بيانات الطلاب — تطبيق حلقتي (Android)

- النطاق: التركيز 1 (الأدوار، الأسرار، التخزين المحلي، WebView، الشبكة، الـ Manifest، سكربتات الخصوصية).
- المنهج: قراءة فقط. كل بند مؤكد مرتبط بسطر حقيقي في المصدر. لم أستخدم أي أداة Supabase ولم أعدّل أي ملف في المستودع.
- لم أستعن بـ knowledge-graph.json: كان التنقل عبر `grep` وقوائم الملفات كافيًا.
- جذر المصدر: `/home/user/halaqati-app/source/app/src/main` (الاختصار `J/` = `java/com/mohammedsamir/halaqati/`).

## الخلاصة

- لا يوجد بند Critical ولا High مؤكد في الكود.
- المؤكد: 6 بنود Medium و9 بنود Low (إجمالي 15).
- أهم ثلاثة:
  1. A-01: رابط `com.mohammedsamir.halaqati://auth/callback` مزوَّر بحقل `type=recovery` يتجاوز حماية PKCE، فيُدخل التطبيق في حساب المهاجم (تثبيت الجلسة).
  2. A-02: تسجيل الخروج لا يُبطل الجلسة على الخادم ولا يمسح بيانات الطلاب المخزنة محليًا.
  3. A-03 و A-04: بيانات الطلاب الحساسة (رقم الهوية والهاتف والعنوان وتاريخ الميلاد) في Room غير مشفّرة، وقاعدة قديمة `halaqati-cache.db` غير مستثناة من النسخ الاحتياطي ولا تُحذف.
- الأمان الفعلي لبيانات الأدوار يقع على RLS والـ RPC في الخادم، وليس على العميل. انظر قسم "غير مؤكد" وقسم "Supabase surface" للمطابقة مع سياسات قاعدة البيانات.

## جدول النتائج المؤكدة

| ID | الخطورة | الملف:السطر | الدليل (مقتطف) | الأثر | الإصلاح |
|---|---|---|---|---|---|
| A-01 | Medium | `J/data/SupabaseApi.kt:215` (+ `J/data/AuthCallbackRules.kt:164-171`, `AndroidManifest.xml` فلتر `com.mohammedsamir.halaqati` BROWSABLE بلا تحقق) | `if (callback.type.isBlank() && !sessionStore.takePkceFlag()) throw SupabaseException("رابط الدخول غير موثوق...")` ثم `userForToken(access)` ثم `sessionStore.save` | أي تطبيق أو صفحة ويب تفتح `<رابط callback مزوَّر — حُذفت التفاصيل>` المهاجم>&refresh_token=...&type=recovery` فيتخطى شرط PKCE (يكفي أن `type` غير فارغ). التحقق الوحيد أن التوكن صالح لدى Supabase، وتوكن المهاجم صالح. النتيجة: تثبيت جلسة/Login-CSRF. يُستبدل حساب المستخدم الحالي بحساب المهاجم، فيُدخل المحفّظ بيانات الطلاب في حساب يتحكم به المهاجم. | إبقاء تدفق PKCE وحده للاسترجاع، أو اشتراط علم محلي "استرجاع قيد الانتظار" يُضبط في `requestPasswordReset` مع مطابقة بريد المستخدم الذي ردّ عليه `/auth/v1/user`. وعدم استبدال جلسة قائمة دون تأكيد. |
| A-02 | Medium | `J/data/HalaqatiRepository.kt:175-181`, `J/ui/AppViewModel.kt:269-282`; `J/data/LocalCache.kt:25` | `fun signOut() { ... store.clear() }`، ولا يوجد استدعاء `/auth/v1/logout`. `clearOwner` معرّفة ولها 0 استدعاءات (grep) | الخروج يمسح التوكنات المحلية فقط. refresh token يبقى صالحًا على الخادم. وتبقى بيانات الطلاب في Room (`cache_entries` و`students_local` و`outbox`) وفي SharedPreferences (`halaqati_native_report_cache`, `halaqati_native_certificates`) بعد الخروج. جهاز مشترك أو مفقود يكشف بيانات الحساب السابق. العزل بين الحسابات بالمفتاح فقط، وليس بالحذف. | استدعاء `POST /auth/v1/logout` قبل المسح. حذف صفوف المالك (`cache_entries`, `students_local`) وتفريغ مفضّلات التقارير والشهادات عند الخروج. أو تحذير المستخدم وحذف الـ outbox المرفوع. |
| A-03 | Medium | `J/data/OfflineDatabase.kt:81-95,253`; `J/ui/screens/ReportsAndTools.kt:818` | `val nationalId: String? = null`، `val address`, `val guardianPhone`, `val guardianEmail`, `val dateOfBirth`; `Room.databaseBuilder(..., "halaqati-rc7.db").build()` بلا تشفير؛ `putString(key, raw)` حتى 3.8MB للتقارير | بيانات قاصرين (هوية وهاتف وعنوان وميلاد وبريد وصور data-URL) في SQLite عادية وXML عادي. تُقرأ على جهاز rooted أو بنسخة شرعية/forensic أو ADB. | تشفير DB (SQLCipher مع مفتاح Keystore) أو عدم تخزين `national_id` والعنوان محليًا. تقليص `rawJson`. نقل كاش التقارير إلى `EncryptedSharedPreferences` أو ملف مشفّر. |
| A-04 | Medium | `AndroidManifest.xml:6` (`allowBackup="true"`), `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `J/data/OfflineLegacyImporter.kt:25` | المستبعد فقط `halaqati-rc7.db*` و`halaqati-offline.db*`. المستورد القديم يفتح `halaqati-cache.db` (جدول `cache_entries` فيه `json_value`) ولا يوجد `delete` في الملف (grep: 0). `grep -c halaqati-cache` في ملفَّي XML = 0 | على الأجهزة المرقّاة من إصدار قديم، تبقى `halaqati-cache.db` (كاش JSON للطلاب) خارج الاستبعاد، فتدخل في Auto Backup السحابي ونقل الجهاز. | إضافة `halaqati-cache.db` و`-wal` و`-shm` للاستبعاد. حذف القواعد القديمة بعد الاستيراد الناجح. والأفضل `allowBackup=false` أو قائمة `<include>` بدل `<exclude>`. |
| A-05 | Medium | `J/data/HalaqatiRepository.kt:721-731`, `J/ui/AppViewModel.kt:91-100` | `selectOwnProfileRow(...) }.getOrElse { error -> cached ?: throw error }`؛ و`_auth.value = authStateFor(cached)` ثم `runCatching { repo.profileWithRefresh() }.onSuccess {...}` (الفشل مُتجاهَل) | أي خطأ، بما فيه 401 و403، يُرجع الملف المخزّن (الدور وحالة الاعتماد) كأنه ناجح. حساب عُطّل أو جُمّد أو خُفّضت صلاحيته في الخادم يبقى بواجهة الدور القديم وببيانات الطلاب المخزنة طالما تُرفض الطلبات. وعلى جهاز rooted يمكن تعديل JSON الملف لعرض واجهة admin (البيانات تبقى محمية بـ RLS). | التمييز بين خطأ الشبكة (يُسمح بالكاش) وخطأ 401/403 (يُفرض إعادة الدخول ومسح الكاش). ربط صلاحية الكاش بمدة قصوى (`maxAge`) للملف الشخصي. |
| A-06 | Medium | `J/ui/update/NativeAppUpdater.kt:18,74-81,149-153`; `AndroidManifest.xml:4` | `UPDATE_MANIFEST_URL = "https://syounis212004-sys.github.io/halaqati-web/version.json"`؛ `if (expected.isNullOrBlank()) return@withContext true`؛ `REQUEST_INSTALL_PACKAGES` | رابط الـ APK والـ sha256 يأتيان من الملف نفسه (جذر ثقة واحد: حساب GitHub شخصي). الـ sha اختياري، فغيابه يعني نجاح التحقق. الـ apkUrl لا يُقيَّد بمضيف. التنزيل إلى Downloads العامة. التخفيف: Android يرفض تحديثًا بتوقيع مختلف، لكن `openInBrowser` ورسالة التحديث الإجباري تدفعان لتثبيت يدوي. | رفض التحديث عند غياب `sha256`. تثبيت المضيف والمسار. مقارنة بصمة شهادة التوقيع المضمَّنة. التنزيل إلى مجلد خاص بالتطبيق. توقيع الـ manifest. |
| A-07 | Low | `J/documents/CertificateHtmlRenderer.kt:76,167,171`; المصدر `J/ui/screens/ReportsAndTools.kt:1550,2713` | `style="$variables"` (دون `hAttr`)، و`"device" -> "'${cssString(custom...)}',sans-serif"`، و`cssString` يهرّب `\` و`'` والأسطر فقط، لا `"` | حقل "اسم الخط" (`customFontName`، أقصى 80 حرفًا) يستطيع إغلاق سمة `style="` وحقن HTML/JS داخل WebView (JS مفعّل). الحقن ذاتي أو عبر JSON نمط مستورد. الخروج للشبكة محجوب (`blockNetworkLoads=true`)، فالأثر محدود. باقي الحقول (الاسم والملاحظات والمحتوى) مهرَّبة عبر `h()` وهذا سليم. | تمرير قيمة `font` عبر `hAttr()` وتقييد الاسم بقائمة بيضاء `[\w\s-]`. |
| A-08 | Low | `J/documents/HtmlDocumentPreview.kt:112-115,59`; `J/documents/VectorDocumentRenderer.kt:868-871,919-921` | `settings.javaScriptEnabled = true`, `settings.allowFileAccess = true`, `loadDataWithBaseURL("file:///android_asset/documents/", html, ...)` | تمكين غير لازم للوصول إلى الملفات مع JS وbase من نوع file://. التخفيف: `blockNetworkLoads=true` و`allowContentAccess=false` و`domStorageEnabled=false` و`MIXED_CONTENT_NEVER_ALLOW`. تشديد دفاعي. | `allowFileAccess=false` (أصول `file:///android_asset` تعمل بدونه). و`allowFileAccessFromFileURLs/UniversalAccessFromFileURLs=false` صراحة. |
| A-09 | Low | `J/data/HalaqatiRepository.kt:554,585`; `J/push/NotificationHelper.kt:60,87-90` | `title = "تحضير الغد · ${student.fullName}"` و`.setContentText(body)` دون `setVisibility` | أسماء طلاب قاصرين وتفاصيل الواجب تظهر على شاشة القفل وتمر عبر FCM. لا يوجد `setPublicVersion` ولا `VISIBILITY_PRIVATE`. | إشعار عام على القفل (`setVisibility(VISIBILITY_PRIVATE)` + `setPublicVersion`) أو عدم وضع الاسم الكامل في العنوان. |
| A-10 | Low | `J/data/SupabaseApi.kt:58-67`; `J/ui/AppViewModel.kt:156,174,190,212` | `.ifBlank { text }` (جسم الخطأ كاملًا)، و`"فشل تسجيل الدخول: ${e.message ...}"` | رسائل PostgREST/GoTrue الخام (`message/details/hint` وأسماء أعمدة وجداول وسياسات) تُعرض للمستخدم. تسريب معلومات عن المخطط. | ربط رموز الأخطاء برسائل عربية ثابتة وعدم عرض `text` الخام. |
| A-11 | Low | `J/ui/AppViewModel.kt:195-214` | `if (... !url.startsWith(SupabaseApi.APP_DEEP_LINK)) return; _auth.value = AuthState.Loading ... error("لم تصل جلسة Google من Supabase")` | رابط `com.mohammedsamir.halaqati://auth/callback` فارغ من أي تطبيق يحوّل الشاشة إلى `AuthState.Error` (شاشة الدخول) رغم وجود جلسة صالحة، حتى إعادة التشغيل. إزعاج. | عدم تغيير الحالة قبل التحقق من أن الرابط يحمل معاملات صالحة. |
| A-12 | Low | `J/ui/HalaqatiApp.kt:415,428-430,260` | `if (!guardianMode) composable("follow_up")`، و`route = "student/{studentId}"`، و`takeIf { it.length == 36 }` | مسارا `follow_up` و`student/{id}` مسجَّلان لكل دور غير ولي الأمر (بينهم OBSERVER وUNKNOWN)، لا حسب `RoleNavigationPolicy`. معالج `navigate("follow_up")` لا يتحقق من الدور. غير قابل للوصول خارجيًا: `DeepLinkRules` يمرّ عبر `AppDestination.fromRoute` + `canOpen`، ومعرّف الطالب في رابط ولي الأمر يُستخدم للترتيب فقط (`ManagementScreens.kt:2402`). ورابط `chat:` يتحقق من الطول (36) لا من صيغة UUID. تشديد دفاعي. | تسجيل المسارين فقط لمن يملك `Students`، وفحص `canOpen` في كل معالج، والتحقق من صيغة UUID. |
| A-13 | Low | `source/.gitignore`; `source/app/build.gradle.kts:29-30` | محتوى `.gitignore` = `__pycache__/` و`*.pyc` فقط | لا يمنع رفع `google-services.json` و`local.properties` و`*.jks`. لم أجد أسرارًا فعلية (انظر "نظيف" أدناه). المفتاح `<publishable-key>` والـ URL مضمَّنان كقيمة افتراضية، وهذا عام بطبيعته. | إضافة تلك الأنماط للـ ignore، وإعداد secret-scanning. |
| A-14 | Low (معلوماتي) | `AndroidManifest.xml` (لا `networkSecurityConfig`)، وغياب `CertificatePinner` أو `TrustManager` (grep: 0) | لا pinning. `targetSdk 36` يمنع cleartext افتراضيًا ولا يوجد `usesCleartextTraffic` | الاتصال TLS قياسي بلا pinning (مقبول لمعظم التطبيقات). لا cleartext. | اختياري: `network_security_config` مع pin لـ `*.supabase.co`. |
| A-15 | Low | `J/documents/VectorDocumentRenderer.kt:651-662`; `J/ui/screens/SettingsParitySections.kt:302-311`; `J/ui/screens/ReportsAndTools.kt:4853` | `Environment.DIRECTORY_DOWNLOADS ... "/Halaqati"`، و`CreateDocument("application/zip")`، و`if (profile.role == ...ADMIN) item { BackupSettingsCard() }` | تقارير PDF/Excel/نسخة كاملة (طلاب وهويات وأولياء أمور) بلا تشفير في Downloads العامة، بقرار المستخدم. تقييد النسخة الكاملة بـ ADMIN في الواجهة فقط، و`exportBackup` نفسها بلا فحص دور (يعتمد على RLS). | خيار حماية الأرشيف بكلمة مرور، وتحذير عند الحفظ، وفحص الدور داخل الـ repository. |

### نظيف (تحققت ولا مشكلة)

- **أسرار**: لا `service_role` ولا JWT ولا مفاتيح Firebase ولا keystore ولا كلمات مرور توقيع في `source/` أو `build.gradle.kts` أو `gradle.properties` أو res/assets. (فحص regex على كل الملفات المتتبَّعة ما عدا `.zip` والصور والخطوط والملفات الثنائية. ولا توجد في أي zip ملفات keystore أو google-services أو local.properties أو apk، بحسب `unzip -l` على RC89 وRC99.) التوقيع عبر `conf("HALAQATI_*")` من البيئة وأسرار GitHub (`.github/workflows/Halaqati_RC27_14.yml:243-249`). `service_role` يظهر فقط في `halaqati-backup.yml` كسر Actions وفي migrations SQL.
- **Manifest**: لا `debuggable` ولا `testOnly`. مكوّن مُصدَّر واحد هو `MainActivity` (launcher + فلترا VIEW)، و`HalaqatiMessagingService` بـ `exported="false"`. لا Provider ولا Receiver ولا FileProvider في مانيفست التطبيق.
- **الجلسة**: توكنات Supabase في `EncryptedSharedPreferences` (`J/data/SecureSessionStore.kt:9-15`) ومستبعدة من النسخ الاحتياطي. PKCE للتدفق الموثوق (verifier من `SecureRandom` وأحادي الاستخدام).
- **Deep links الأدوار**: `DeepLinkRules.navigation` يقبل UUID أو مسارات بيضاء، و`canOpen(role, dest)` يُفحص قبل `nav.navigate` (`HalaqatiApp.kt:266-271`)، والرسم البياني للتنقل يُبنى لكل دور من `RoleNavigationPolicy.destinations` (`HalaqatiApp.kt:190-191`). لا IDOR عبر رابط عميق من العميل.
- **Excel/Word**: الخلايا `t="inlineStr"` (لا حقن صيغ، `NativeReportExport.kt:128`)، وWord مهرَّب بـ `html()`.
- **HTML التقارير**: كل الحقول تمرّ عبر `h()` (`ReportHtmlRenderer.kt:170,454`). الألوان بتحقق regex (`CertificateHtmlRenderer.kt:120`).
- **السجلات**: لا `Log` لبيانات شخصية أو توكنات. `PerfLog` في الذاكرة فقط وبلا PII.
- **حقن فلاتر PostgREST**: من 156 موضع استيفاء مع `eq./in./gte./lte.` ما راجعته (HalaqatiRepository وواجهات Org/Supervisor/Management) معرّفات UUID أو تواريخ أو قيم تعداد. لم أجد نصًا حرًا من المستخدم في أي فلتر، ولا `ilike` ولا `like` ولا `or=` بمدخل مستخدم. عيّنة مباشرة لا استقصاء كامل (انظر "غير مؤكد" S8).
- **سكربتات الخصوصية** (شُغّلت بـ `python3 -I`، قراءة فقط): `scripts/auth-push-privacy-check.py` و`local-sensitive-account-scope-check.py` و`offline-account-scope-check.py` و`google-auth-flow-check.py` كلها نجحت على الكود الحالي. المخرجات: `Auth/push/privacy check passed`، و`Local sensitive-data account scope check passed`، و`Offline account-scope check passed`، و`Google auth flow contract: PASS`.
  - هي فحوص وجود نصوص (token-presence grep) وليست فحوص سلوك. لهذا نجحت رغم A-01 (وهي توصّف "strict recovery callback")، ورغم A-02 (لا فحص لتنظيف الخروج)، ورغم A-04 (تفحص وجود `halaqati-offline.db` ولا تتحقق من `halaqati-cache.db`).
  - `local-sensitive-account-scope-check.py` يعزل بالمفتاح فقط، ولا يضمن حذف البيانات ولا تشفيرها.

## غير مؤكد (مشتبه به، لم أتحقق منه)

| ID | الاشتباه | لماذا لم يُؤكَّد / ما المطلوب |
|---|---|---|
| S1 | **تصعيد صلاحيات عبر `PATCH profiles` المباشر**: العميل يعتمد الحسابات ويغيّر الدور ويعطّل ويرفض عبر PATCH مباشر على `profiles` (`HalaqatiRepository.kt:2932-2950` `approveAccount`، و`3279-3282` `setAccountActive`، و`3284-3295` `changeAccountRole`، و`3270-3277` رفض الحساب، و`368` `frozen`). | إن سمحت سياسة UPDATE على `profiles` للمستخدم بتعديل `role`/`approval_status`/`active`/`frozen`/`is_platform_owner`/`approved_by` لصفه، فالتصعيد الذاتي ممكن. الفحص الوحيد (`ui/ ... ADMIN`) في الواجهة. يتطلب مراجعة RLS والـ triggers على `profiles`. |
| S2 | `signUp` يرسل `requested_role` في `user_metadata` (`SupabaseApi.kt:116`)، و`rc_complete_signup(p_role)` (`HalaqatiRepository.kt:698`). | يجب أن يحصر الخادم القيم (`guardian/teacher/supervisor/observer`) ولا يثق بالـ metadata لمنح `admin`. الواجهة تعرض 4 أدوار فقط. |
| S3 | **IDOR في RPC بمعرّفات من العميل**: `guardian_child_overview` و`guardian_child_photo` و`guardian_student_dashboard_range` و`guardian_competition_board_range` و`rc42_observer_student_detail(p_student)` و`rc40_observer_breakdown` و`rc54_thread_get_by_id`. | العميل يمرّر معرّف الطالب أو المحادثة. يجب أن يتحقق الخادم من الربط (guardian↔student، donor↔awarded student). |
| S4 | **ربط ولي الأمر بالطالب**: `rc_request_guardian_link(p_national_id, p_birth_date)` و`rc_complete_signup` يعتمدان على رقم هوية + تاريخ ميلاد (العميل يشترط 5 أرقام فقط، `AuthScreens.kt:519`). | قوة الإثبات ومعدّل المحاولات (brute-force وتعداد طلاب) يحددهما الخادم. غير مفحوص. |
| S5 | **Edge Functions `halaqati-notify` و`halaqati-chat-push`**: العميل يرسل `student_id` وعنوان الإشعار ونصه و`scope/audience/target_id` (`HalaqatiRepository.kt:389-402,300-318`)، وتُولَّد تنبيهات الخطة الذكية من جهاز المحفّظ (`:540-590`). | يجب أن يتحقق الخادم أن المستدعي مخوَّل لذلك الطالب أو الحلقة، وأن يضع حدًا للمعدل (تصيّد وإزعاج لأولياء الأمور). الكود غير موجود في هذا النطاق (الموجود `migration/legacy-web-v2.44.0/.../halaqati-notify/index.ts` قديم). |
| S6 | عرض اسم الطالب وصورته وهاتف ولي الأمر للمانح (`DonorScreen.kt:472-490`، `rc46_donor_awards`، `rc83_donor_award_photos`، `rc42_observer_student_detail`). | يوجد مفتاح موافقة `rc46_set_donor_contact`. صحة التطبيق على الخادم غير مفحوصة. |
| S7 | مفتاح تشفير `EncryptedSharedPreferences` (`__androidx_security_crypto_encrypted_prefs__`) غير مستثنى من النسخ الاحتياطي بينما `halaqati_secure.xml` مستثنى. | معروف أن استعادة المفتاح على جهاز آخر قد تسبب تعطل `EncryptedSharedPreferences.create` عند الإقلاع. لم أتحقق وقت التشغيل. |
| S8 | حقن فلاتر PostgREST: `sessionTypeQuery` يضع `else -> "&sessions.session_type=eq.$sessionType"` بلا تحقق (`HalaqatiRepository.kt:6533`)، و`SupabaseApi.patch/delete/upsert` تلصق `filter`/`onConflict` بلا ترميز (`:329-343,317-318`). | المراجعة عيّنة (156 موضعًا). القيم في كل ما راجعت من مصدر داخلي (تعداد/UUID). يلزم استقصاء كامل لكل الاستدعاءات للتأكد أن لا نصًا حرًا يصل لها. الأثر المحتمل محدود بـ RLS للمستخدم نفسه. |
| S9 | مكوّنات مُصدَّرة من مكتبات (Firebase وWorkManager وCredentials) في الـ merged manifest. | لا يوجد merged manifest في المستودع. |
| S10 | `rc_pending_requests` يعيد بريد وهاتف طالبي الحساب (`HalaqatiRepository.kt:~746-760`). | تقييده بالدور (admin/supervisor/teacher حسب المرحلة) على الخادم. |

## لم أتحقق منه

- سياسات RLS، وتعريفات الـ RPC (`SECURITY DEFINER`/`search_path`)، وصلاحيات `anon` على الدوال. هي في `source/migration/*.sql` وخارج نطاق هذا التدقيق. ملف `RC91_revoke_anon_definer_execute.sql` يوحي بمعالجة سابقة ولم أقرأه.
- سلوك وقت التشغيل: لم أبنِ ولم أشغّل التطبيق. الحكم على A-01 وA-05 وA-07 من قراءة الكود، دون تجربة.
- Firebase: ملف `google-services.json` غير موجود في المستودع (يُحقن في CI). لا فحص لقواعد مفاتيح API ولا لمعرّف عميل Google.
- منطق CI خارج استخراج الأسرار (`Halaqati_RC27_14.yml`, `halaqati-backup.yml`). `scripts/backup-supabase.py` تستخدم `service_role` في Actions. لم أقيّم حماية تشفير النسخة (`BACKUP_PASSPHRASE`).
- حجم تأثير `proguard-rules.pro` وتعتيم الكود. وفحص التبعيات وإصداراتها (ثغرات CVE).
- محتوى `docs/` و`design/` و`benchmark/` وملفات RC*.md (توثيق).
- الاختبارات الوحدوية للجوانب الأمنية (`app/src/test`).

## Supabase surface

مصدره الكود (`SupabaseApi.kt` و`HalaqatiRepository.kt` وملفات الواجهات التي تستدعي المستودع). الأسماء كما وردت حرفيًا.

### Tables (REST `/rest/v1/<table>`)

```
announcements
app_settings
app_support_info
associations
attendance_records
centers
certificate_revisions
certificate_series
certificate_templates
certificates
competition_seasons
data_import_jobs
deleted_session_snapshots
exam_examiners
exam_settings
forum_comments
forum_posts
halaqa_supervisors
halaqa_teachers
halaqas
monthly_comments
notification_preferences
notifications
profiles
rc15_tathbit_consistency_issues
recitation_entries
sessions
smart_plan_events
smart_plan_month_archives
smart_plan_phase_states
smart_plan_phase_templates
smart_plan_phases
smart_plans
student_categories
student_daily_notes
student_exams
student_goals
student_guardians
student_halaqa_memberships
student_points
students
supervisor_visits
```

جداول إضافية تُقرأ فقط في النسخة الاحتياطية الكاملة للمدير (`BackupRules.kt:31-40`):

```
smart_plan_health_daily
achievement_badge_definitions
student_badge_awards
management_audit_log
```

(تُضاف إلى القائمة أعلاه جداول عدة بالفعل داخل `BackupRules.TABLES`: profiles وhalaqas وstudents وsessions وغيرها.)

### RPC (REST `/rest/v1/rpc/<name>`)

```
admin_delete_account
delete_halaqa_cascade
guardian_child_overview
guardian_child_photo
guardian_children
guardian_competition_board_range
guardian_student_dashboard_range
list_guardian_accounts
rc112_capabilities
rc112_reconcile_plan_phase_states
rc112_transition_plan_students
rc112_update_plan_phase
rc113_bulk_configure_plan_cycles
rc113_capabilities
rc121_issue_certificate
rc152_roll_tathbit_months
rc15_create_smart_plan_bundle
rc171_latest_activity_anchor
rc182_refresh_student_badges_exact
rc182_set_session_archived
rc182_update_session_details
rc19_mark_plan_health_notified
rc19_record_plan_health
rc19_start_month
rc23_get_or_create_session
rc24_create_certificate_revision
rc24_import_legacy_certificate
rc24_issue_certificate
rc24_void_certificate
rc25_restore_deleted_session
rc27_cleanup_notifications
rc32_observer_overview
rc32_observer_requests
rc32_observer_resolve
rc32_observer_submit_request
rc40_observer_breakdown
rc42_observer_student_detail
rc46_admin_awards
rc46_approve_awards
rc46_award_months
rc46_compute_awards
rc46_donor_awards
rc46_exclude_award
rc46_set_donor_contact
rc48_set_donor_halaqas
rc48_support_cancel
rc48_support_confirm
rc48_support_pledge
rc49_donor_impact
rc49_donor_report
rc49_donor_suggest
rc49_news_save
rc49_support_deliver
rc49_support_proof
rc49_support_save_card
rc49_support_set_defaults
rc49_support_submit_transfer
rc52_reward_option_delete
rc52_reward_option_save
rc52_reward_proof
rc52_reward_request
rc52_set_settings
rc52_thread_get
rc53_alert_staff
rc53_broadcast_special
rc53_staff_overview
rc54_chat_overview
rc54_thread_get_by_id
rc54_thread_open
rc65_exam_students
rc66_my_subscription
rc83_donor_award_photos
rc_broadcast_detail
rc_broadcast_stats
rc_complete_signup
rc_my_signup_status
rc_pending_requests
rc_request_guardian_link
rc_review_request
set_student_primary_guardian
student_plan_month_history
```

### Edge Functions (`/functions/v1/<name>`)

```
halaqati-notify
halaqati-chat-push
```

أفعال `halaqati-notify` (حقل `action` في الجسم): `register_device`, `unregister_device`, `test_push`, `manual`, `manual_preview`, `event_student`, `push_approval_events`.

### Storage buckets

```
(لا يوجد: لا استدعاء Storage API في الكود)
```

الصور (`students.photo_path` و`profiles.avatar_path`) تُخزَّن كـ data-URL داخل أعمدة الجداول، والاسم `profile-avatars` يرد في تعليق فقط (`J/model/Models.kt:32`).

### نقاط Auth المستخدمة (GoTrue، `/auth/v1/...`)

```
token?grant_type=password
token?grant_type=id_token
token?grant_type=refresh_token
token?grant_type=pkce
signup
authorize?provider=google
recover
user   (GET للتحقق، وPUT لتغيير كلمة المرور)
```

لا يوجد `/auth/v1/logout` (انظر A-02).

### علاقات embed مستخدمة في الاستعلامات

تظهر في فلاتر مثل `sessions.status=neq.cancelled` و`sessions.halaqa_id=eq.` و`sessions.session_type=...` و`students.track_code` (استعلامات `recitation_entries` و`attendance_records` تضمّ `sessions` و`students`). يجب أن تتوافق RLS مع الجداول المتضمَّنة.

## أولويات المطابقة مع قاعدة البيانات (للمدقق التالي)

1. سياسات UPDATE/INSERT على `profiles`: أعمدة `role` و`approval_status` و`active` و`frozen` و`is_platform_owner` و`approved_by` و`onboarded` (S1، S2).
2. تحقق الربط داخل: `guardian_child_overview` و`guardian_child_photo` و`guardian_student_dashboard_range` و`rc42_observer_student_detail` و`rc54_thread_get_by_id` و`rc_request_guardian_link` (S3، S4).
3. فحص `halaqati-notify` للمستدعي والهدف والمعدل (S5).
4. `GRANT EXECUTE` لـ `anon` على كل دوال القائمة أعلاه، خصوصًا `admin_delete_account` و`delete_halaqa_cascade` و`rc_review_request` و`rc46_*` و`rc53_*` (راجع `RC91_revoke_anon_definer_execute.sql`).
