-- Halaqati v2.54.2 RC18.2 — Session Editing + Individual Monthly Continuity
-- Run AFTER 20261002_v2540_rc18_privacy_monthly_achievements.sql.
-- Additive / idempotent. Preserves recitation history and manual plan edits.

begin;

-- ---------------------------------------------------------------------------
-- 0) Compatibility / schema safety
-- ---------------------------------------------------------------------------
alter table public.sessions add column if not exists notes text;

-- RC17.1 already introduced last_activity. Re-assert it so this migration is safe
-- even if a deployment skipped that constraint update but otherwise has RC18 objects.
alter table public.smart_plan_phases
  drop constraint if exists smart_plan_phases_start_mode_check;
alter table public.smart_plan_phases
  add constraint smart_plan_phases_start_mode_check
  check (start_mode in ('plan_start','last_sard','last_activity','manual','range_start'));

-- ---------------------------------------------------------------------------
-- 1) Halaqa management authorization helper
-- ---------------------------------------------------------------------------
create or replace function public.rc182_can_manage_halaqa(p_halaqa_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() is not null and p_halaqa_id is not null and (
    public.is_admin()
    or exists (
      select 1 from public.halaqa_teachers ht
      where ht.halaqa_id = p_halaqa_id and ht.teacher_id = auth.uid()
    )
    or exists (
      select 1 from public.halaqa_supervisors hs
      where hs.halaqa_id = p_halaqa_id and hs.supervisor_id = auth.uid()
    )
  );
$$;
revoke all on function public.rc182_can_manage_halaqa(uuid) from public, anon;
grant execute on function public.rc182_can_manage_halaqa(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 2) Preserve session metadata while archiving/restoring
-- ---------------------------------------------------------------------------
create or replace function public.rc182_set_session_archived(
  p_session_id uuid,
  p_archived boolean
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_old public.sessions%rowtype;
  v_activity jsonb;
  v_restore text;
  v_status_type text;
  v_result jsonb;
begin
  select * into v_old from public.sessions where id = p_session_id for update;
  if not found then raise exception 'session not found'; end if;
  if not public.rc182_can_manage_halaqa(v_old.halaqa_id) then raise exception 'session access denied'; end if;

  v_activity := coalesce(v_old.activity_data, '{}'::jsonb);
  v_status_type := pg_typeof(v_old.status)::text;

  if coalesce(p_archived,false) then
    if not (v_activity ? 'pre_archive_status') then
      v_activity := jsonb_set(v_activity, '{pre_archive_status}', to_jsonb(v_old.status::text), true);
    end if;
    v_activity := jsonb_set(v_activity, '{archived}', 'true'::jsonb, true);
    execute format(
      'update public.sessions set status = $1::%s, activity_data = $2 where id = $3',
      v_status_type
    ) using 'closed', v_activity, p_session_id;
  else
    v_restore := nullif(v_activity->>'pre_archive_status','');
    if v_restore not in ('open','planned','closed') then v_restore := 'closed'; end if;
    v_activity := (v_activity - 'pre_archive_status') || jsonb_build_object('archived',false);
    execute format(
      'update public.sessions set status = $1::%s, activity_data = $2 where id = $3',
      v_status_type
    ) using v_restore, v_activity, p_session_id;
  end if;

  select to_jsonb(s) into v_result from public.sessions s where s.id = p_session_id;
  return v_result;
end;
$$;
revoke all on function public.rc182_set_session_archived(uuid,boolean) from public, anon;
grant execute on function public.rc182_set_session_archived(uuid,boolean) to authenticated;

-- ---------------------------------------------------------------------------
-- 3) Rebind one recitation after a session-date edit or a monthly-plan repair.
--    The recitation fact itself is not changed; only its plan/phase pointers are.
-- ---------------------------------------------------------------------------
create or replace function public.rc182_rebind_recitation(p_recitation_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
  st record;
  v_plan_id uuid;
  v_phase_id uuid;
  v_phase_type text;
  v_result jsonb;
begin
  select re.id, re.student_id, re.activity_type::text as activity_type,
         se.session_date, se.halaqa_id
    into r
    from public.recitation_entries re
    join public.sessions se on se.id = re.session_id
   where re.id = p_recitation_id;
  if r.id is null then raise exception 'recitation not found'; end if;
  if not public.rc182_can_manage_halaqa(r.halaqa_id) then raise exception 'recitation access denied'; end if;

  select s.id, s.halaqa_id, s.category_id, s.track_code
    into st from public.students s where s.id = r.student_id;
  if st.id is null then raise exception 'student not found'; end if;

  select sp.id into v_plan_id
    from public.smart_plans sp
   where coalesce(sp.legacy,false)=false
     and sp.status <> 'archived'
     and r.session_date between sp.start_date and sp.end_date
     and (
       sp.student_id = r.student_id
       or (sp.scope_type='category' and sp.category_id=st.category_id and sp.halaqa_id=st.halaqa_id)
       or (sp.scope_type='halaqa' and sp.halaqa_id=st.halaqa_id)
     )
     and (
       (st.track_code='tathbit' and sp.plan_type='tathbit')
       or (st.track_code<>'tathbit' and sp.plan_type<>'tathbit')
     )
   order by
     case when sp.student_id=r.student_id then 3 when sp.scope_type='category' then 2 else 1 end desc,
     sp.start_date desc,
     sp.updated_at desc
   limit 1;

  v_phase_type := case
    when r.activity_type='sard' then 'sard'
    when r.activity_type in ('new_hifz','hifz','test') and st.track_code<>'tathbit' then 'hifz'
    else null
  end;

  if v_plan_id is not null and v_phase_type is not null then
    select ph.id into v_phase_id
      from public.smart_plan_phases ph
     where ph.plan_id=v_plan_id
       and ph.phase_type=v_phase_type
       and ph.status<>'archived'
       and r.session_date between ph.start_date and ph.end_date
     order by ph.phase_order
     limit 1;
  end if;

  update public.recitation_entries
     set plan_id = v_plan_id,
         phase_id = v_phase_id
   where id = p_recitation_id;

  select jsonb_build_object(
    'recitation_id', p_recitation_id,
    'student_id', r.student_id,
    'session_date', r.session_date,
    'plan_id', v_plan_id,
    'phase_id', v_phase_id
  ) into v_result;
  return v_result;
end;
$$;
revoke all on function public.rc182_rebind_recitation(uuid) from public, anon;
grant execute on function public.rc182_rebind_recitation(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 4) Exact badge refresh: automatic badges may disappear when corrected source
--    data no longer qualifies. Manual/future badge codes are never deleted here.
-- ---------------------------------------------------------------------------
create or replace function public.rc182_clear_computed_badges(p_student_id uuid, p_month date)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer;
  v_month date := date_trunc('month',p_month)::date;
begin
  if auth.uid() is null or not public.rc18_can_view_student(p_student_id) then raise exception 'student access denied'; end if;
  delete from public.student_badge_awards
   where student_id=p_student_id
     and period_month=v_month
     and badge_code in (
       'plan_complete','hifz_20','sard_20','review_40','tathbit_20',
       'attendance_perfect','quality_95','consistency_8'
     );
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
-- Internal implementation only: do not expose destructive clearing directly to clients.
revoke all on function public.rc182_clear_computed_badges(uuid,date) from public, anon, authenticated;

create or replace function public.rc182_refresh_student_badges_exact(p_student_id uuid, p_month date)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.rc18_can_view_student(p_student_id) then raise exception 'student access denied'; end if;
  perform public.rc182_clear_computed_badges(p_student_id,p_month);
  return public.rc18_refresh_student_badges(p_student_id,p_month);
end;
$$;
revoke all on function public.rc182_refresh_student_badges_exact(uuid,date) from public, anon;
grant execute on function public.rc182_refresh_student_badges_exact(uuid,date) to authenticated;

-- ---------------------------------------------------------------------------
-- 5) Harden historical archive continuity.
--    Student-scoped history survives deactivation. Group history includes students
--    who belonged to the scope and/or have facts tied to that historical plan.
-- ---------------------------------------------------------------------------
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
  v_hifz numeric; v_sard numeric; v_tathbit numeric;
  v_target numeric; v_sard_target numeric;
  v_present integer; v_absent integer; v_quality numeric;
  v_first jsonb; v_last jsonb;
  v_count integer := 0;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;

  for p in
    select sp.* from public.smart_plans sp
     where coalesce(sp.legacy,false)=false
       and sp.end_date < v_month_start
       and sp.status in ('active','paused','completed')
       and public.rc182_can_manage_halaqa(sp.halaqa_id)
  loop
    v_period := coalesce(p.period_month,date_trunc('month',p.start_date)::date);

    for s in
      select distinct st.*
        from public.students st
       where
         (p.scope_type='student' and st.id=p.student_id)
         or (
           p.scope_type='category'
           and st.category_id=p.category_id and st.halaqa_id=p.halaqa_id
           and (st.enrollment_date is null or st.enrollment_date<=p.end_date)
         )
         or (
           p.scope_type='halaqa'
           and st.halaqa_id=p.halaqa_id
           and (st.enrollment_date is null or st.enrollment_date<=p.end_date)
         )
         or exists (
           select 1 from public.recitation_entries rr
           join public.sessions ss on ss.id=rr.session_id
           where rr.student_id=st.id and ss.session_date between p.start_date and p.end_date
             and (rr.plan_id=p.id or exists(select 1 from public.smart_plan_phases pp where pp.id=rr.phase_id and pp.plan_id=p.id))
         )
    loop
      with rec as (
        select r.*,se.session_date
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
      select coalesce(sum(pages_count) filter(where activity_type::text='new_hifz'),0),
             coalesce(sum(pages_count) filter(where activity_type::text='sard'),0),
             coalesce(sum(pages_count) filter(where activity_type::text in ('tathbit','review_far')),0),
             round(avg(safety_percentage) filter(where safety_percentage is not null),1),
             (select jsonb_build_object('date',session_date,'start_surah',start_surah,'start_ayah',start_ayah,'end_surah',end_surah,'end_ayah',end_ayah,'pages',pages_count) from rec order by session_date,created_at limit 1),
             (select jsonb_build_object('date',session_date,'start_surah',start_surah,'start_ayah',start_ayah,'end_surah',end_surah,'end_ayah',end_ayah,'pages',pages_count) from rec order by session_date desc,created_at desc limit 1)
        into v_hifz,v_sard,v_tathbit,v_quality,v_first,v_last from rec;

      select coalesce(sum(coalesce(ps.target_pages_override,ps.frozen_target_pages,ph.target_pages,
                         case when ph.goal_mode='juz' then ph.target_units*20 else null end,0)) filter(where ph.phase_type='hifz'),0),
             coalesce(sum(coalesce(ps.target_pages_override,ps.frozen_target_pages,ph.target_pages,
                         case when ph.goal_mode='juz' then ph.target_units*20 else null end,0)) filter(where ph.phase_type='sard'),0)
        into v_target,v_sard_target
        from public.smart_plan_phases ph
        left join public.smart_plan_phase_states ps on ps.phase_id=ph.id and ps.student_id=s.id
       where ph.plan_id=p.id and ph.status<>'archived';
      v_target := coalesce(nullif(v_target,0),p.target_pages,0);

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
        coalesce(v_sard_target,0),coalesce(v_sard,0),coalesce(v_present,0),coalesce(v_absent,0),v_quality,v_first,v_last,
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
      ) on conflict(student_id,source_plan_id,period_month) do update set
        target_pages=excluded.target_pages,done_pages=excluded.done_pages,
        sard_target_pages=excluded.sard_target_pages,sard_done_pages=excluded.sard_done_pages,
        attendance_present=excluded.attendance_present,attendance_absent=excluded.attendance_absent,
        quality_percent=excluded.quality_percent,start_anchor=excluded.start_anchor,end_anchor=excluded.end_anchor,
        snapshot=excluded.snapshot,archived_at=now();
      v_count := v_count+1;
    end loop;

    update public.smart_plans
       set status='completed', period_month=v_period, frozen_at=coalesce(frozen_at,now()),
           frozen_summary=coalesce(frozen_summary,'{}'::jsonb) || jsonb_build_object('rc18_archived',true,'archived_at',now()),
           updated_at=now(), revision=revision+1
     where id=p.id and status<>'archived';

    update public.smart_plan_phases
       set status='completed', completed_at=coalesce(completed_at,now()), updated_at=now(), revision=revision+1
     where plan_id=p.id and status not in ('completed','archived');
  end loop;

  return jsonb_build_object('archived_rows',v_count,'through',v_month_start-1);
end;
$$;
revoke all on function public.rc18_archive_finished_plans(date) from public, anon;
grant execute on function public.rc18_archive_finished_plans(date) to authenticated;

-- ---------------------------------------------------------------------------
-- 6) Individual monthly plan engine.
--    Hifz target is inherited per student. Sard is always 25 pages.
--    Hifz: day 1..22. Sard: day 23..calendar month end.
-- ---------------------------------------------------------------------------
create or replace function public.rc182_start_month(p_month_start date default date_trunc('month',current_date)::date)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  st record;
  oldp public.smart_plans%rowtype;
  curp public.smart_plans%rowtype;
  v_month_start date := date_trunc('month',p_month_start)::date;
  v_month_end date := (date_trunc('month',p_month_start)+interval '1 month - 1 day')::date;
  v_hifz_end date;
  v_sard_start date;
  v_hifz_target numeric;
  v_plan_id uuid;
  v_hifz_phase_id uuid;
  v_sard_phase_id uuid;
  v_created integer := 0;
  v_normalized integer := 0;
  v_missing integer := 0;
  v_rebound integer := 0;
  v_title text;
  v_schedule smallint[];
  rec record;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  if not public.is_admin()
     and not exists(select 1 from public.halaqa_teachers ht where ht.teacher_id=auth.uid())
     and not exists(select 1 from public.halaqa_supervisors hs where hs.supervisor_id=auth.uid()) then
    raise exception 'monthly plan management denied';
  end if;

  v_hifz_end := least(v_month_end, v_month_start + 21);
  v_sard_start := least(v_month_end, v_month_start + 22);
  perform public.rc18_archive_finished_plans(v_month_start);

  for st in
    select s.* from public.students s
     where s.status='active'
       and s.track_code<>'tathbit'
       and s.halaqa_id is not null
       and public.rc182_can_manage_halaqa(s.halaqa_id)
     order by s.halaqa_id,s.full_name
  loop
    v_plan_id := null; v_hifz_phase_id := null; v_sard_phase_id := null;
    v_hifz_target := null; v_title := null; v_schedule := null;

    -- Current explicit student plan wins. If it is already RC18.2-generated, do not
    -- overwrite the manager's later edits; the button is idempotent.
    select sp.* into curp
      from public.smart_plans sp
     where coalesce(sp.legacy,false)=false
       and sp.scope_type='student' and sp.student_id=st.id
       and sp.plan_type<>'tathbit' and sp.status<>'archived'
       and sp.start_date<=v_month_end and sp.end_date>=v_month_start
     order by sp.start_date desc,sp.updated_at desc limit 1;

    if curp.id is not null and coalesce((curp.settings->>'rc182_monthly_generated')::boolean,false) then
      continue;
    end if;

    -- Latest effective PREVIOUS plan first; specificity breaks ties at the same recency.
    select sp.* into oldp
      from public.smart_plans sp
     where coalesce(sp.legacy,false)=false
       and sp.plan_type<>'tathbit'
       and sp.end_date < v_month_start
       and sp.status in ('active','paused','completed','archived')
       and (
         sp.student_id=st.id
         or (sp.scope_type='category' and sp.category_id=st.category_id and sp.halaqa_id=st.halaqa_id)
         or (sp.scope_type='halaqa' and sp.halaqa_id=st.halaqa_id)
       )
     order by sp.end_date desc,
              case when sp.student_id=st.id then 3 when sp.scope_type='category' then 2 else 1 end desc,
              sp.updated_at desc
     limit 1;

    if curp.id is not null then
      v_plan_id := curp.id;
      v_title := curp.title;
      v_schedule := curp.schedule_weekdays;
      select coalesce(
        ps.target_pages_override,ps.frozen_target_pages,ph.target_pages,
        case when ph.goal_mode='juz' then ph.target_units*20 else null end,
        curp.target_pages
      ) into v_hifz_target
      from public.smart_plan_phases ph
      left join public.smart_plan_phase_states ps on ps.phase_id=ph.id and ps.student_id=st.id
      where ph.plan_id=curp.id and ph.phase_type='hifz' and ph.status<>'archived'
      order by ph.phase_order limit 1;
      v_hifz_target := coalesce(v_hifz_target,curp.target_pages);
    else
      if oldp.id is not null then
        v_title := regexp_replace(oldp.title,'\\s*[·-]?\\s*\\d{4}[-/]\\d{1,2}\\s*$','') || ' · ' || to_char(v_month_start,'YYYY-MM');
        v_schedule := oldp.schedule_weekdays;
        select coalesce(
          ps.target_pages_override,ps.frozen_target_pages,ph.target_pages,
          case when ph.goal_mode='juz' then ph.target_units*20 else null end,
          oldp.target_pages
        ) into v_hifz_target
        from public.smart_plan_phases ph
        left join public.smart_plan_phase_states ps on ps.phase_id=ph.id and ps.student_id=st.id
        where ph.plan_id=oldp.id and ph.phase_type='hifz' and ph.status<>'archived'
        order by ph.phase_order limit 1;
        v_hifz_target := coalesce(v_hifz_target,oldp.target_pages);
      else
        v_missing := v_missing+1;
        v_title := 'الخطة الشهرية · ' || to_char(v_month_start,'YYYY-MM');
        v_schedule := array[0,1,2,3,4]::smallint[];
        v_hifz_target := 25;
      end if;

      v_hifz_target := greatest(coalesce(v_hifz_target,25),0.01);
      insert into public.smart_plans(
        scope_type,halaqa_id,student_id,category_id,parent_plan_id,plan_type,review_tier,title,
        goal_unit,target_units,target_pages,start_date,end_date,schedule_weekdays,catch_up_weekday,
        max_daily_multiplier,auto_extend,quality_gate_enabled,minimum_evaluation,
        range_start_page,range_end_page,range_start_surah,range_start_ayah,range_end_surah,range_end_ayah,
        selected_surahs,selected_juz,selected_rubs,near_revision_pages,far_revision_pages,
        milestone_mode,milestone_requires_approval,delay_alert_days,home_prep_push,status,note,settings,
        period_month,legacy,created_by
      ) values (
        'student',st.halaqa_id,st.id,null,null,'hifz',coalesce(oldp.review_tier,'none'),v_title,
        'pages',v_hifz_target,v_hifz_target,v_month_start,v_month_end,coalesce(v_schedule,array[0,1,2,3,4]::smallint[]),oldp.catch_up_weekday,
        coalesce(oldp.max_daily_multiplier,1.5),coalesce(oldp.auto_extend,true),coalesce(oldp.quality_gate_enabled,true),coalesce(oldp.minimum_evaluation,'very_good'),
        oldp.range_start_page,oldp.range_end_page,oldp.range_start_surah,oldp.range_start_ayah,oldp.range_end_surah,oldp.range_end_ayah,
        coalesce(oldp.selected_surahs,'{}'::smallint[]),coalesce(oldp.selected_juz,'{}'::smallint[]),coalesce(oldp.selected_rubs,'{}'::smallint[]),
        coalesce(oldp.near_revision_pages,7),coalesce(oldp.far_revision_pages,20),coalesce(oldp.milestone_mode,'juz'),
        coalesce(oldp.milestone_requires_approval,true),coalesce(oldp.delay_alert_days,3),coalesce(oldp.home_prep_push,true),
        'active',oldp.note,
        coalesce(oldp.settings,'{}'::jsonb) || jsonb_build_object(
          'rc182_monthly_generated',true,'rc182_source_plan_id',oldp.id,'rc182_target_source',case when oldp.id is null then 'default_25' else 'previous_student_effective_plan' end,
          'rc182_hifz_days','1-22','rc182_sard_target_pages',25,'rc182_sard_start_day',23
        ),v_month_start,false,auth.uid()
      ) returning id into v_plan_id;
      v_created := v_created+1;
    end if;

    v_hifz_target := greatest(coalesce(v_hifz_target,25),0.01);

    -- Normalize an existing unmarked current student plan once. Preserve its current
    -- Hifz target, but repair the monthly dates/metadata and fixed Sard contract.
    update public.smart_plans
       set start_date=v_month_start,end_date=v_month_end,period_month=v_month_start,
           target_units=v_hifz_target,target_pages=v_hifz_target,status=case when status in ('paused','completed') then status else 'active' end,
           settings=coalesce(settings,'{}'::jsonb) || jsonb_build_object(
             'rc182_monthly_generated',true,'rc182_source_plan_id',oldp.id,
             'rc182_target_source',case when curp.id is not null then 'existing_current_student_plan' when oldp.id is null then 'default_25' else 'previous_student_effective_plan' end,
             'rc182_hifz_days','1-22','rc182_sard_target_pages',25,'rc182_sard_start_day',23
           ),updated_at=now(),revision=revision+1
     where id=v_plan_id;
    if curp.id is not null then v_normalized := v_normalized+1; end if;

    select ph.id into v_hifz_phase_id from public.smart_plan_phases ph
     where ph.plan_id=v_plan_id and ph.phase_type='hifz' and ph.status<>'archived'
     order by ph.phase_order limit 1;
    if v_hifz_phase_id is null then
      insert into public.smart_plan_phases(
        plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,goal_mode,goal_unit,
        target_units,target_pages,distribution_mode,start_mode,allow_surplus,status,settings,created_by
      ) values (
        v_plan_id,1,'hifz','مرحلة الحفظ',v_month_start,v_hifz_end,coalesce(v_schedule,array[0,1,2,3,4]::smallint[]),
        'pages','pages',v_hifz_target,v_hifz_target,'flexible','last_activity',true,'active',
        jsonb_build_object('rc182_monthly_generated',true,'continuation_mode','last_activity'),auth.uid()
      ) returning id into v_hifz_phase_id;
    else
      update public.smart_plan_phases set phase_order=1,title='مرحلة الحفظ',start_date=v_month_start,end_date=v_hifz_end,
        goal_mode='pages',goal_unit='pages',target_units=v_hifz_target,target_pages=v_hifz_target,
        distribution_mode='flexible',start_mode='last_activity',status=case when status='completed' then 'completed' else 'active' end,
        completed_at=case when status='completed' then completed_at else null end,
        settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object('rc182_monthly_generated',true,'continuation_mode','last_activity'),
        updated_at=now(),revision=revision+1 where id=v_hifz_phase_id;
    end if;

    select ph.id into v_sard_phase_id from public.smart_plan_phases ph
     where ph.plan_id=v_plan_id and ph.phase_type='sard' and ph.status<>'archived'
     order by ph.phase_order limit 1;
    if v_sard_phase_id is null then
      insert into public.smart_plan_phases(
        plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,goal_mode,goal_unit,
        target_units,target_pages,distribution_mode,start_mode,allow_surplus,status,settings,created_by
      ) values (
        v_plan_id,2,'sard','مرحلة السرد',v_sard_start,v_month_end,array[5]::smallint[],
        'pages','pages',25,25,'flexible','last_activity',true,'active',
        jsonb_build_object('rc182_monthly_generated',true,'fixed_sard_target',25,'continuation_mode','last_activity'),auth.uid()
      ) returning id into v_sard_phase_id;
    else
      update public.smart_plan_phases set phase_order=2,title='مرحلة السرد',start_date=v_sard_start,end_date=v_month_end,
        goal_mode='pages',goal_unit='pages',target_units=25,target_pages=25,
        distribution_mode='flexible',start_mode='last_activity',status=case when status='completed' then 'completed' else 'active' end,
        completed_at=case when status='completed' then completed_at else null end,
        settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object('rc182_monthly_generated',true,'fixed_sard_target',25,'continuation_mode','last_activity'),
        updated_at=now(),revision=revision+1 where id=v_sard_phase_id;
    end if;

    insert into public.smart_plan_phase_states(phase_id,student_id,status,target_pages_override,carry_shortfall_pages,settings,updated_by)
    values(v_hifz_phase_id,st.id,'not_started',null,0,jsonb_build_object('rc182_monthly_generated',true),auth.uid())
    on conflict(phase_id,student_id) do update set
      updated_by=auth.uid(),updated_at=now(),
      settings=coalesce(public.smart_plan_phase_states.settings,'{}'::jsonb)||jsonb_build_object('rc182_monthly_generated',true);

    insert into public.smart_plan_phase_states(phase_id,student_id,status,target_pages_override,carry_shortfall_pages,settings,updated_by)
    values(v_sard_phase_id,st.id,'not_started',null,0,jsonb_build_object('rc182_monthly_generated',true),auth.uid())
    on conflict(phase_id,student_id) do update set
      target_pages_override=null,frozen_target_pages=null,
      updated_by=auth.uid(),updated_at=now(),
      settings=coalesce(public.smart_plan_phase_states.settings,'{}'::jsonb)||jsonb_build_object('rc182_monthly_generated',true,'fixed_sard_target',25);

    -- Rebind facts already recorded in the current month to the newly explicit student plan.
    for rec in
      select re.id from public.recitation_entries re
      join public.sessions se on se.id=re.session_id
      where re.student_id=st.id and se.session_date between v_month_start and v_month_end and se.status<>'cancelled'
    loop
      perform public.rc182_rebind_recitation(rec.id);
      v_rebound := v_rebound+1;
    end loop;
  end loop;

  -- Tathbit has its own lifelong/monthly continuity engine. It operates on current_date,
  -- therefore only invoke it when the requested month is the actual current month.
  if v_month_start=date_trunc('month',current_date)::date
     and to_regprocedure('public.rc152_roll_tathbit_months()') is not null then
    perform public.rc152_roll_tathbit_months();
  end if;

  return jsonb_build_object(
    'month',v_month_start,'month_end',v_month_end,'hifz_end',v_hifz_end,'sard_start',v_sard_start,
    'sard_target_pages',25,'created_plans',v_created,'normalized_plans',v_normalized,
    'missing_source_students',v_missing,'rebound_recitations',v_rebound
  );
