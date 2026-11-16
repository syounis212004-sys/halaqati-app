-- Halaqati Native v2.63.42 RC27.42 — multi-level account approval
--
--   * Guardian: signs up with the student's national id + date of birth. The student's halaqa teacher approves
--     first, then the system admin confirms. (No teacher for the student -> goes straight to the admin.)
--   * Teacher: approved by a halaqa supervisor OR the admin.
--   * Supervisor: approved by the admin only.
--
-- Safe to run twice. Run it in the Supabase SQL editor (one go). It does not touch existing accounts:
-- every account that exists today is marked "onboarded" and keeps its current approval state.
-- The app (2.63.42) also works before this script runs: it just keeps the old sign-in flow until the script is applied.

begin;

-- 1) New profile column. Existing accounts count as onboarded (done BEFORE the protection trigger is replaced).
do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'profiles' and column_name = 'onboarded'
  ) then
    alter table public.profiles add column onboarded boolean not null default false;
    update public.profiles set onboarded = true;
  end if;
end $$;

-- 2) The security-fields trigger now also guards "onboarded" and lets the review functions below through
--    (they raise a transaction-local flag; REST clients cannot set it).
create or replace function public.protect_profile_security_fields()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if coalesce(auth.role(),'') <> 'service_role'
     and not public.is_admin()
     and coalesce(current_setting('halaqati.review_ok', true), '') <> '1' then
    if new.role is distinct from old.role
       or new.active is distinct from old.active
       or new.approval_status is distinct from old.approval_status
       or new.approved_at is distinct from old.approved_at
       or new.approved_by is distinct from old.approved_by
       or new.rejection_reason is distinct from old.rejection_reason
       or new.onboarded is distinct from old.onboarded then
      raise exception 'هذه الحقول لا يغيرها إلا مسؤول النظام';
    end if;
  end if;
  new.updated_at := now();
  return new;
end $function$;

-- 3) Guardian <-> student link requests (first child at sign-up, and any later child).
create table if not exists public.guardian_link_requests (
  id uuid primary key default gen_random_uuid(),
  guardian_id uuid not null references public.profiles(id) on delete cascade,
  student_id uuid not null references public.students(id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending', 'teacher_approved', 'approved', 'rejected')),
  teacher_id uuid references public.profiles(id) on delete set null,
  teacher_at timestamptz,
  admin_id uuid references public.profiles(id) on delete set null,
  admin_at timestamptz,
  reason text,
  created_at timestamptz not null default now(),
  unique (guardian_id, student_id)
);
create index if not exists guardian_link_requests_status_idx on public.guardian_link_requests(status, created_at);
alter table public.guardian_link_requests enable row level security;
revoke all on public.guardian_link_requests from anon, authenticated;
grant select on public.guardian_link_requests to authenticated;
drop policy if exists rc42_link_requests_read on public.guardian_link_requests;
create policy rc42_link_requests_read on public.guardian_link_requests
  for select to authenticated using (guardian_id = (select auth.uid()) or public.is_admin());

-- 4) Failed national-id attempts (rate limit: 5 failures per hour per account).
create table if not exists public.rc_link_attempts (
  id bigserial primary key,
  user_id uuid not null,
  ok boolean not null,
  created_at timestamptz not null default now()
);
create index if not exists rc_link_attempts_user_idx on public.rc_link_attempts(user_id, created_at desc);
alter table public.rc_link_attempts enable row level security;
revoke all on public.rc_link_attempts from anon, authenticated;

-- 5) Helpers
create or replace function public.rc_is_student_teacher(p_user uuid, p_student uuid)
returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.students s
    where s.id = p_student
      and (
        s.primary_teacher_id = p_user
        or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id = s.halaqa_id and ht.teacher_id = p_user)
      )
  )
$$;

