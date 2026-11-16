# POWER RC8 — Quran Core + Recitation Engine

## النطاق
RC8 يبني قلبًا قرآنيًا واحدًا فوق RC7 Offline Foundation دون الرجوع إلى أي نسخة أقدم. المساران الوحيدان للطالب يظلان `general_hifz` (الحفظ العام) و`tathbit` (التثبيت). `sard` نشاط تسميع وليس مسارًا ثالثًا.

## مصدر المصحف والحساب
- ترتيب المصحف: مصحف المدينة، 604 صفحات، 15 سطرًا في الصفحة.
- مصدر توزيع الآيات/الأسطر: `MohamadHajjRabee/quran-qcf4` — QCF4 Hafs / Madinah Mushaf 1441 AH.
- المصدر مثبت على commit: `5130511027e769f0a8f4eeb7f00f46bde3788d60`.
- `verses.json` مثبت على Git blob SHA-1: `568f4820cefe142ccd3f75b89fa99b1a2ad01fe9`.
- الأصل المحلي `qcf4-layout-v2.bin` موجود داخل الحزمة: ثلاثة بايت لكل آية (page + firstLine + lastLine) لجميع 6236 آية، ويتحقق `scripts/generate-qcf4-layout.py` من سلامته قبل البناء.
- البناء العادي لا ينزّل بيانات القرآن من الشبكة؛ إذا فُقد الأصل أو تلف يفشل فورًا بدل التعليق. إعادة التوليد الشبكية متاحة فقط للصيانة عبر `HALAQATI_ALLOW_QCF4_FETCH=1`.
- لا يعتمد Runtime على الشبكة لحساب الصفحات بعد تضمين الأصل في APK.
- لا تُضمّن خطوط QCF4 في RC8؛ البيانات JSON المستخدمة للحساب مرخصة MIT، وإشعار الطرف الثالث موجود في `migration/THIRD_PARTY_NOTICES_RC8.md`.

## QuranRangeEngine
الملف `quran/QuranRangeEngine.kt` هو المصدر الوحيد لمعنى النطاق القرآني الجديد:

### الاتجاه
`QuranTraversalDirection`:
- `FORWARD_QURAN`
- `REVERSE_SURAH_ORDER`
- `AUTO_BY_ACTIVITY`

قواعد `AUTO_BY_ACTIVITY`:
- الحفظ العام (`general_hifz` + `new_hifz/hifz`): بين السور من الرقم الأعلى إلى الأقل، لكن داخل كل سورة الآيات تصاعدية دائمًا.
- التثبيت/السرد/المراجعة/التلاوة: يحافظ المحرك على المعنى الذي أدخله المستخدم؛ يسمح بالصعود والنزول ولا يقلب البداية والنهاية قبل فهم السياق.
- اختيار صفحات كاملة هو اختيار مباشر؛ في `AUTO` يحافظ على ترتيب start→end كما أدخله المستخدم، لأن اتجاه رقم الصفحة وحده لا يكفي لاستنتاج اتجاه الحفظ داخل السورة.

### الحساب
- Ayah range: يحول النطاق إلى Segments صريحة ثم يجمع مفاتيح QCF4 الفعلية `page:line`.
- Cross-surah: يجمع بقية السورة الأولى + السور الوسطية + الجزء المطلوب من السورة الأخيرة.
- Partial page: `exactPages = uniqueQcfLines / 15.0`، ولا تُقرب القيمة الداخلية.
- Full page selection: كل صفحة مختارة = 15 سطرًا كاملة.
- Surahs/Juz: يسمح بقائمة متعددة، ويحافظ على الترتيب المسموح به حسب المسار/النشاط.
- General hifz Juz: يعكس ترتيب السور داخل الجزء، مع إبقاء آيات كل سورة تصاعدية.

مثال الحفظ العام: `الشورى 27 → فصلت 18` يتحول إلى مقطعين: `الشورى 27→53` ثم `فصلت 1→18`، وليس فرقًا عدديًا بين صفحتين.

## محرر التسميع
`QuranRangePickerDialog` يدعم:
- سورة وآية
- صفحات
- سور متعددة
- أجزاء متعددة

ويعرض قبل الحفظ:
- 📖 النطاق
- 📄 عدد الصفحات الدقيق
- 🔢 عدد الآيات/السور
- ↕ اتجاه traversal المحسوب

حتى الإدخال اليدوي في حقلي "من صفحة / إلى صفحة" يمر عبر `QuranRangeEngine` قبل الحفظ؛ لا يوجد مسار يدوي يتجاوز المحرك.

## التخزين وSupabase
تمت مراجعة schema الحية لـ `public.recitation_entries`. الحقول اللازمة موجودة أصلًا، لذلك RC8 لا ينفذ DDL ولا migration على قاعدة الإنتاج. يحفظ RC8، حسب نوع الاختيار:
- `start_surah`, `start_ayah`, `end_surah`, `end_ayah`
- `start_page`, `end_page`
- `pages_count` بالقيمة الدقيقة
- `ayahs_count`, `surahs_count`
- `selected_surahs`
- `selection_data.qcf_line_keys`
- `selection_data.quran_traversal_direction`
- `selection_data.quran_segments`
- `selection_data.selected_juz`
- `selection_data.line_model = 15`
- `selection_data.quran_engine = rc8`

كل هذا يبقى فوق Room/Outbox/WorkManager في RC7، أي أن حفظ التسميع يظل Local-first ثم يتزامن مع Supabase.

## Smart Plan وQuick Memorization
- Quick Memorization يستفيد من `qcfLineKeys` ويحسب الصفحات الدقيقة منها عند الحفظ.
- SmartPlanEngine يفضل QCF line keys على page fallback.
- fallback الخاص بالسجلات القديمة يحافظ على ترتيب `start→end` ولا يعيد ترتيب التسميع تلقائيًا.
- إعادة بناء Smart Plan POWER نفسها (لماذا هذا المقرر/التعويض/Student Override...) تبقى RC9 حسب الخطة، ولا يتم خلطها داخل RC8.

## بوابات RC8
- `scripts/kotlin-selftest/QuranRangeEngineSelfTest.kt`
- `app/src/test/.../QuranRangeEngineTest.kt`
- `scripts/rc8-integration-check.py`
- جميع بوابات RC7 / parity السابقة تبقى إلزامية.

الحالات المغطاة تشمل:
- صفحة جزئية QCF4.
- الحفظ العام عبر سور بترتيب 114→1 مع آيات صاعدة داخل السورة.
- سرد نازل داخل السورة بدون قلب الإدخال.
- صفحات كاملة تنازلية.
- صفحات عامة صاعدة في Page Mode بدون فرض اتجاه خاطئ من رقم الصفحة فقط.
- الجزء 30 في الحفظ العام يبدأ من السور الأعلى رقمًا.

## Gate للانتقال إلى RC9
لا يبدأ RC9 إلا إذا:
1. بوابات Kotlin والـstatic contracts كلها خضراء.
2. QCF4 asset يتحقق من commit/blob SHA المثبتين.
3. Gradle compile + unit tests + lint + assemble تنجح في GitHub Actions.
4. لا يحدث Regression في RC7 Offline/Outbox/Room/parity.
