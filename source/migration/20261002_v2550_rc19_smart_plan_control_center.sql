-- Halaqati v2.55.0 RC19 — Smart Plan Control Center
-- Run AFTER 20261002_v2542_rc18_2_session_monthly_continuity.sql.
-- Additive/idempotent. Repairs current-month phase state, adds plan-health history,
-- and provides a verified monthly create/repair entry point.

begin;

-- ---------------------------------------------------------------------------
-- 1) Daily plan-health snapshots used by the blue/green/yellow/red health engine.
-- ---------------------------------------------------------------------------
create table if not exists public.smart_plan_health_daily (
  plan_id uuid not null references public.smart_plans(id) on delete cascade,
  student_id uuid not null references public.students(id) on delete cascade,
  day date not null,
  scheduled boolean not null default false,
  health text not null check (health in ('blue','green','yellow','red','gray')),
  ratio numeric not null default 1,
  expected_pages numeric not null default 0,
  completed_pages numeric not null default 0,
  behind_pages numeric not null default 0,
  notification_sent_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(plan_id,student_id,day)
);
create index if not exists smart_plan_health_daily_student_day_idx
  on public.smart_plan_health_daily(student_id,day desc);

alter table public.smart_plan_health_daily enable row level security;
revoke all on public.smart_plan_health_daily from anon;
revoke insert,update,delete on public.smart_plan_health_daily from authenticated;
grant select on public.smart_plan_health_daily to authenticated;

drop policy if exists rc19_health_select on public.smart_plan_health_daily;
create policy rc19_health_select on public.smart_plan_health_daily
for select to authenticated
using (public.rc18_can_view_student(student_id));

