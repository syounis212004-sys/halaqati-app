-- Halaqati v2.54.0 RC18 — Guardian Privacy + Monthly Plan Archive/Rollover + Real Achievements
-- Run AFTER 20260930_v2522_rc17_1_build_fix3_smart_plan_continuity.sql.
-- Additive/idempotent. Does not delete recitation history.

begin;

-- ---------------------------------------------------------------------------
-- 1) Guardian privacy helpers and authoritative child list
-- ---------------------------------------------------------------------------
create index if not exists student_guardians_guardian_student_idx
  on public.student_guardians(guardian_id, student_id);

create or replace function public.rc18_guardian_owns_student(p_student_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() is not null and exists (
    select 1
      from public.student_guardians sg
     where sg.guardian_id = auth.uid()
       and sg.student_id = p_student_id
  );
$$;

create or replace function public.rc18_can_manage_student(p_student_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() is not null and (
    public.is_admin()
    or exists (
      select 1
        from public.students s
        join public.halaqa_teachers ht on ht.halaqa_id = s.halaqa_id
       where s.id = p_student_id and ht.teacher_id = auth.uid()
    )
    or exists (
      select 1
        from public.students s
        join public.halaqa_supervisors hs on hs.halaqa_id = s.halaqa_id
       where s.id = p_student_id and hs.supervisor_id = auth.uid()
    )
  );
$$;

create or replace function public.rc18_can_view_student(p_student_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.rc18_guardian_owns_student(p_student_id)
      or public.rc18_can_manage_student(p_student_id);
$$;

revoke all on function public.rc18_guardian_owns_student(uuid) from public, anon;
revoke all on function public.rc18_can_manage_student(uuid) from public, anon;
revoke all on function public.rc18_can_view_student(uuid) from public, anon;
grant execute on function public.rc18_guardian_owns_student(uuid) to authenticated;
grant execute on function public.rc18_can_manage_student(uuid) to authenticated;
grant execute on function public.rc18_can_view_student(uuid) to authenticated;

create or replace function public.guardian_children()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_result jsonb;
begin
  if public.current_role() is distinct from 'guardian'::public.app_role then
    raise exception 'guardian access only';
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
      'id', s.id,
      'full_name', s.full_name,
      'status', s.status,
      'track_code', s.track_code,
      'halaqa_id', s.halaqa_id,
      'category_id', s.category_id,
      'primary_teacher_id', s.primary_teacher_id,
      'target_pages_weekly', s.target_pages_weekly,
      'target_review_pages_weekly', s.target_review_pages_weekly,
      'updated_at', s.updated_at
    ) order by s.full_name), '[]'::jsonb)
    into v_result
    from public.student_guardians sg
    join public.students s on s.id = sg.student_id
   where sg.guardian_id = auth.uid()
     and s.status = 'active';

  return v_result;
end;
$$;

revoke all on function public.guardian_children() from public, anon;
grant execute on function public.guardian_children() to authenticated;