create or replace function public.rc_student_has_teacher(p_student uuid)
returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.students s
    where s.id = p_student
      and (
        s.primary_teacher_id is not null
        or exists (
          select 1 from public.halaqa_teachers ht
          join public.profiles tp on tp.id = ht.teacher_id and tp.approval_status = 'approved' and tp.active
          where ht.halaqa_id = s.halaqa_id
        )
      )
  )
$$;

create or replace function public.rc_approval_notify(p_to uuid, p_title text, p_body text, p_key text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  insert into public.notifications (recipient_id, kind, title, body, source_key, data)
  values (p_to, 'announcement', p_title, p_body, p_key, '{}'::jsonb)
  on conflict (recipient_id, source_key) do nothing;
exception when others then
  null; -- a notification problem must never block an approval
end $$;

create or replace function public.rc_audit(p_table text, p_id text, p_action text, p_data jsonb)
returns void
language plpgsql security definer set search_path = public as $$
begin
  insert into public.management_audit_log (actor_id, entity_table, entity_id, action, after_data)
  values (auth.uid(), p_table, p_id, p_action, p_data);
exception when others then
  null;
end $$;

-- Shared by sign-up and "add another child": checks national id + birth date, rate-limits, creates the request.
create or replace function public.rc_create_link_request(p_guardian uuid, p_national_id text, p_birth_date date)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_nid text;
  v_sid uuid;
  v_fails integer;
  v_status text;
  v_name text;
  t record;
begin
  v_nid := regexp_replace(translate(coalesce(p_national_id, ''), '٠١٢٣٤٥٦٧٨٩', '0123456789'), '\s', '', 'g');
  if v_nid !~ '^[0-9]{5,20}$' then
    return jsonb_build_object('ok', false, 'error', 'رقم هوية الطالب غير صالح');
  end if;
  if p_birth_date is null then
    return jsonb_build_object('ok', false, 'error', 'تاريخ ميلاد الطالب مطلوب');
  end if;
  select count(*) into v_fails from public.rc_link_attempts
  where user_id = p_guardian and not ok and created_at > now() - interval '1 hour';
  if v_fails >= 5 then
    return jsonb_build_object('ok', false, 'error', 'محاولات كثيرة غير صحيحة. حاول بعد ساعة أو تواصل مع الإدارة');
  end if;

  select s.id, s.full_name into v_sid, v_name
  from public.students s
  where regexp_replace(translate(coalesce(s.national_id, ''), '٠١٢٣٤٥٦٧٨٩', '0123456789'), '\s', '', 'g') = v_nid
    and s.date_of_birth = p_birth_date
    and s.status = 'active'
  limit 1;

  if v_sid is null then
    insert into public.rc_link_attempts(user_id, ok) values (p_guardian, false);
    return jsonb_build_object('ok', false, 'error', 'البيانات غير مطابقة لأي طالب مسجّل');
  end if;
  insert into public.rc_link_attempts(user_id, ok) values (p_guardian, true);

  if exists (select 1 from public.student_guardians where student_id = v_sid and guardian_id = p_guardian) then
    return jsonb_build_object('ok', false, 'error', 'هذا الطالب مرتبط بحسابك مسبقًا');
  end if;

  v_status := case when public.rc_student_has_teacher(v_sid) then 'pending' else 'teacher_approved' end;
  insert into public.guardian_link_requests (guardian_id, student_id, status)
  values (p_guardian, v_sid, v_status)
  on conflict (guardian_id, student_id) do update
    set status = excluded.status, reason = null, teacher_id = null, teacher_at = null, admin_id = null, admin_at = null,
        created_at = now()
    where public.guardian_link_requests.status = 'rejected';

  -- tell the people who must act next
  if v_status = 'pending' then
    for t in
      select distinct u.id from (
        select s.primary_teacher_id as id from public.students s where s.id = v_sid and s.primary_teacher_id is not null
        union
        select ht.teacher_id from public.students s join public.halaqa_teachers ht on ht.halaqa_id = s.halaqa_id where s.id = v_sid
      ) u
    loop
      perform public.rc_approval_notify(t.id, 'طلب ربط ولي أمر',
        'طلب ولي أمر جديد لربط حسابه بالطالب ' || v_name || '. راجعه من «طلبات الاعتماد».',
        'link-req:' || p_guardian::text || ':' || v_sid::text || ':t:' || to_char(now(), 'YYYYMMDDHH24MISS'));
    end loop;
  else
    for t in select id from public.profiles where role = 'admin' and approval_status = 'approved' and active loop
      perform public.rc_approval_notify(t.id, 'طلب ربط ولي أمر',
        'طلب ولي أمر جديد لربط حسابه بالطالب ' || v_name || ' (لا يوجد محفّظ للحلقة). راجعه من «طلبات الاعتماد».',
        'link-req:' || p_guardian::text || ':' || v_sid::text || ':a:' || to_char(now(), 'YYYYMMDDHH24MISS'));
    end loop;
  end if;

  return jsonb_build_object('ok', true, 'stage', case when v_status = 'pending' then 'teacher' else 'admin' end);
end $$;

-- 6) Sign-up completion (name, phone, account type, and for a guardian the student's id + birth date).
create or replace function public.rc_complete_signup(
  p_name text, p_phone text, p_role text, p_national_id text default null, p_birth_date date default null
)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  me public.profiles%rowtype;
  res jsonb;
  t record;
begin
  if uid is null then raise exception 'غير مسجّل الدخول'; end if;
  select * into me from public.profiles where id = uid for update;
  if not found then raise exception 'لا يوجد ملف لهذا الحساب'; end if;
  if me.approval_status <> 'pending' then
    return jsonb_build_object('ok', false, 'error', 'حالة الحساب لا تسمح بهذا الإجراء');
  end if;
  if p_role not in ('guardian', 'teacher', 'supervisor') then
    return jsonb_build_object('ok', false, 'error', 'نوع الحساب غير صالح');
  end if;
  if length(trim(coalesce(p_name, ''))) < 3 then
    return jsonb_build_object('ok', false, 'error', 'اكتب الاسم الكامل');
  end if;

  if p_role = 'guardian' then
    res := public.rc_create_link_request(uid, p_national_id, p_birth_date);
    if not coalesce((res ->> 'ok')::boolean, false) then
      return res;
    end if;
  end if;

  perform set_config('halaqati.review_ok', '1', true);
  update public.profiles
     set full_name = trim(p_name),
         phone = nullif(trim(coalesce(p_phone, '')), ''),
         requested_role = p_role::public.app_role,
         onboarded = true
   where id = uid;
  perform set_config('halaqati.review_ok', '', true);

  if p_role = 'teacher' then
    for t in select id from public.profiles where role in ('supervisor', 'admin') and approval_status = 'approved' and active loop
      perform public.rc_approval_notify(t.id, 'طلب حساب محفّظ',
        'طلب جديد من ' || trim(p_name) || ' لحساب محفّظ. راجعه من «طلبات الاعتماد».',
        'acct-req:' || uid::text || ':' || to_char(now(), 'YYYYMMDDHH24MISS'));
    end loop;
  elsif p_role = 'supervisor' then
    for t in select id from public.profiles where role = 'admin' and approval_status = 'approved' and active loop
      perform public.rc_approval_notify(t.id, 'طلب حساب مشرف',
        'طلب جديد من ' || trim(p_name) || ' لحساب مشرف حلقات. راجعه من «طلبات الاعتماد».',
        'acct-req:' || uid::text || ':' || to_char(now(), 'YYYYMMDDHH24MISS'));
    end loop;
  end if;

  perform public.rc_audit('profiles', uid::text, 'rc_complete_signup', jsonb_build_object('role', p_role));
  return coalesce(res, jsonb_build_object('ok', true, 'stage', case when p_role = 'teacher' then 'supervisor' else 'admin' end));
end $$;

-- 7) A guardian that is already approved adds another child.
create or replace function public.rc_request_guardian_link(p_national_id text, p_birth_date date)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'غير مسجّل الدخول'; end if;
  if not exists (
    select 1 from public.profiles p
    where p.id = uid and p.role::text = 'guardian' and p.approval_status in ('pending', 'approved')
      and (p.approval_status = 'approved' or p.requested_role::text = 'guardian')
  ) then
    return jsonb_build_object('ok', false, 'error', 'هذه الخدمة لأولياء الأمور فقط');
  end if;
  return public.rc_create_link_request(uid, p_national_id, p_birth_date);
