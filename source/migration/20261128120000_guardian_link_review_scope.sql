-- GA-02 / GA-05. Forward-only, with the existing RPC names and argument contracts.
-- Derived from production catalog definitions checked 2026-11-28.
begin;

-- Existing, explicitly verified student_guardians rows are preserved.
-- Email equality on a profile update no longer creates a student relationship.
drop trigger if exists link_guardian_profile_trigger on public.profiles;

create function public.guard_guardian_account_review()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if coalesce(auth.role(), '') = 'authenticated'
     and old.approval_status in ('pending', 'rejected')
     and new.approval_status is distinct from old.approval_status
     and (coalesce(old.requested_role, old.role)::text = 'guardian'
       or new.role::text = 'guardian')
     and coalesce(current_setting('halaqati.guardian_link_approval', true), '') <> '1' then
    raise exception 'راجع طلب الربط الخاص بولي الأمر' using errcode = '42501';
  end if;
  return new;
end;
$$;
revoke execute on function public.guard_guardian_account_review() from public, anon, authenticated;
create trigger guard_guardian_account_review
before update of approval_status on public.profiles
for each row execute function public.guard_guardian_account_review();

CREATE OR REPLACE FUNCTION public.rc_review_request(p_kind text, p_id uuid, p_action text, p_reason text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  uid uuid := auth.uid();
  r text := public.current_role()::text;
  tp public.profiles%rowtype;
  l public.guardian_link_requests%rowtype;
  v_reason text := nullif(trim(coalesce(p_reason, '')), '');
  student public.students%rowtype;
  scope_id uuid := public.my_association();
  owner boolean := public.is_platform_owner();
  a record;
begin
  if uid is null or r not in ('admin', 'supervisor', 'teacher') or not exists (
    select 1 from public.profiles reviewer
    where reviewer.id = uid and reviewer.active and not reviewer.frozen
      and reviewer.approval_status = 'approved'
  ) then
    raise exception 'لا تملك صلاحية مراجعة الطلبات';
  end if;
  if p_action is null or p_action not in ('approve', 'reject') then raise exception 'إجراء غير صالح'; end if;

  if p_kind = 'account' then
    select * into tp from public.profiles where id = p_id for update;
    if not found then return jsonb_build_object('ok', false, 'error', 'الطلب غير موجود'); end if;
    if tp.id = uid or (
      (r = 'admin' or (r = 'supervisor' and tp.requested_role::text = 'teacher'))
      and (owner or tp.association_id = scope_id)
    ) is not true then
      raise exception 'لا تملك صلاحية على هذا الطلب' using errcode = '42501';
    end if;
    if tp.approval_status <> 'pending' or tp.frozen or not tp.onboarded then
      return jsonb_build_object('ok', false, 'error', 'تمت معالجة هذا الطلب مسبقًا');
    end if;
    if coalesce(tp.requested_role, tp.role)::text = 'guardian' then
      return jsonb_build_object('ok', false, 'error', 'راجع طلب الربط الخاص بولي الأمر');
    end if;
    if tp.requested_role is null or tp.requested_role::text not in ('teacher', 'supervisor', 'observer') then
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
    select * into tp from public.profiles where id = l.guardian_id for update;
    select * into student from public.students where id = l.student_id for share;
    if student.id is null or tp.id is null or tp.id = uid
       or not (r = 'admin' or (r = 'teacher' and public.rc_is_student_teacher(uid, l.student_id)))
       or not (owner or coalesce(student.association_id = scope_id, false))
       or (tp.association_id is not null and tp.association_id is distinct from student.association_id) then
      raise exception 'لا تملك صلاحية على هذا الطلب' using errcode = '42501';
    end if;
    if p_action = 'approve' and (
      student.status <> 'active' or tp.frozen or not tp.onboarded
      or not ((tp.approval_status = 'pending' and tp.requested_role::text = 'guardian')
           or (tp.approval_status = 'approved' and tp.role::text = 'guardian' and tp.active))
    ) is not false then
      return jsonb_build_object('ok', false, 'error', 'حالة الطالب أو حساب ولي الأمر لا تسمح بالربط');
    end if;
    if r = 'admin' and p_action = 'approve' and exists (
      select 1 from public.student_guardians sg join public.students child on child.id = sg.student_id
      where sg.guardian_id = tp.id and child.association_id is distinct from student.association_id
    ) then
      return jsonb_build_object('ok', false, 'error', 'يرتبط هذا الحساب بطالب في جمعية أخرى');
    end if;

    if r = 'teacher' then
      if l.status <> 'pending' then
        return jsonb_build_object('ok', false, 'error', 'تمت معالجة هذا الطلب مسبقًا');
      end if;
      if p_action = 'approve' then
        update public.guardian_link_requests
           set status = 'teacher_approved', teacher_id = uid, teacher_at = now() where id = p_id;
        for a in select id from public.profiles where role = 'admin' and approval_status = 'approved' and active and not frozen
          and (is_platform_owner or association_id = student.association_id) loop
          perform public.rc_approval_notify(a.id, 'وافق المحفّظ على طلب ولي أمر',
            'وافق المحفّظ على ربط ولي أمر بالطالب ' || coalesce(student.full_name, '') || '. بانتظار تأكيدك من «طلبات الاعتماد».',
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
        if tp.approval_status = 'pending' then
          perform set_config('halaqati.review_ok', '1', true);
          perform set_config('halaqati.guardian_link_approval', '1', true);
          update public.profiles
             set role = 'guardian', approval_status = 'approved', active = true,
                 approved_at = now(), approved_by = uid, rejection_reason = null
           where id = l.guardian_id;
          perform set_config('halaqati.guardian_link_approval', '', true);
          perform set_config('halaqati.review_ok', '', true);
        end if;
        insert into public.student_guardians (student_id, guardian_id, relation, is_primary)
        values (l.student_id, l.guardian_id, 'ولي أمر', true)
        on conflict (student_id, guardian_id) do nothing;
        perform public.rc_approval_notify(l.guardian_id, 'تم اعتماد طلب الربط',
          'تم ربط حسابك بالطالب ' || coalesce(student.full_name, '') || '. يمكنك متابعة ابنك الآن.',
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
end $function$
;

CREATE OR REPLACE FUNCTION public.rc_pending_requests()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  uid uuid := auth.uid();
  r text := public.current_role()::text;
  res jsonb;
  scope_id uuid := public.my_association();
  owner boolean := public.is_platform_owner();
begin
  if uid is null or r not in ('admin', 'supervisor', 'teacher') or not exists (
    select 1 from public.profiles reviewer
    where reviewer.id = uid and reviewer.active and not reviewer.frozen
      and reviewer.approval_status = 'approved'
  ) then return '[]'::jsonb; end if;

  select coalesce(jsonb_agg(x order by x ->> 'created_at'), '[]'::jsonb) into res from (
    select jsonb_build_object(
      'kind', 'account', 'id', p.id, 'name', p.full_name, 'email', p.email, 'phone', p.phone,
      'role', p.requested_role::text, 'stage', 'account', 'created_at', p.created_at) as x
    from public.profiles p
    where r in ('admin', 'supervisor')
      and p.approval_status = 'pending' and p.onboarded
      and p.id <> uid and not p.frozen
      and (owner or (scope_id is not null and p.association_id = scope_id))
      and ((r = 'admin' and p.requested_role::text in ('teacher', 'supervisor', 'observer'))
        or (r = 'supervisor' and p.requested_role::text = 'teacher'))
    union all
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
    where (owner or (scope_id is not null and s.association_id = scope_id))
      and (gp.association_id is null or gp.association_id = s.association_id)
      and gp.id <> uid
      and ((r = 'admin' and l.status in ('pending', 'teacher_approved'))
       or (r = 'teacher' and l.status = 'pending' and public.rc_is_student_teacher(uid, l.student_id)))
  ) q;
  return res;
end $function$
;

commit;