-- Defense in depth: even legacy screens that accidentally query broad tables must not
-- expose another child to a guardian. Restrictive policies AND with existing policies.
do $$
begin
  if to_regclass('public.students') is not null then
    execute 'drop policy if exists rc18_guardian_students_privacy on public.students';
    execute $p$create policy rc18_guardian_students_privacy on public.students as restrictive
      for select to authenticated using (
        public.current_role() is distinct from 'guardian'::public.app_role
        or public.rc18_guardian_owns_student(id)
      )$p$;
  end if;
  if to_regclass('public.recitation_entries') is not null then
    execute 'drop policy if exists rc18_guardian_recitations_privacy on public.recitation_entries';
    execute $p$create policy rc18_guardian_recitations_privacy on public.recitation_entries as restrictive
      for select to authenticated using (
        public.current_role() is distinct from 'guardian'::public.app_role
        or public.rc18_guardian_owns_student(student_id)
      )$p$;
  end if;
  if to_regclass('public.attendance_records') is not null then
    execute 'drop policy if exists rc18_guardian_attendance_privacy on public.attendance_records';
    execute $p$create policy rc18_guardian_attendance_privacy on public.attendance_records as restrictive
      for select to authenticated using (
        public.current_role() is distinct from 'guardian'::public.app_role
        or public.rc18_guardian_owns_student(student_id)
      )$p$;
  end if;
  if to_regclass('public.smart_plan_phase_states') is not null then
    execute 'drop policy if exists rc18_guardian_phase_states_privacy on public.smart_plan_phase_states';
    execute $p$create policy rc18_guardian_phase_states_privacy on public.smart_plan_phase_states as restrictive
      for select to authenticated using (
        public.current_role() is distinct from 'guardian'::public.app_role
        or public.rc18_guardian_owns_student(student_id)
      )$p$;
  end if;
  if to_regclass('public.smart_plan_events') is not null then
    execute 'drop policy if exists rc18_guardian_plan_events_privacy on public.smart_plan_events';
    execute $p$create policy rc18_guardian_plan_events_privacy on public.smart_plan_events as restrictive
      for select to authenticated using (
        public.current_role() is distinct from 'guardian'::public.app_role
        or public.rc18_guardian_owns_student(student_id)
      )$p$;
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- 2) Immutable month archive for Smart Plan history
-- ---------------------------------------------------------------------------
create table if not exists public.smart_plan_month_archives (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.students(id) on delete cascade,
  halaqa_id uuid not null references public.halaqas(id) on delete cascade,
  source_plan_id uuid references public.smart_plans(id) on delete set null,
  period_month date not null,
  plan_type text not null,
  plan_title text not null,
  target_pages numeric(10,2) not null default 0,
  done_pages numeric(10,2) not null default 0,
  sard_target_pages numeric(10,2) not null default 0,
  sard_done_pages numeric(10,2) not null default 0,
  attendance_present integer not null default 0,
  attendance_absent integer not null default 0,
  quality_percent numeric(7,2),
  start_anchor jsonb,
  end_anchor jsonb,
  snapshot jsonb not null default '{}'::jsonb,
  archived_at timestamptz not null default now(),
  created_by uuid default auth.uid() references public.profiles(id) on delete set null,
  unique(student_id, source_plan_id, period_month)
);

create index if not exists smart_plan_month_archives_student_month_idx
  on public.smart_plan_month_archives(student_id, period_month desc);
create index if not exists smart_plan_month_archives_halaqa_month_idx
  on public.smart_plan_month_archives(halaqa_id, period_month desc);

alter table public.smart_plan_month_archives enable row level security;
revoke all on public.smart_plan_month_archives from anon;
revoke all on public.smart_plan_month_archives from authenticated;
grant select on public.smart_plan_month_archives to authenticated;

drop policy if exists rc18_month_archive_select on public.smart_plan_month_archives;
create policy rc18_month_archive_select on public.smart_plan_month_archives
for select to authenticated
using (public.rc18_can_view_student(student_id));

