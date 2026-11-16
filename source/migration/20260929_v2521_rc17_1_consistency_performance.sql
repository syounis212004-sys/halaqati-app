-- ==========================================================================
-- Halaqati v2.52.0 RC17.1 — consistency patch (additive, no table changes)
-- Guardian overview no longer merges Hifz + Sard into one ambiguous plan total.
-- It also tells the client whether today's halaqa session exists / has started.
-- ==========================================================================
begin;

create or replace function public.guardian_child_overview(p_student_id uuid, p_today date default current_date)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $function$
declare
  v_student record;
  v_program text;
  v_month_start date := date_trunc('month', p_today)::date;
  v_plan record;
  v_result jsonb;
begin
  if public.current_role() is distinct from 'guardian'::public.app_role then
    raise exception 'guardian access only';
  end if;
  if not exists (
    select 1 from public.student_guardians sg
    where sg.student_id = p_student_id and sg.guardian_id = auth.uid()
  ) then
    raise exception 'student is not linked to this guardian';
  end if;

  select s.id, s.full_name, s.track_code, s.status, s.halaqa_id,
         c.name as category_name, h.name as halaqa_name
    into v_student
    from public.students s
    left join public.student_categories c on c.id = s.category_id
    left join public.halaqas h on h.id = s.halaqa_id
   where s.id = p_student_id;

  if v_student.id is null then raise exception 'student not found'; end if;
  v_program := case when v_student.track_code = 'tathbit' then 'tathbit' else 'general_hifz' end;

  select p.id, p.plan_type, p.title, p.start_date, p.end_date, p.target_pages, p.settings
    into v_plan
    from public.smart_plans p
   where p.status = 'active' and p.legacy is not true
     and (p.student_id = p_student_id or (p.scope_type = 'halaqa' and p.halaqa_id = v_student.halaqa_id))
     and p_today between p.start_date and p.end_date
     and ((v_program = 'tathbit') = (p.plan_type = 'tathbit'))
   order by (p.student_id = p_student_id) desc nulls last, p.start_date desc
   limit 1;

  with rec as (
    select r.*, se.session_date
      from public.recitation_entries r
      join public.sessions se on se.id = r.session_id and se.status <> 'cancelled'
     where r.student_id = p_student_id
       and se.session_date between least(v_month_start, coalesce(v_plan.start_date, v_month_start)) and p_today
  ), att as (
    select ar.status::text as status, se.session_date
      from public.attendance_records ar
      join public.sessions se on se.id = ar.session_id and se.status <> 'cancelled'
     where ar.student_id = p_student_id and se.session_date between v_month_start and p_today
  ), phase_summary as (
    select ph.phase_type,
           sum(coalesce(
             ps.target_pages_override,
             ps.frozen_target_pages,
             case when ph.goal_mode = 'juz' then ph.target_units * 20 else ph.target_pages end,
             0
           ))::numeric as target_pages,
           min(coalesce(ps.start_date_override, ph.start_date)) as phase_start,
           max(coalesce(ps.end_date_override, ph.end_date)) as phase_end
      from public.smart_plan_phases ph
      left join public.smart_plan_phase_states ps
        on ps.phase_id = ph.id and ps.student_id = p_student_id
     where v_plan.id is not null
       and ph.plan_id = v_plan.id
       and ph.status <> 'archived'
       and coalesce(ps.status, 'not_started') <> 'skipped'
     group by ph.phase_type
  ), facts as (
    select
      coalesce((select sum(pages_count) from rec where session_date >= v_month_start and activity_type::text = 'new_hifz'), 0)::numeric as month_hifz,
      coalesce((select sum(pages_count) from rec where session_date >= v_month_start and activity_type::text in ('review_near','review_far','tilawah')), 0)::numeric as month_review,
      coalesce((select sum(pages_count) from rec where session_date >= v_month_start and activity_type::text = 'sard'), 0)::numeric as month_sard,
      coalesce((select sum(pages_count) from rec where session_date >= v_month_start and v_program = 'tathbit' and activity_type::text <> 'test'), 0)::numeric as month_tathbit,
      coalesce((select sum(pages_count) from rec where v_plan.id is not null and session_date between v_plan.start_date and v_plan.end_date and activity_type::text = 'new_hifz'), 0)::numeric as plan_hifz,
      coalesce((select sum(pages_count) from rec where v_plan.id is not null and session_date between v_plan.start_date and v_plan.end_date and activity_type::text = 'sard'), 0)::numeric as plan_sard,
      coalesce((select sum(pages_count) from rec where v_plan.id is not null and session_date between v_plan.start_date and v_plan.end_date and activity_type::text <> 'test'), 0)::numeric as plan_tathbit,
      coalesce((select target_pages from phase_summary where phase_type = 'hifz'), v_plan.target_pages, 0)::numeric as hifz_target,
      coalesce((select target_pages from phase_summary where phase_type = 'sard'), 0)::numeric as sard_target,
      coalesce((select target_pages from phase_summary where phase_type = 'tathbit'), v_plan.target_pages, 0)::numeric as tathbit_target
  )
  select jsonb_build_object(
    'student', jsonb_build_object(
      'id', v_student.id,
      'full_name', v_student.full_name,
      'track', v_program,
      'category_name', v_student.category_name,
      'halaqa_name', v_student.halaqa_name
    ),
    'today', jsonb_build_object(
      'session_exists', exists (
        select 1 from public.sessions se
        where se.halaqa_id = v_student.halaqa_id and se.session_date = p_today and se.status <> 'cancelled'
      ),
      'session_started', exists (
        select 1 from public.sessions se
        where se.halaqa_id = v_student.halaqa_id and se.session_date = p_today and se.status in ('open','closed')
      ),
      'attendance', (select a.status from att a where a.session_date = p_today limit 1),
      'recited', exists (select 1 from rec where session_date = p_today and activity_type::text <> 'test'),
      'pages', coalesce((select sum(pages_count) from rec where session_date = p_today), 0),
      'ranges', coalesce((
        select jsonb_agg(jsonb_build_object(
          'activity', activity_type,
          'start_surah', start_surah,
          'start_ayah', start_ayah,
          'end_surah', end_surah,
          'end_ayah', end_ayah,
          'pages', pages_count
        ) order by created_at)
        from rec where session_date = p_today
      ), '[]'::jsonb)
    ),
    'month', jsonb_build_object(
      'hifz', f.month_hifz,
      'review', f.month_review,
      'sard', f.month_sard,
      'tathbit', f.month_tathbit,
      'points', coalesce((select sum(points) from rec where session_date >= v_month_start), 0),
      'quality', (select round(avg(safety_percentage), 1) from rec where session_date >= v_month_start and safety_percentage is not null),
      'mistakes', coalesce((select sum(mistakes) from rec where session_date >= v_month_start), 0),
      'present', (select count(*) from att where status in ('present','late')),
      'absent', (select count(*) from att where status = 'absent'),
      'excused', (select count(*) from att where status = 'excused'),
      'late', (select count(*) from att where status = 'late'),
      'sessions', (select count(*) from att)
    ),
    'plan', case when v_plan.id is null then null else jsonb_build_object(
      'id', v_plan.id,
      'type', v_plan.plan_type,
      'title', v_plan.title,
      'start_date', v_plan.start_date,
      'end_date', v_plan.end_date,
      -- Legacy keys remain for old clients, but done_pages is now the MAIN activity only.
      'target_pages', case when v_program = 'tathbit' then f.tathbit_target else f.hifz_target end,
      'done_pages', case when v_program = 'tathbit' then f.plan_tathbit else f.plan_hifz end,
      'sard_target_pages', f.sard_target,
      'activities', case
        when v_program = 'tathbit' then jsonb_build_array(
          jsonb_build_object('key','tathbit','label','التثبيت','target_pages',f.tathbit_target,'done_pages',f.plan_tathbit)
        )
        else jsonb_build_array(
          jsonb_build_object('key','hifz','label','الحفظ','target_pages',f.hifz_target,'done_pages',f.plan_hifz)
        ) || case when f.sard_target > 0 or f.plan_sard > 0 or exists (select 1 from phase_summary where phase_type='sard')
             then jsonb_build_array(jsonb_build_object('key','sard','label','السرد','target_pages',f.sard_target,'done_pages',f.plan_sard))
             else '[]'::jsonb end
      end
    ) end,
    'latest', coalesce((
      select jsonb_agg(x order by x->>'date' desc) from (
        select jsonb_build_object(
          'date', session_date,
          'activity', activity_type,
          'start_surah', start_surah,
          'start_ayah', start_ayah,
          'end_surah', end_surah,
          'end_ayah', end_ayah,
          'pages', pages_count,
          'evaluation', evaluation,
          'mistakes', mistakes,
          'prompts', prompts,
          'quality', safety_percentage
        ) as x
        from rec order by session_date desc, created_at desc limit 5
      ) t
    ), '[]'::jsonb),
    'updated_at', now()
  ) into v_result
  from facts f;

  return v_result;
end;
$function$;

revoke all on function public.guardian_child_overview(uuid, date) from public;
grant execute on function public.guardian_child_overview(uuid, date) to authenticated;

commit;
