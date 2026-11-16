# Halaqati v2.46.0 — POWER RC5

هذه الدفعة تبني فوق RC4 العامل وتغيّر معيار القبول من وجود Route/Screen إلى عقود سلوكية فعلية مستمدة من 2.44.

## ما تم إصلاحه
- جلسة اليوم تعرض التسميع المحفوظ فورًا، بما فيه النطاق والصفحات والتقييم والأخطاء والتلقين والسلامة، حتى قبل مزامنة Offline Queue.
- Dashboard يعتمد session_date بدل created_at ويضم التسميعات المحلية غير المرفوعة.
- تقييم V6 مصدر واحد محسوب من الصفحات والأخطاء والتلقين.
- بطاقات Smart Plan تعرض المطلوب والمنجز والمتبقي والتقدم والأسبوع وآخر تسميع والمقرر القادم وسبب المقرر والتعويض/التقدم.
- التحفيظ السريع أصبح Workflow Native كاملًا: طالب واحد، مقرر ذكي، حضور/غياب، أخطاء/تلقين، حفظ ثم التالي.
- الشهادات تستخدم Renderer واحدًا للمعاينة وPDF، مع Zoom وتخصيص ألوان/خطوط/شعارات/موضع الرقم ومقاسات 2.44.
- Report Studio لديه معاينة مباشرة ويتحكم بالفترة والمسار والحلقة ونوع اللقاء والاتجاه والخط والألوان والشعارات والأعمدة، مع PDF/Excel/Word/PNG/Share.
- بطاقات الطلاب تستعيد سياق الخطة وإحصائيات الشهر (حفظ/تثبيت/مراجعة/غياب) مع تحميل أساسي سريع ثم إثراء بالخلفية.
- تحسين التباين وإزالة لون النص الثانوي الباهت، وإضافة stable key/contentType للقوائم الثقيلة.
- المحافظة على Cairo، RTL، مسارين فقط: الحفظ العام والتثبيت، وminSdk 23.

## Gates
- behavioral-parity-check.py: 34/34
- full-release-parity-check.py: 48/48 (هيكلي، وليس بديلًا عن السلوك)
- release-parity-contract-check.py: 9/9
- UI/UX Pro Max
- Google auth flow
- offline/account-scope
- auth/push/privacy
- Cairo/manifest/navigation/compiler source checks
- Kotlin self-tests

> Gradle/Android compilation النهائي يبقى مسؤولية GitHub Actions؛ نجاح فحوصات المصدر ليس بديلًا عن compile/lint/assemble.