-- One factual archive row per student / source monthly plan. Existing rows are updated only
-- by the idempotent rollover function so a retry cannot duplicate history.
create or replace function public.rc18_archive_finished_plans(p_as_of date default current_date)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  p record;
  s record;
  v_month_start date := date_trunc('month', p_as_of)::date;
  v_period date;
  v_hifz numeric;
  v_sard numeric;
  v_tathbit numeric;
  v_target numeric;
  v_sard_target numeric;
  v_present integer;
  v_absent integer;
  v_quality numeric;
  v_first jsonb;
  v_last jsonb;
  v_count integer := 0;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;

  for p in
    select sp.*
      from public.smart_plans sp
     where coalesce(sp.legacy, false) = false
       and sp.end_date < v_month_start
       and sp.status in ('active','paused','completed')
       and (
         public.is_admin()
         or exists(select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=auth.uid())
         or exists(select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=auth.uid())
       )
  loop
    v_period := coalesce(p.period_month, date_trunc('month', p.start_date)::date);

    for s in
      select st.* from public.students st
       where st.status='active' and st.halaqa_id=p.halaqa_id and (
         (p.scope_type='student' and st.id=p.student_id)
         or (p.scope_type='category' and st.category_id=p.category_id)
         or (p.scope_type='halaqa')
       )
    loop
      with rec as (
        select r.*, se.session_date
          from public.recitation_entries r
          join public.sessions se on se.id=r.session_id and se.status<>'cancelled'
         where r.student_id=s.id
           and se.session_date between p.start_date and p.end_date
           and lower(coalesce(r.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف')
           and (
             r.plan_id=p.id
             or exists(select 1 from public.smart_plan_phases ph where ph.id=r.phase_id and ph.plan_id=p.id)
             or (r.plan_id is null and r.phase_id is null)
           )
      )
      select
        coalesce(sum(pages_count) filter(where activity_type::text='new_hifz'),0),
        coalesce(sum(pages_count) filter(where activity_type::text='sard'),0),
        coalesce(sum(pages_count) filter(where activity_type::text in ('tathbit','review_far')),0),
        round(avg(safety_percentage) filter(where safety_percentage is not null),1),
        (select jsonb_build_object('date',session_date,'start_surah',start_surah,'start_ayah',start_ayah,'end_surah',end_surah,'end_ayah',end_ayah,'pages',pages_count)
           from rec order by session_date,created_at limit 1),
        (select jsonb_build_object('date',session_date,'start_surah',start_surah,'start_ayah',start_ayah,'end_surah',end_surah,'end_ayah',end_ayah,'pages',pages_count)
           from rec order by session_date desc,created_at desc limit 1)
      into v_hifz,v_sard,v_tathbit,v_quality,v_first,v_last
      from rec;

      select coalesce(sum(coalesce(ps.target_pages_override,ps.frozen_target_pages,ph.target_pages,
                  case when ph.goal_mode='juz' then ph.target_units*20 else null end,0)) filter(where ph.phase_type='hifz'),0),
             coalesce(sum(coalesce(ps.target_pages_override,ps.frozen_target_pages,ph.target_pages,
                  case when ph.goal_mode='juz' then ph.target_units*20 else null end,0)) filter(where ph.phase_type='sard'),0)
        into v_target,v_sard_target
        from public.smart_plan_phases ph
        left join public.smart_plan_phase_states ps on ps.phase_id=ph.id and ps.student_id=s.id
       where ph.plan_id=p.id and ph.status<>'archived';

      if p.plan_type='tathbit' then
        v_target := coalesce(nullif(v_target,0),p.target_pages,0);
      else
        v_target := coalesce(nullif(v_target,0),p.target_pages,0);
      end if;

      select count(*) filter(where ar.status::text in ('present','late')),
             count(*) filter(where ar.status::text='absent')
        into v_present,v_absent
        from public.attendance_records ar
        join public.sessions se on se.id=ar.session_id and se.status<>'cancelled'
       where ar.student_id=s.id and se.session_date between p.start_date and p.end_date;

      insert into public.smart_plan_month_archives(
        student_id,halaqa_id,source_plan_id,period_month,plan_type,plan_title,
        target_pages,done_pages,sard_target_pages,sard_done_pages,
        attendance_present,attendance_absent,quality_percent,start_anchor,end_anchor,snapshot,created_by
      ) values (
        s.id,p.halaqa_id,p.id,v_period,p.plan_type,p.title,
        coalesce(v_target,0),case when p.plan_type='tathbit' then coalesce(v_tathbit,0) else coalesce(v_hifz,0) end,
        coalesce(v_sard_target,0),coalesce(v_sard,0),
        coalesce(v_present,0),coalesce(v_absent,0),v_quality,v_first,v_last,
        jsonb_build_object(
          'scope_type',p.scope_type,'category_id',p.category_id,'student_id',p.student_id,
          'start_date',p.start_date,'end_date',p.end_date,'status',p.status,
          'activities',case when p.plan_type='tathbit' then jsonb_build_array(
             jsonb_build_object('key','tathbit','target_pages',coalesce(v_target,0),'done_pages',coalesce(v_tathbit,0))
          ) else jsonb_build_array(
             jsonb_build_object('key','hifz','target_pages',coalesce(v_target,0),'done_pages',coalesce(v_hifz,0)),
             jsonb_build_object('key','sard','target_pages',coalesce(v_sard_target,0),'done_pages',coalesce(v_sard,0))
          ) end
        ),auth.uid()
      )
      on conflict(student_id,source_plan_id,period_month) do update set
        target_pages=excluded.target_pages,done_pages=excluded.done_pages,
        sard_target_pages=excluded.sard_target_pages,sard_done_pages=excluded.sard_done_pages,
        attendance_present=excluded.attendance_present,attendance_absent=excluded.attendance_absent,
        quality_percent=excluded.quality_percent,start_anchor=excluded.start_anchor,end_anchor=excluded.end_anchor,
        snapshot=excluded.snapshot,archived_at=now();
      v_count := v_count + 1;
    end loop;

    update public.smart_plans
       set status='completed', period_month=v_period, frozen_at=coalesce(frozen_at,now()),
           frozen_summary=coalesce(frozen_summary,'{}'::jsonb) || jsonb_build_object('rc18_archived',true,'archived_at',now()),
           updated_at=now(), revision=revision+1
     where id=p.id and status<>'archived';

    update public.smart_plan_phases
       set status='completed', completed_at=coalesce(completed_at,now()), updated_at=now(), revision=revision+1
     where plan_id=p.id and status='active' and end_date < v_month_start;
  end loop;

  return jsonb_build_object('archived_rows',v_count,'through',v_month_start-1);
end;
$$;

revoke all on function public.rc18_archive_finished_plans(date) from public, anon;
grant execute on function public.rc18_archive_finished_plans(date) to authenticated;

-- Clone the latest completed monthly plans into the requested month. Phase dates are shifted
-- by their offset from the old plan start, preserving Hifz/Sard structure and targets.
create or replace function public.rc18_start_month(p_month_start date default date_trunc('month',current_date)::date)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  oldp record;
  oldph record;
  new_plan_id uuid;
  new_phase_id uuid;
  v_month_start date := date_trunc('month',p_month_start)::date;
  v_month_end date := (date_trunc('month',p_month_start)+interval '1 month - 1 day')::date;
  v_created integer := 0;
  v_phases integer := 0;
  v_start date;
  v_end date;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  perform public.rc18_archive_finished_plans(v_month_start);

  for oldp in
    select distinct on (sp.scope_type,sp.halaqa_id,coalesce(sp.student_id,'00000000-0000-0000-0000-000000000000'::uuid),coalesce(sp.category_id,'00000000-0000-0000-0000-000000000000'::uuid),sp.plan_type)
           sp.*
      from public.smart_plans sp
     where coalesce(sp.legacy,false)=false
       and sp.parent_plan_id is null
       and sp.plan_type<>'tathbit'
       and sp.end_date < v_month_start
       and sp.status in ('completed','archived','paused','active')
       and (
         public.is_admin()
         or exists(select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=auth.uid())
         or exists(select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=auth.uid())
       )
     order by sp.scope_type,sp.halaqa_id,coalesce(sp.student_id,'00000000-0000-0000-0000-000000000000'::uuid),coalesce(sp.category_id,'00000000-0000-0000-0000-000000000000'::uuid),sp.plan_type,sp.end_date desc
  loop
    if exists(
      select 1 from public.smart_plans x
       where coalesce(x.legacy,false)=false
         and x.scope_type=oldp.scope_type and x.halaqa_id=oldp.halaqa_id
         and x.student_id is not distinct from oldp.student_id
         and x.category_id is not distinct from oldp.category_id
         and x.plan_type=oldp.plan_type
         and v_month_start between x.start_date and x.end_date
         and x.status<>'archived'
    ) then continue; end if;

    insert into public.smart_plans(
      scope_type,halaqa_id,student_id,category_id,parent_plan_id,plan_type,review_tier,title,
      goal_unit,target_units,target_pages,start_date,end_date,schedule_weekdays,catch_up_weekday,
      max_daily_multiplier,auto_extend,quality_gate_enabled,minimum_evaluation,
      range_start_page,range_end_page,range_start_surah,range_start_ayah,range_end_surah,range_end_ayah,
      selected_surahs,selected_juz,selected_rubs,near_revision_pages,far_revision_pages,
      milestone_mode,milestone_requires_approval,delay_alert_days,home_prep_push,status,note,settings,
      period_month,legacy,created_by
    ) values (
      oldp.scope_type,oldp.halaqa_id,oldp.student_id,oldp.category_id,null,oldp.plan_type,oldp.review_tier,
      regexp_replace(oldp.title,'\\s*[·-]?\\s*\\d{4}[-/]\\d{1,2}\\s*$','') || ' · ' || to_char(v_month_start,'YYYY-MM'),
      oldp.goal_unit,oldp.target_units,oldp.target_pages,v_month_start,v_month_end,oldp.schedule_weekdays,oldp.catch_up_weekday,
      oldp.max_daily_multiplier,oldp.auto_extend,oldp.quality_gate_enabled,oldp.minimum_evaluation,
      oldp.range_start_page,oldp.range_end_page,oldp.range_start_surah,oldp.range_start_ayah,oldp.range_end_surah,oldp.range_end_ayah,
      oldp.selected_surahs,oldp.selected_juz,oldp.selected_rubs,oldp.near_revision_pages,oldp.far_revision_pages,
      oldp.milestone_mode,oldp.milestone_requires_approval,oldp.delay_alert_days,oldp.home_prep_push,'active',oldp.note,oldp.settings,
      v_month_start,false,auth.uid()
    ) returning id into new_plan_id;
    v_created := v_created+1;

    for oldph in select * from public.smart_plan_phases where plan_id=oldp.id and status<>'archived' order by phase_order
    loop
      v_start := least(v_month_end, v_month_start + greatest(0,(oldph.start_date-oldp.start_date)));
      v_end := least(v_month_end, v_month_start + greatest(0,(oldph.end_date-oldp.start_date)));
      if v_end < v_start then v_end := v_start; end if;
      insert into public.smart_plan_phases(
        plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,goal_mode,goal_unit,
        target_units,target_pages,distribution_mode,start_mode,range_start_page,range_end_page,
        range_start_surah,range_start_ayah,range_end_surah,range_end_ayah,allow_surplus,status,settings,created_by
      ) values (
        new_plan_id,oldph.phase_order,oldph.phase_type,oldph.title,v_start,v_end,oldph.schedule_weekdays,oldph.goal_mode,oldph.goal_unit,
        oldph.target_units,oldph.target_pages,oldph.distribution_mode,oldph.start_mode,oldph.range_start_page,oldph.range_end_page,
        oldph.range_start_surah,oldph.range_start_ayah,oldph.range_end_surah,oldph.range_end_ayah,oldph.allow_surplus,'active',oldph.settings,auth.uid()
      ) returning id into new_phase_id;
      v_phases := v_phases+1;

      insert into public.smart_plan_phase_states(
        phase_id,student_id,status,target_pages_override,carry_shortfall_pages,settings,updated_by
      )
      select new_phase_id,ps.student_id,'not_started',ps.target_pages_override,0,ps.settings,auth.uid()
        from public.smart_plan_phase_states ps where ps.phase_id=oldph.id
      on conflict(phase_id,student_id) do nothing;
    end loop;
  end loop;

  -- Tathbit already has a dedicated monthly roller in RC15.2.
  if to_regprocedure('public.rc152_roll_tathbit_months()') is not null then
    perform public.rc152_roll_tathbit_months();
  end if;

  return jsonb_build_object('month',v_month_start,'created_plans',v_created,'created_phases',v_phases);
end;
$$;

revoke all on function public.rc18_start_month(date) from public, anon;
grant execute on function public.rc18_start_month(date) to authenticated;

create or replace function public.student_plan_month_history(p_student_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare v_result jsonb;
begin
  if not public.rc18_can_view_student(p_student_id) then
    raise exception 'student access denied';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object(
      'id',a.id,'period_month',a.period_month,'plan_type',a.plan_type,'title',a.plan_title,
      'target_pages',a.target_pages,'done_pages',a.done_pages,
      'sard_target_pages',a.sard_target_pages,'sard_done_pages',a.sard_done_pages,
      'present',a.attendance_present,'absent',a.attendance_absent,'quality',a.quality_percent,
      'start_anchor',a.start_anchor,'end_anchor',a.end_anchor,'snapshot',a.snapshot,'archived_at',a.archived_at
    ) order by a.period_month desc,a.archived_at desc),'[]'::jsonb)
    into v_result from public.smart_plan_month_archives a where a.student_id=p_student_id;
  return v_result;
end;
$$;

revoke all on function public.student_plan_month_history(uuid) from public, anon;
grant execute on function public.student_plan_month_history(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 3) Real achievement/badge engine. Awards are evidence-backed and reproducible.
-- ---------------------------------------------------------------------------
create table if not exists public.achievement_badge_definitions (
  code text primary key,
  title text not null,
  description text not null,
  category text not null,
  icon_key text not null default 'award',
  active boolean not null default true,
  created_at timestamptz not null default now()
);

insert into public.achievement_badge_definitions(code,title,description,category,icon_key) values
 ('plan_complete','منجز الخطة','أتم الهدف الشهري الفعلي للخطة، بما في ذلك السرد عندما يكون له هدف.','plan','verified'),
 ('hifz_20','همة الحفظ','أنجز 20 صفحة حفظ أو أكثر خلال الشهر.','hifz','menu_book'),
 ('sard_20','قوة السرد','أنجز 20 صفحة سرد أو أكثر خلال الشهر.','sard','auto_stories'),
 ('review_40','ثبات المراجعة','أنجز 40 صفحة مراجعة أو أكثر خلال الشهر.','review','replay'),
 ('tathbit_20','رسوخ التثبيت','أنجز 20 صفحة تثبيت أو أكثر خلال الشهر.','tathbit','menu_book'),
 ('attendance_perfect','مواظبة متميزة','حضر 8 لقاءات على الأقل بلا غياب غير معذور خلال الشهر.','attendance','event_available'),
 ('quality_95','إتقان 95%','حقق متوسط جودة 95% أو أكثر في 4 تسميعات مقيمة على الأقل.','quality','star'),
 ('consistency_8','استمرارية','سجّل تسميعًا في 8 أيام مختلفة أو أكثر خلال الشهر.','consistency','local_fire_department')
on conflict(code) do update set title=excluded.title,description=excluded.description,category=excluded.category,icon_key=excluded.icon_key,active=true;

create table if not exists public.student_badge_awards (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.students(id) on delete cascade,
  badge_code text not null references public.achievement_badge_definitions(code) on delete restrict,
  period_month date not null,
  evidence jsonb not null default '{}'::jsonb,
  awarded_at timestamptz not null default now(),
  unique(student_id,badge_code,period_month)
);
create index if not exists student_badge_awards_student_month_idx
  on public.student_badge_awards(student_id,period_month desc,awarded_at desc);

alter table public.achievement_badge_definitions enable row level security;
alter table public.student_badge_awards enable row level security;
revoke all on public.achievement_badge_definitions from anon;
revoke all on public.student_badge_awards from anon;
grant select on public.achievement_badge_definitions to authenticated;
grant select on public.student_badge_awards to authenticated;

drop policy if exists rc18_badge_definitions_read on public.achievement_badge_definitions;
create policy rc18_badge_definitions_read on public.achievement_badge_definitions
for select to authenticated using(active);

drop policy if exists rc18_badge_awards_read on public.student_badge_awards;
create policy rc18_badge_awards_read on public.student_badge_awards
for select to authenticated using(public.rc18_can_view_student(student_id));

create or replace function public.rc18_refresh_student_badges(p_student_id uuid,p_month date default date_trunc('month',current_date)::date)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_month date := date_trunc('month',p_month)::date;
  v_end date := (date_trunc('month',p_month)+interval '1 month - 1 day')::date;
  v_track text;
  v_hifz numeric := 0; v_review numeric := 0; v_sard numeric := 0; v_tathbit numeric := 0;
  v_days integer := 0; v_rec_count integer := 0; v_quality numeric; v_present integer := 0; v_absent integer := 0;
  v_target numeric := 0; v_sard_target numeric := 0; v_plan_done numeric := 0;
  v_result jsonb;
begin
  if not public.rc18_can_view_student(p_student_id) then raise exception 'student access denied'; end if;
  select s.track_code into v_track from public.students s where s.id=p_student_id;
  if v_track is null then raise exception 'student not found'; end if;

  with rec as (
    select r.*,se.session_date
      from public.recitation_entries r
      join public.sessions se on se.id=r.session_id and se.status<>'cancelled'
     where r.student_id=p_student_id and se.session_date between v_month and v_end
       and lower(coalesce(r.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف')
  )
  select coalesce(sum(pages_count) filter(where activity_type::text='new_hifz'),0),
         coalesce(sum(pages_count) filter(where activity_type::text in ('review','review_near','review_far','tilawah')),0),
         coalesce(sum(pages_count) filter(where activity_type::text='sard'),0),
         coalesce(sum(pages_count) filter(where activity_type::text in ('tathbit','review_far')),0),
         count(distinct session_date),count(*),
         round(avg(safety_percentage) filter(where safety_percentage is not null),1)
    into v_hifz,v_review,v_sard,v_tathbit,v_days,v_rec_count,v_quality from rec;

  select count(*) filter(where ar.status::text in ('present','late')),
         count(*) filter(where ar.status::text='absent')
    into v_present,v_absent
    from public.attendance_records ar join public.sessions se on se.id=ar.session_id and se.status<>'cancelled'
   where ar.student_id=p_student_id and se.session_date between v_month and v_end;

  -- Most-specific monthly plan, same precedence as guardian_child_overview.
  with st as (select * from public.students where id=p_student_id), p as (
    select sp.* from public.smart_plans sp,st
     where coalesce(sp.legacy,false)=false and sp.status in ('active','completed','paused')
       and sp.start_date<=v_end and sp.end_date>=v_month
       and ((st.track_code='tathbit')=(sp.plan_type='tathbit'))
       and (sp.student_id=p_student_id
         or (sp.scope_type='category' and sp.category_id=st.category_id and sp.halaqa_id=st.halaqa_id)
         or (sp.scope_type='halaqa' and sp.halaqa_id=st.halaqa_id))
     order by case when sp.student_id=p_student_id then 3 when sp.scope_type='category' then 2 else 1 end desc,sp.start_date desc limit 1
  )
  select coalesce(p.target_pages,0),
         coalesce((select sum(coalesce(ph.target_pages,case when ph.goal_mode='juz' then ph.target_units*20 else 0 end,0)) from public.smart_plan_phases ph where ph.plan_id=p.id and ph.phase_type='sard' and ph.status<>'archived'),0)
    into v_target,v_sard_target from p;

  v_plan_done := case when v_track='tathbit' then v_tathbit else v_hifz end;

  if v_target>0 and v_plan_done>=v_target and (v_sard_target<=0 or v_sard>=v_sard_target) then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence)
    values(p_student_id,'plan_complete',v_month,jsonb_build_object('target',v_target,'done',v_plan_done,'sard_target',v_sard_target,'sard_done',v_sard))
    on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;
  if v_hifz>=20 and v_track<>'tathbit' then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence) values(p_student_id,'hifz_20',v_month,jsonb_build_object('pages',v_hifz)) on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;
  if v_sard>=20 and v_track<>'tathbit' then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence) values(p_student_id,'sard_20',v_month,jsonb_build_object('pages',v_sard)) on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;
  if v_review>=40 and v_track<>'tathbit' then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence) values(p_student_id,'review_40',v_month,jsonb_build_object('pages',v_review)) on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;
  if v_tathbit>=20 and v_track='tathbit' then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence) values(p_student_id,'tathbit_20',v_month,jsonb_build_object('pages',v_tathbit)) on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;
  if v_present>=8 and v_absent=0 then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence) values(p_student_id,'attendance_perfect',v_month,jsonb_build_object('present',v_present,'absent',v_absent)) on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;
  if coalesce(v_quality,0)>=95 and v_rec_count>=4 then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence) values(p_student_id,'quality_95',v_month,jsonb_build_object('quality',v_quality,'recitations',v_rec_count)) on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;
  if v_days>=8 then
    insert into public.student_badge_awards(student_id,badge_code,period_month,evidence) values(p_student_id,'consistency_8',v_month,jsonb_build_object('days',v_days)) on conflict(student_id,badge_code,period_month) do update set evidence=excluded.evidence;
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
      'code',a.badge_code,'title',d.title,'description',d.description,'category',d.category,'icon_key',d.icon_key,
      'period_month',a.period_month,'awarded_at',a.awarded_at,'evidence',a.evidence
    ) order by a.period_month desc,a.awarded_at desc),'[]'::jsonb)
    into v_result
    from public.student_badge_awards a join public.achievement_badge_definitions d on d.code=a.badge_code
   where a.student_id=p_student_id and d.active;
  return v_result;