create or replace function public.rc19_record_plan_health(
  p_plan_id uuid,
  p_student_id uuid,
  p_day date,
  p_scheduled boolean,
  p_health text,
  p_ratio numeric,
  p_expected_pages numeric,
  p_completed_pages numeric,
  p_behind_pages numeric
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_halaqa uuid;
  r record;
  v_streak integer := 0;
  v_episode_start date := p_day;
  v_last_notified timestamptz;
  v_should boolean := false;
  v_lag numeric := greatest(0,(1-coalesce(p_ratio,1))*100);
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  if p_day is null or p_plan_id is null or p_student_id is null then raise exception 'health snapshot arguments required'; end if;
  if p_health not in ('blue','green','yellow','red','gray') then raise exception 'invalid plan health'; end if;

  select s.halaqa_id into v_halaqa from public.students s where s.id=p_student_id;
  if v_halaqa is null or not public.rc182_can_manage_halaqa(v_halaqa) then
    raise exception 'plan health management denied';
  end if;
  if not exists (
    select 1 from public.smart_plans sp
    where sp.id=p_plan_id and (
      sp.student_id=p_student_id
      or (sp.scope_type='category' and sp.category_id=(select category_id from public.students where id=p_student_id) and sp.halaqa_id=v_halaqa)
      or (sp.scope_type='halaqa' and sp.halaqa_id=v_halaqa)
    )
  ) then raise exception 'plan/student mismatch'; end if;

  insert into public.smart_plan_health_daily(
    plan_id,student_id,day,scheduled,health,ratio,expected_pages,completed_pages,behind_pages,updated_at
  ) values (
    p_plan_id,p_student_id,p_day,coalesce(p_scheduled,false),p_health,coalesce(p_ratio,1),
    greatest(coalesce(p_expected_pages,0),0),greatest(coalesce(p_completed_pages,0),0),greatest(coalesce(p_behind_pages,0),0),now()
  )
  on conflict(plan_id,student_id,day) do update set
    scheduled=excluded.scheduled,health=excluded.health,ratio=excluded.ratio,
    expected_pages=excluded.expected_pages,completed_pages=excluded.completed_pages,
    behind_pages=excluded.behind_pages,updated_at=now();

  if coalesce(p_scheduled,false) and p_health='red' then
    for r in
      select day,health from public.smart_plan_health_daily
      where plan_id=p_plan_id and student_id=p_student_id and scheduled=true and day<=p_day
      order by day desc limit 31
    loop
      exit when r.health <> 'red';
      v_streak := v_streak+1;
      v_episode_start := r.day;
    end loop;
  end if;

  select max(notification_sent_at) into v_last_notified
  from public.smart_plan_health_daily
  where plan_id=p_plan_id and student_id=p_student_id and notification_sent_at is not null;

  v_should := v_streak>=2 and (v_last_notified is null or v_last_notified::date <= p_day-3);
  return jsonb_build_object(
    'health',p_health,'ratio',coalesce(p_ratio,1),'lag_percent',round(v_lag,1),
    'red_streak',v_streak,'should_notify',v_should,
    'episode_key',to_char(v_episode_start,'YYYY-MM-DD')
  );
end;
$$;
revoke all on function public.rc19_record_plan_health(uuid,uuid,date,boolean,text,numeric,numeric,numeric,numeric) from public,anon;
grant execute on function public.rc19_record_plan_health(uuid,uuid,date,boolean,text,numeric,numeric,numeric,numeric) to authenticated;

create or replace function public.rc19_mark_plan_health_notified(
  p_plan_id uuid,p_student_id uuid,p_day date
) returns void
language plpgsql
security definer
set search_path=public
as $$
declare v_halaqa uuid;
begin
  select halaqa_id into v_halaqa from public.students where id=p_student_id;
  if auth.uid() is null or v_halaqa is null or not public.rc182_can_manage_halaqa(v_halaqa) then
    raise exception 'plan health management denied';
  end if;
  update public.smart_plan_health_daily set notification_sent_at=now(),updated_at=now()
   where plan_id=p_plan_id and student_id=p_student_id and day=p_day;
end;
$$;
revoke all on function public.rc19_mark_plan_health_notified(uuid,uuid,date) from public,anon;
grant execute on function public.rc19_mark_plan_health_notified(uuid,uuid,date) to authenticated;

-- ---------------------------------------------------------------------------
-- 2) Verified current-month repair/create engine.
--    rc182 creates missing student plans; RC19 then repairs EVERY current plan,
--    including plans that RC18.2 previously skipped because they were already marked generated.
-- ---------------------------------------------------------------------------
create or replace function public.rc19_start_month(p_month_start date default date_trunc('month',current_date)::date)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  v_base jsonb;
  st record;
  p public.smart_plans%rowtype;
  dup record;
  v_month_start date := date_trunc('month',p_month_start)::date;
  v_month_end date := (date_trunc('month',p_month_start)+interval '1 month - 1 day')::date;
  v_hifz_end date;
  v_sard_start date;
  v_hifz_phase uuid;
  v_sard_phase uuid;
  v_hifz_target numeric;
  v_hifz_done numeric;
  v_sard_done numeric;
  v_hifz_state text;
  v_sard_state text;
  v_students integer := 0;
  v_repaired_phases integer := 0;
  v_archived_duplicates integer := 0;
  v_missing integer := 0;
  v_verified integer := 0;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  v_hifz_end := least(v_month_end,v_month_start+21);
  v_sard_start := least(v_month_end,v_month_start+22);

  -- First create any missing individualized plans using the already deployed RC18.2 inheritance logic.
  v_base := public.rc182_start_month(v_month_start);

  for st in
    select s.* from public.students s
    where s.status='active' and s.track_code<>'tathbit' and s.halaqa_id is not null
      and public.rc182_can_manage_halaqa(s.halaqa_id)
    order by s.halaqa_id,s.full_name
  loop
    v_students := v_students+1;
    v_hifz_phase := null; v_sard_phase := null;

    select sp.* into p from public.smart_plans sp
     where coalesce(sp.legacy,false)=false and sp.scope_type='student' and sp.student_id=st.id
       and sp.plan_type<>'tathbit' and sp.status<>'archived'
       and sp.start_date<=v_month_end and sp.end_date>=v_month_start
     order by sp.updated_at desc,sp.created_at desc limit 1;

    if p.id is null then
      v_missing := v_missing+1;
      continue;
    end if;

    -- Current month must have one authoritative student plan. Archive stale competitors,
    -- but never delete history or recitations.
    for dup in
      select sp.id from public.smart_plans sp
      where sp.id<>p.id and coalesce(sp.legacy,false)=false and sp.scope_type='student'
        and sp.student_id=st.id and sp.plan_type<>'tathbit' and sp.status<>'archived'
        and sp.start_date<=v_month_end and sp.end_date>=v_month_start
    loop
      update public.smart_plans set status='archived',updated_at=now(),revision=revision+1 where id=dup.id;
      update public.smart_plan_phases set status='archived',updated_at=now(),revision=revision+1 where plan_id=dup.id and status<>'archived';
      v_archived_duplicates:=v_archived_duplicates+1;
    end loop;

    select ph.id,coalesce(ps.target_pages_override,ph.target_pages,p.target_pages)
      into v_hifz_phase,v_hifz_target
      from public.smart_plan_phases ph
      left join public.smart_plan_phase_states ps on ps.phase_id=ph.id and ps.student_id=st.id
     where ph.plan_id=p.id and ph.phase_type='hifz' and ph.status<>'archived'
     order by ph.phase_order limit 1;
    v_hifz_target:=greatest(coalesce(v_hifz_target,p.target_pages,25),0.01);

    if v_hifz_phase is null then
      insert into public.smart_plan_phases(
        plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,goal_mode,goal_unit,
        target_units,target_pages,distribution_mode,start_mode,allow_surplus,status,settings,created_by
      ) values (
        p.id,1,'hifz','مرحلة الحفظ',v_month_start,v_hifz_end,coalesce(p.schedule_weekdays,array[0,1,2,3,4]::smallint[]),
        'pages','pages',v_hifz_target,v_hifz_target,'flexible','last_activity',true,'active',
        jsonb_build_object('rc19_monthly_generated',true,'continuation_mode','last_activity'),auth.uid()
      ) returning id into v_hifz_phase;
      v_repaired_phases:=v_repaired_phases+1;
    else
      update public.smart_plan_phases set
        phase_order=1,title='مرحلة الحفظ',start_date=v_month_start,end_date=v_hifz_end,
        schedule_weekdays=coalesce(nullif(schedule_weekdays,'{}'::smallint[]),coalesce(p.schedule_weekdays,array[0,1,2,3,4]::smallint[])),
        goal_mode='pages',goal_unit='pages',target_units=v_hifz_target,target_pages=v_hifz_target,
        distribution_mode='flexible',start_mode='last_activity',status='active',completed_at=null,
        settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object('rc19_monthly_generated',true,'continuation_mode','last_activity'),
        updated_at=now(),revision=revision+1 where id=v_hifz_phase;
      v_repaired_phases:=v_repaired_phases+1;
    end if;

    select ph.id into v_sard_phase from public.smart_plan_phases ph
     where ph.plan_id=p.id and ph.phase_type='sard' and ph.status<>'archived'
     order by ph.phase_order limit 1;
    if v_sard_phase is null then
      insert into public.smart_plan_phases(
        plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,goal_mode,goal_unit,
        target_units,target_pages,distribution_mode,start_mode,allow_surplus,status,settings,created_by
      ) values (
        p.id,2,'sard','مرحلة السرد',v_sard_start,v_month_end,array[5]::smallint[],
        'pages','pages',25,25,'flexible','last_activity',true,'active',
        jsonb_build_object('rc19_monthly_generated',true,'fixed_sard_target',25,'continuation_mode','last_activity'),auth.uid()
      ) returning id into v_sard_phase;
      v_repaired_phases:=v_repaired_phases+1;
    else
      update public.smart_plan_phases set
        phase_order=2,title='مرحلة السرد',start_date=v_sard_start,end_date=v_month_end,
        schedule_weekdays=array[5]::smallint[],goal_mode='pages',goal_unit='pages',target_units=25,target_pages=25,
        distribution_mode='flexible',start_mode='last_activity',status='active',completed_at=null,
        settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object('rc19_monthly_generated',true,'fixed_sard_target',25,'continuation_mode','last_activity'),
        updated_at=now(),revision=revision+1 where id=v_sard_phase;
      v_repaired_phases:=v_repaired_phases+1;
    end if;

    select coalesce(sum(re.pages_count),0) into v_hifz_done
      from public.recitation_entries re join public.sessions se on se.id=re.session_id
     where re.student_id=st.id and se.session_date between v_month_start and v_hifz_end and se.status<>'cancelled'
       and re.activity_type::text in ('new_hifz','hifz')
       and lower(coalesce(re.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف');
    select coalesce(sum(re.pages_count),0) into v_sard_done
      from public.recitation_entries re join public.sessions se on se.id=re.session_id
     where re.student_id=st.id and se.session_date between v_sard_start and v_month_end and se.status<>'cancelled'
       and re.activity_type::text='sard'
       and lower(coalesce(re.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف');

    v_hifz_state := case
      when v_hifz_done>=v_hifz_target then 'completed'
      when current_date<v_month_start then 'not_started'
      when current_date between v_month_start and v_hifz_end and v_hifz_done>0 then 'active'
      when current_date between v_month_start and v_hifz_end then 'not_started'
      else 'active'
    end;
    v_sard_state := case
      when v_sard_done>=25 then 'completed'
      when current_date<v_sard_start then 'not_started'
      when current_date between v_sard_start and v_month_end and v_sard_done>0 then 'active'
      when current_date between v_sard_start and v_month_end then 'not_started'
      else 'active'
    end;

    insert into public.smart_plan_phase_states(phase_id,student_id,status,target_pages_override,carry_shortfall_pages,settings,updated_by)
    values(v_hifz_phase,st.id,v_hifz_state,null,0,jsonb_build_object('rc19_repaired',true),auth.uid())
    on conflict(phase_id,student_id) do update set
      status=v_hifz_state,
      frozen_completed_pages=case when v_hifz_state='completed' then greatest(coalesce(public.smart_plan_phase_states.frozen_completed_pages,0),v_hifz_done) else null end,
      frozen_target_pages=case when v_hifz_state='completed' then v_hifz_target else null end,
      completion_percent=case when v_hifz_target>0 then round(least(100,v_hifz_done/v_hifz_target*100),1) else null end,
      completed_at=case when v_hifz_state='completed' then coalesce(public.smart_plan_phase_states.completed_at,now()) else null end,
      updated_by=auth.uid(),updated_at=now(),settings=coalesce(public.smart_plan_phase_states.settings,'{}'::jsonb)||jsonb_build_object('rc19_repaired',true);

    insert into public.smart_plan_phase_states(phase_id,student_id,status,target_pages_override,carry_shortfall_pages,settings,updated_by)
    values(v_sard_phase,st.id,v_sard_state,null,0,jsonb_build_object('rc19_repaired',true,'fixed_sard_target',25),auth.uid())
    on conflict(phase_id,student_id) do update set
      status=v_sard_state,target_pages_override=null,
      frozen_completed_pages=case when v_sard_state='completed' then greatest(coalesce(public.smart_plan_phase_states.frozen_completed_pages,0),v_sard_done) else null end,
      frozen_target_pages=case when v_sard_state='completed' then 25 else null end,
      completion_percent=round(least(100,v_sard_done/25*100),1),
      completed_at=case when v_sard_state='completed' then coalesce(public.smart_plan_phase_states.completed_at,now()) else null end,
      updated_by=auth.uid(),updated_at=now(),settings=coalesce(public.smart_plan_phase_states.settings,'{}'::jsonb)||jsonb_build_object('rc19_repaired',true,'fixed_sard_target',25);

    update public.smart_plans set
      start_date=v_month_start,end_date=v_month_end,period_month=v_month_start,target_pages=v_hifz_target,target_units=v_hifz_target,
      status=case when status='paused' then 'paused' else 'active' end,
      settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object(
        'rc19_monthly_generated',true,'rc19_hifz_start_day',1,'rc19_hifz_end_day',22,
        'rc19_sard_start_day',23,'rc19_sard_target_pages',25,'sard_target_pages',25,'monthly_hifz_target_pages',v_hifz_target
      ),updated_at=now(),revision=revision+1
     where id=p.id;

    if exists(select 1 from public.smart_plan_phases where id=v_hifz_phase and start_date=v_month_start and end_date=v_hifz_end and target_pages=v_hifz_target and status='active')
       and exists(select 1 from public.smart_plan_phases where id=v_sard_phase and start_date=v_sard_start and end_date=v_month_end and target_pages=25 and status='active') then
      v_verified:=v_verified+1;
    end if;
  end loop;

  return coalesce(v_base,'{}'::jsonb) || jsonb_build_object(
    'rc19',true,'month',v_month_start,'month_end',v_month_end,'hifz_end',v_hifz_end,'sard_start',v_sard_start,
    'students_checked',v_students,'verified_students',v_verified,'repaired_phases',v_repaired_phases,
    'archived_duplicate_plans',v_archived_duplicates,'missing_students',v_missing,'sard_target_pages',25
  );
end;
$$;
revoke all on function public.rc19_start_month(date) from public,anon;
grant execute on function public.rc19_start_month(date) to authenticated;

-- Older RC18 clients that call rc18_start_month get the repaired behavior after migration.
create or replace function public.rc18_start_month(p_month_start date default date_trunc('month',current_date)::date)
returns jsonb language sql security definer set search_path=public
as $$ select public.rc19_start_month(p_month_start); $$;
revoke all on function public.rc18_start_month(date) from public,anon;
grant execute on function public.rc18_start_month(date) to authenticated;

commit;