end $$;

-- 8) What the waiting person sees.
create or replace function public.rc_my_signup_status()
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  me public.profiles%rowtype;
  l public.guardian_link_requests%rowtype;
begin
  select * into me from public.profiles where id = uid;
  if not found then return jsonb_build_object('stage', 'unknown'); end if;
  if me.approval_status = 'approved' then return jsonb_build_object('stage', 'approved'); end if;
  if me.approval_status = 'rejected' then
    return jsonb_build_object('stage', 'rejected', 'text', coalesce(me.rejection_reason, ''));
  end if;
  if me.requested_role::text = 'guardian' then
    select * into l from public.guardian_link_requests where guardian_id = uid order by created_at desc limit 1;
    if found then
      if l.status = 'pending' then
        return jsonb_build_object('stage', 'teacher', 'text', 'بانتظار موافقة محفّظ حلقة الطالب');
      elsif l.status = 'teacher_approved' then
        return jsonb_build_object('stage', 'admin', 'text',
          case when l.teacher_id is null then 'بانتظار تأكيد مسؤول النظام'
               else 'وافق المحفّظ، وبانتظار تأكيد مسؤول النظام' end);
      elsif l.status = 'rejected' then
        return jsonb_build_object('stage', 'rejected', 'text', coalesce(l.reason, 'تم رفض طلب الربط'));
      end if;
    end if;
    return jsonb_build_object('stage', 'admin', 'text', 'بانتظار مسؤول النظام');
  elsif me.requested_role::text = 'teacher' then
    return jsonb_build_object('stage', 'supervisor', 'text', 'بانتظار موافقة مشرف الحلقات أو مسؤول النظام');
  end if;
  return jsonb_build_object('stage', 'admin', 'text', 'بانتظار مسؤول النظام');
