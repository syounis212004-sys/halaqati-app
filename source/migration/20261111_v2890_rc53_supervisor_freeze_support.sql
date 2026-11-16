-- RC53 (2.89.0) server changes. Safe to run more than once. Run in the Supabase SQL editor BEFORE publishing the APK.
-- 1) Account freezing + staff photo  2) Support details card  3) Supervisor alerts + staff overview  4) Visit follow-up flag

-- ---------- 1) profiles: frozen / frozen_reason / avatar_path ----------
alter table public.profiles add column if not exists frozen boolean not null default false;
alter table public.profiles add column if not exists frozen_reason text;
alter table public.profiles add column if not exists avatar_path text;   -- small JPEG data URL, same format as students.photo_path

-- Only an administrator may change frozen / frozen_reason (everyone may still edit their own avatar_path).
create or replace function public.protect_profile_security_fields()
returns trigger language plpgsql security definer set search_path to 'public' as $function$
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
       or new.onboarded is distinct from old.onboarded
       or new.frozen is distinct from old.frozen
       or new.frozen_reason is distinct from old.frozen_reason then
      raise exception 'هذه الحقول لا يغيرها إلا مسؤول النظام';
    end if;
  end if;
  new.updated_at := now();
  return new;
end $function$;

-- A frozen account (other than an administrator) gets no role, so every policy that depends on current_role() closes.
-- The account can still read its own profile row, which is how the app learns it is frozen.
create or replace function public."current_role"()
returns app_role language sql stable security definer set search_path to 'public' as $function$
  select role from public.profiles
  where id=auth.uid() and active=true and approval_status='approved'
    and (coalesce(frozen,false)=false or role='admin')
$function$;

-- ---------- 2) support details (phone / kind of destination) ----------
create table if not exists public.app_support_info (
  id smallint primary key default 1 check (id = 1),
  phone text,
  entity_type text,      -- bank | wallet | other
  entity_name text,
  note text,
  updated_at timestamptz not null default now()
);
alter table public.app_support_info enable row level security;
drop policy if exists app_support_info_read on public.app_support_info;
create policy app_support_info_read on public.app_support_info for select to authenticated using (true);
drop policy if exists app_support_info_admin_ins on public.app_support_info;
create policy app_support_info_admin_ins on public.app_support_info for insert to authenticated with check (public.is_admin());
drop policy if exists app_support_info_admin_upd on public.app_support_info;
create policy app_support_info_admin_upd on public.app_support_info for update to authenticated using (public.is_admin()) with check (public.is_admin());
insert into public.app_support_info(id) values (1) on conflict (id) do nothing;

-- ---------- 3a) alert a teacher / supervisor (admin: any staff; supervisor: teachers of his halaqat) ----------
create or replace function public.rc53_alert_staff(p_recipient uuid, p_title text, p_body text, p_halaqa uuid default null)
returns jsonb language plpgsql security definer set search_path to 'public' as $function$
declare v_role app_role := public."current_role"(); v_ok boolean := false;
begin
  if v_role is null or v_role not in ('admin','supervisor') then
    return jsonb_build_object('ok',false,'error','غير مصرح');
  end if;
  if coalesce(btrim(p_title),'')='' or coalesce(btrim(p_body),'')='' then
    return jsonb_build_object('ok',false,'error','اكتب العنوان والنص');
  end if;
  if v_role='admin' then
    v_ok := exists(select 1 from public.profiles where id=p_recipient and active and approval_status='approved' and role in ('teacher','supervisor'));
  else
    v_ok := exists(select 1 from public.halaqa_teachers ht where ht.teacher_id=p_recipient and public.is_halaqa_supervisor(ht.halaqa_id));
  end if;
  if not v_ok then return jsonb_build_object('ok',false,'error','لا تملك صلاحية تنبيه هذا الحساب'); end if;
  insert into public.notifications(recipient_id,sender_id,halaqa_id,kind,title,body,source_key,deep_link,data,push_status)
  values (p_recipient, auth.uid(), p_halaqa, 'manual', left(btrim(p_title),80), left(btrim(p_body),500),
          'alert:'||auth.uid()::text||':'||gen_random_uuid()::text, 'notifications', jsonb_build_object('alert',true), 'skipped');
  return jsonb_build_object('ok',true);
end $function$;
grant execute on function public.rc53_alert_staff(uuid,text,text,uuid) to authenticated;