end;
$$;

revoke all on function public.rc18_refresh_student_badges(uuid,date) from public, anon;
grant execute on function public.rc18_refresh_student_badges(uuid,date) to authenticated;

-- ---------------------------------------------------------------------------
-- 4) Lightweight audit trail for high-impact administrative edits
-- ---------------------------------------------------------------------------
create table if not exists public.management_audit_log (
  id bigserial primary key,
  actor_id uuid default auth.uid() references public.profiles(id) on delete set null,
  entity_table text not null,
  entity_id text,
  action text not null,
  before_data jsonb,
  after_data jsonb,
  created_at timestamptz not null default now()
);
create index if not exists management_audit_log_entity_idx on public.management_audit_log(entity_table,entity_id,created_at desc);
create index if not exists management_audit_log_actor_idx on public.management_audit_log(actor_id,created_at desc);
alter table public.management_audit_log enable row level security;
revoke all on public.management_audit_log from anon, authenticated;
grant select on public.management_audit_log to authenticated;
drop policy if exists rc18_audit_admin_read on public.management_audit_log;
create policy rc18_audit_admin_read on public.management_audit_log for select to authenticated using(public.is_admin());

create or replace function public.rc18_audit_management_mutation()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  insert into public.management_audit_log(actor_id,entity_table,entity_id,action,before_data,after_data)
  values(auth.uid(),tg_table_name,coalesce(to_jsonb(new)->>'id',to_jsonb(old)->>'id'),tg_op,
         case when tg_op in ('UPDATE','DELETE') then to_jsonb(old) else null end,
         case when tg_op in ('INSERT','UPDATE') then to_jsonb(new) else null end);
  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end $$;