end $$;

-- 9) The requests a person is allowed to act on.
create or replace function public.rc_pending_requests()
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  r text := public.current_role()::text;
  res jsonb;
begin
  if r not in ('admin', 'supervisor', 'teacher') then return '[]'::jsonb; end if;

  select coalesce(jsonb_agg(x order by x ->> 'created_at'), '[]'::jsonb) into res from (
    -- account requests (teacher / supervisor, and a guardian that has no link request)
    select jsonb_build_object(
      'kind', 'account', 'id', p.id, 'name', p.full_name, 'email', p.email, 'phone', p.phone,
      'role', p.requested_role::text, 'stage', 'account', 'created_at', p.created_at) as x
    from public.profiles p
    where r in ('admin', 'supervisor')
      and p.approval_status = 'pending' and p.onboarded
      and p.id <> uid
      and (
        (r = 'admin' and (
           p.requested_role::text in ('teacher', 'supervisor')
           or (p.requested_role::text = 'guardian' and not exists (
                 select 1 from public.guardian_link_requests l
                 where l.guardian_id = p.id))))
        or (r = 'supervisor' and p.requested_role::text = 'teacher')
      )
    union all
    -- guardian link requests
    select jsonb_build_object(
      'kind', 'link', 'id', l.id, 'name', gp.full_name, 'email', gp.email, 'phone', gp.phone, 'role', 'guardian',
      'student', s.full_name, 'halaqa', h.name,
      'stage', case when l.status = 'teacher_approved' then 'admin' else 'teacher' end,
      'teacher_name', tp.full_name, 'created_at', l.created_at) as x
    from public.guardian_link_requests l
    join public.profiles gp on gp.id = l.guardian_id
    join public.students s on s.id = l.student_id
    left join public.halaqas h on h.id = s.halaqa_id
    left join public.profiles tp on tp.id = l.teacher_id
    where (r = 'admin' and l.status in ('pending', 'teacher_approved'))
       or (r = 'teacher' and l.status = 'pending' and public.rc_is_student_teacher(uid, l.student_id))
  ) q;
  return res;