-- ---------- 3b) one-shot staff overview for the supervisor / administrator dashboards ----------
create or replace function public.rc53_staff_overview(p_from date, p_to date)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $function$
declare v_role app_role := public."current_role"();
begin
  if v_role is null or v_role not in ('admin','supervisor') then raise exception 'غير مصرح'; end if;
  return coalesce((
    with staff as (
      select p.id, p.full_name, p.role::text as role, p.avatar_path, p.phone, p.active, coalesce(p.frozen,false) as frozen
      from public.profiles p
      where p.role in ('teacher','supervisor') and p.approval_status='approved'
        and (v_role='admin'
             or p.id=auth.uid()
             or (p.role='teacher' and exists(select 1 from public.halaqa_teachers ht where ht.teacher_id=p.id and public.is_halaqa_supervisor(ht.halaqa_id))))
    ), hq as (
      select s.id as staff_id, h.id as halaqa_id, h.name
      from staff s join public.halaqas h on h.id in (
        select halaqa_id from public.halaqa_teachers where teacher_id=s.id
        union select halaqa_id from public.halaqa_supervisors where supervisor_id=s.id
        union select id from public.halaqas where supervisor_id=s.id)
    )
    select jsonb_agg(jsonb_build_object(
      'id', s.id, 'full_name', s.full_name, 'role', s.role, 'avatar_path', s.avatar_path, 'phone', s.phone,
      'active', s.active, 'frozen', s.frozen,
      'halaqas', (select coalesce(jsonb_agg(jsonb_build_object('id',hq.halaqa_id,'name',hq.name)),'[]'::jsonb) from hq where hq.staff_id=s.id),
      'sessions', (select count(*) from public.sessions x where x.created_by=s.id and x.session_date between p_from and p_to),
      'session_days', (select count(distinct x.session_date) from public.sessions x where x.created_by=s.id and x.session_date between p_from and p_to),
      'closed_sessions', (select count(*) from public.sessions x where x.created_by=s.id and x.status='closed' and x.session_date between p_from and p_to),
      'recitations', (select count(*) from public.recitation_entries r join public.sessions x on x.id=r.session_id where r.created_by=s.id and x.session_date between p_from and p_to),
      'last_session', (select max(x.session_date) from public.sessions x where x.created_by=s.id),
      'visits_made', (select count(*) from public.supervisor_visits v where v.supervisor_id=s.id and v.visit_date between p_from and p_to),
      'visits_received', (select count(*) from public.supervisor_visits v where v.halaqa_id in (select halaqa_id from hq where hq.staff_id=s.id) and v.visit_date between p_from and p_to),
      'visit_score', (select round(avg((coalesce(v.organization_score,0)+coalesce(v.teaching_score,0)+coalesce(v.followup_score,0))/3.0),2)
                      from public.supervisor_visits v where v.halaqa_id in (select halaqa_id from hq where hq.staff_id=s.id) and v.visit_date between p_from and p_to),
      'open_actions', (select count(*) from public.supervisor_visits v where v.halaqa_id in (select halaqa_id from hq where hq.staff_id=s.id)
                       and coalesce(btrim(v.action_items),'')<>'' and coalesce(v.action_done,false)=false)
    ) order by s.role desc, s.full_name)
    from staff s
  ), '[]'::jsonb);
end $function$;
grant execute on function public.rc53_staff_overview(date,date) to authenticated;

-- ---------- 4) supervisor visits: follow-up flag ----------
alter table public.supervisor_visits add column if not exists action_done boolean not null default false;

-- ---------- 5) notification centre: reach donors / supervisors (administrator only) ----------
create or replace function public.rc53_broadcast_special(p_audience text, p_title text, p_body text, p_source_key text, p_label text default '', p_dry boolean default false)
returns jsonb language plpgsql security definer set search_path to 'public' as $function$
declare v_role app_role := public."current_role"(); v_target app_role; v_count int;
begin
  if v_role is distinct from 'admin'::app_role then return jsonb_build_object('error','هذه الفئة لمسؤول النظام فقط'); end if;
  if p_audience = 'donors' then v_target := 'observer'::app_role;
  elsif p_audience = 'supervisors' then v_target := 'supervisor'::app_role;
  else return jsonb_build_object('error','فئة غير مدعومة'); end if;
  select count(*) into v_count from public.profiles where role=v_target and active and approval_status='approved' and id<>auth.uid();
  if p_dry then return jsonb_build_object('ok',true,'preview',true,'recipients',v_count); end if;
  if coalesce(btrim(p_title),'')='' or coalesce(btrim(p_body),'')='' then return jsonb_build_object('error','اكتب العنوان والنص'); end if;
  insert into public.notifications(recipient_id,sender_id,kind,title,body,source_key,deep_link,data,push_status)
  select p.id, auth.uid(), 'manual', left(btrim(p_title),80), left(btrim(p_body),500), p_source_key, 'notifications',
         jsonb_build_object('audience',p_audience,'label',coalesce(p_label,''),'scope','all'), 'skipped'
  from public.profiles p where p.role=v_target and p.active and p.approval_status='approved' and p.id<>auth.uid()
  on conflict (recipient_id, source_key) do nothing;
  return jsonb_build_object('ok',true,'recipients',v_count,'created',v_count);
end $function$;
grant execute on function public.rc53_broadcast_special(text,text,text,text,text,boolean) to authenticated;