-- Attach only to stable core tables; repeated migration stays idempotent.
do $$
begin
  if to_regclass('public.halaqas') is not null then
    execute 'drop trigger if exists rc18_audit_halaqas on public.halaqas';
    execute 'create trigger rc18_audit_halaqas after insert or update or delete on public.halaqas for each row execute function public.rc18_audit_management_mutation()';
  end if;
  if to_regclass('public.smart_plans') is not null then
    execute 'drop trigger if exists rc18_audit_smart_plans on public.smart_plans';
    execute 'create trigger rc18_audit_smart_plans after insert or update or delete on public.smart_plans for each row execute function public.rc18_audit_management_mutation()';
  end if;
  if to_regclass('public.student_categories') is not null then
    execute 'drop trigger if exists rc18_audit_categories on public.student_categories';
    execute 'create trigger rc18_audit_categories after insert or update or delete on public.student_categories for each row execute function public.rc18_audit_management_mutation()';
  end if;
  if to_regclass('public.announcements') is not null then
    execute 'drop trigger if exists rc18_audit_announcements on public.announcements';
    execute 'create trigger rc18_audit_announcements after insert or update or delete on public.announcements for each row execute function public.rc18_audit_management_mutation()';
  end if;
end $$;

commit;

-- Optional one-time verification after applying:
-- select public.guardian_children(); -- as guardian: own linked children only
-- select public.rc18_start_month(date_trunc('month',current_date)::date); -- as manager
