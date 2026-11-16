-- RC27.18 — فحص اختياري (قراءة فقط): هل بقي أي بيانات يتيمة على السيرفر بعد حذف الجلسات؟
-- شغّله في Supabase SQL Editor. لا يغيّر أي شيء.

-- 1) عدد الجلسات لكل حلقة وحالتها (الجلسات المحذوفة لا تظهر هنا)
select h.name as halaqa, s.status, count(*) as sessions
from public.sessions s join public.halaqas h on h.id = s.halaqa_id
group by h.name, s.status order by h.name, s.status;

-- 2) سجلات حضور/تسميع/ملاحظات بلا جلسة (يتيمة) — يجب أن تكون كلها 0
select 'attendance_records' as tbl, count(*) as orphans
from public.attendance_records a where not exists (select 1 from public.sessions s where s.id = a.session_id)
union all
select 'recitation_entries', count(*)
from public.recitation_entries r where not exists (select 1 from public.sessions s where s.id = r.session_id)
union all
select 'student_daily_notes', count(*)
from public.student_daily_notes n where not exists (select 1 from public.sessions s where s.id = n.session_id);

-- 3) الجلسات المحذوفة المحفوظة كنسخة احتياطية (للاستعادة من التطبيق عند الحاجة)
select session_id, deleted_at,
       jsonb_array_length(attendance_records) as attendance,
       jsonb_array_length(recitation_entries) as recitations
from public.deleted_session_snapshots order by deleted_at desc limit 20;