end;
$$;
revoke all on function public.rc182_start_month(date) from public, anon;
grant execute on function public.rc182_start_month(date) to authenticated;

-- Compatibility: old 2.54.0/2.54.1 clients get the corrected engine after this migration.
create or replace function public.rc18_start_month(p_month_start date default date_trunc('month',current_date)::date)
returns jsonb
language sql
security definer
set search_path = public
as $$ select public.rc182_start_month(p_month_start); $$;
revoke all on function public.rc18_start_month(date) from public, anon;
grant execute on function public.rc18_start_month(date) to authenticated;

-- ---------------------------------------------------------------------------
-- 7) Transactional session metadata edit with historical repair
-- ---------------------------------------------------------------------------
create or replace function public.rc182_update_session_details(
  p_session_id uuid,
  p_halaqa_id uuid,
  p_session_date date,
  p_session_type text,
  p_title text default null,
  p_notes text default null
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_old public.sessions%rowtype;
  v_type_name text;
  v_result jsonb;
  v_dep_count integer;
  v_student uuid;
  v_old_month date;
  v_new_month date;
begin
  if p_session_date is null then raise exception 'session date required'; end if;
  if p_session_type not in ('tasmee','sard','review','test','tajweed','lesson','other') then
    raise exception 'invalid session type';
  end if;

  select * into v_old from public.sessions where id=p_session_id for update;
  if not found then raise exception 'session not found'; end if;
  if not public.rc182_can_manage_halaqa(v_old.halaqa_id) then raise exception 'session access denied'; end if;
  if not public.rc182_can_manage_halaqa(p_halaqa_id) then raise exception 'target halaqa access denied'; end if;

  if p_halaqa_id is distinct from v_old.halaqa_id then
    select
      (select count(*) from public.recitation_entries r where r.session_id=p_session_id)
      +(select count(*) from public.attendance_records a where a.session_id=p_session_id)
      +(select count(*) from public.student_daily_notes n where n.session_id=p_session_id)
      into v_dep_count;
    if v_dep_count>0 then
      raise exception 'cannot move a used session to another halaqa; create a new session or clear dependent records first';
    end if;
  end if;

  v_type_name := pg_typeof(v_old.session_type)::text;
  execute format(
    'update public.sessions set halaqa_id=$1, session_date=$2, session_type=$3::%s, title=$4, notes=$5 where id=$6',
    v_type_name
  ) using p_halaqa_id,p_session_date,p_session_type,nullif(trim(coalesce(p_title,'')),''),nullif(trim(coalesce(p_notes,'')),''),p_session_id;

  for v_student in
    select distinct x.student_id from (
      select r.student_id from public.recitation_entries r where r.session_id=p_session_id
      union all select a.student_id from public.attendance_records a where a.session_id=p_session_id
      union all select n.student_id from public.student_daily_notes n where n.session_id=p_session_id
    ) x where x.student_id is not null
  loop
    -- Rebind every recitation in the edited session to the correct date-specific plan/phase.
    perform public.rc182_rebind_recitation(r.id)
      from public.recitation_entries r where r.session_id=p_session_id and r.student_id=v_student;

    v_old_month := date_trunc('month',v_old.session_date)::date;
    v_new_month := date_trunc('month',p_session_date)::date;
    if v_old_month < date_trunc('month',current_date)::date then
      perform public.rc18_archive_finished_plans((v_old_month + interval '1 month')::date);
    end if;
    if v_new_month <> v_old_month and v_new_month < date_trunc('month',current_date)::date then
      perform public.rc18_archive_finished_plans((v_new_month + interval '1 month')::date);
    end if;

    if public.rc18_can_manage_student(v_student) then
      perform public.rc182_clear_computed_badges(v_student,v_old_month);
      perform public.rc18_refresh_student_badges(v_student,v_old_month);
      if v_new_month<>v_old_month then
        perform public.rc182_clear_computed_badges(v_student,v_new_month);
        perform public.rc18_refresh_student_badges(v_student,v_new_month);
      end if;
    end if;
  end loop;

  select to_jsonb(s) into v_result from public.sessions s where s.id=p_session_id;
  return v_result;
end;
$$;
revoke all on function public.rc182_update_session_details(uuid,uuid,date,text,text,text) from public, anon;
grant execute on function public.rc182_update_session_details(uuid,uuid,date,text,text,text) to authenticated;

-- ---------------------------------------------------------------------------
-- 8) Audit session administration without changing the data model
-- ---------------------------------------------------------------------------
do $$
begin
  if to_regclass('public.sessions') is not null and to_regprocedure('public.rc18_audit_management_mutation()') is not null then
    execute 'drop trigger if exists rc182_audit_sessions on public.sessions';
    execute 'create trigger rc182_audit_sessions after insert or update or delete on public.sessions for each row execute function public.rc18_audit_management_mutation()';
  end if;
end $$;

commit;

-- Verification examples (run with the appropriate authenticated role):
-- select public.rc182_start_month(date_trunc('month',current_date)::date);
-- select public.rc182_set_session_archived('<session-uuid>',true);
-- select public.rc182_refresh_student_badges_exact('<student-uuid>',date_trunc('month',current_date)::date);