end $$;

-- 10) Approve / reject.
create or replace function public.rc_review_request(p_kind text, p_id uuid, p_action text, p_reason text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  r text := public.current_role()::text;
  tp public.profiles%rowtype;
  l public.guardian_link_requests%rowtype;
  v_reason text := nullif(trim(coalesce(p_reason, '')), '');
  v_student text;
  a record;
begin
  if uid is null or r not in ('admin', 'supervisor', 'teacher') then
    raise exception 'لا تملك صلاحية مراجعة الطلبات';
  end if;
  if p_action not in ('approve', 'reject') then raise exception 'إجراء غير صالح'; end if;

  if p_kind = 'account' then
    select * into tp from public.profiles where id = p_id for update;
    if not found then return jsonb_build_object('ok', false, 'error', 'الطلب غير موجود'); end if;
    if tp.approval_status <> 'pending' then
      return jsonb_build_object('ok', false, 'error', 'تمت معالجة هذا الطلب مسبقًا');
    end if;
    if tp.id = uid then raise exception 'لا يمكنك اعتماد حسابك بنفسك'; end if;
    if not (r = 'admin' or (r = 'supervisor' and tp.requested_role::text = 'teacher')) then
      raise exception 'لا تملك صلاحية على هذا الطلب';
    end if;
    if tp.requested_role::text not in ('guardian', 'teacher', 'supervisor') then
      raise exception 'نوع الحساب المطلوب غير صالح';
    end if;

    perform set_config('halaqati.review_ok', '1', true);
    if p_action = 'approve' then
      update public.profiles
         set role = tp.requested_role, approval_status = 'approved', active = true,
             approved_at = now(), approved_by = uid, rejection_reason = null
       where id = p_id;
      perform public.rc_approval_notify(p_id, 'تم اعتماد حسابك', 'تم اعتماد حسابك. يمكنك الآن استخدام التطبيق.',
        'acct-ok:' || p_id::text || ':' || to_char(now(), 'YYYYMMDDHH24MISS'));
    else
      update public.profiles
         set approval_status = 'rejected', active = false, rejection_reason = v_reason
       where id = p_id;
    end if;
    perform set_config('halaqati.review_ok', '', true);
    perform public.rc_audit('profiles', p_id::text, 'rc_review_account_' || p_action,
      jsonb_build_object('by_role', r, 'role', tp.requested_role::text, 'reason', v_reason));
    return jsonb_build_object('ok', true);

  elsif p_kind = 'link' then
    select * into l from public.guardian_link_requests where id = p_id for update;
    if not found then return jsonb_build_object('ok', false, 'error', 'الطلب غير موجود'); end if;
    select full_name into v_student from public.students where id = l.student_id;

    if r = 'teacher' then
      if l.status <> 'pending' then
        return jsonb_build_object('ok', false, 'error', 'تمت معالجة هذا الطلب مسبقًا');
      end if;
      if not public.rc_is_student_teacher(uid, l.student_id) then
        raise exception 'هذا الطالب ليس من طلابك';
      end if;
      if p_action = 'approve' then
        update public.guardian_link_requests
           set status = 'teacher_approved', teacher_id = uid, teacher_at = now() where id = p_id;
        for a in select id from public.profiles where role = 'admin' and approval_status = 'approved' and active loop
          perform public.rc_approval_notify(a.id, 'وافق المحفّظ على طلب ولي أمر',
            'وافق المحفّظ على ربط ولي أمر بالطالب ' || coalesce(v_student, '') || '. بانتظار تأكيدك من «طلبات الاعتماد».',
            'link-tok:' || p_id::text);
        end loop;
      else
        update public.guardian_link_requests
           set status = 'rejected', teacher_id = uid, teacher_at = now(), reason = v_reason where id = p_id;
        perform public.rc_approval_notify(l.guardian_id, 'تم رفض طلب الربط',
          coalesce(v_reason, 'رفض محفّظ الحلقة طلب ربط حسابك بالطالب.'),
          'link-rej:' || p_id::text || ':' || to_char(now(), 'YYYYMMDDHH24MISS'));
      end if;

    elsif r = 'admin' then
      if l.status not in ('pending', 'teacher_approved') then
        return jsonb_build_object('ok', false, 'error', 'تمت معالجة هذا الطلب مسبقًا');
      end if;
      if p_action = 'approve' then
        update public.guardian_link_requests
           set status = 'approved', admin_id = uid, admin_at = now() where id = p_id;
        select * into tp from public.profiles where id = l.guardian_id for update;
        if tp.approval_status = 'pending' then
          perform set_config('halaqati.review_ok', '1', true);
          update public.profiles
             set role = 'guardian', approval_status = 'approved', active = true,
                 approved_at = now(), approved_by = uid, rejection_reason = null
           where id = l.guardian_id;
          perform set_config('halaqati.review_ok', '', true);
        end if;
        insert into public.student_guardians (student_id, guardian_id, relation, is_primary)
        values (l.student_id, l.guardian_id, 'ولي أمر', true)
        on conflict (student_id, guardian_id) do nothing;
        perform public.rc_approval_notify(l.guardian_id, 'تم اعتماد حسابك',
          'تم ربط حسابك بالطالب ' || coalesce(v_student, '') || '. يمكنك متابعة ابنك الآن.',
          'link-ok:' || p_id::text);
      else
        update public.guardian_link_requests
           set status = 'rejected', admin_id = uid, admin_at = now(), reason = v_reason where id = p_id;
        perform public.rc_approval_notify(l.guardian_id, 'تم رفض طلب الربط',
          coalesce(v_reason, 'رفض مسؤول النظام طلب ربط حسابك بالطالب.'),
          'link-rej:' || p_id::text || ':' || to_char(now(), 'YYYYMMDDHH24MISS'));
      end if;
    else
      raise exception 'لا تملك صلاحية على هذا الطلب';
    end if;

    perform public.rc_audit('guardian_link_requests', p_id::text, 'rc_review_link_' || p_action,
      jsonb_build_object('by_role', r, 'student_id', l.student_id, 'guardian_id', l.guardian_id, 'reason', v_reason));
    return jsonb_build_object('ok', true);
  end if;

  raise exception 'نوع الطلب غير صالح';
end $$;

-- 11) Who may call what.
revoke execute on function public.rc_create_link_request(uuid, text, date) from public, anon, authenticated;
revoke execute on function public.rc_approval_notify(uuid, text, text, text) from public, anon, authenticated;
revoke execute on function public.rc_audit(text, text, text, jsonb) from public, anon, authenticated;
revoke execute on function public.rc_is_student_teacher(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.rc_student_has_teacher(uuid) from public, anon, authenticated;

revoke execute on function public.rc_complete_signup(text, text, text, text, date) from public, anon;
revoke execute on function public.rc_request_guardian_link(text, date) from public, anon;
revoke execute on function public.rc_my_signup_status() from public, anon;
revoke execute on function public.rc_pending_requests() from public, anon;
revoke execute on function public.rc_review_request(text, uuid, text, text) from public, anon;
grant execute on function public.rc_complete_signup(text, text, text, text, date) to authenticated;
grant execute on function public.rc_request_guardian_link(text, date) to authenticated;
grant execute on function public.rc_my_signup_status() to authenticated;
grant execute on function public.rc_pending_requests() to authenticated;
grant execute on function public.rc_review_request(text, uuid, text, text) to authenticated;

commit;
