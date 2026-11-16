-- ============================================================================
-- Halaqati v2.52.0 RC17.1 BUILD FIX 3 — Smart Plan Continuity & Consistency
-- Additive / idempotent. Run AFTER 20260929_v2521_rc17_1_consistency_performance.sql.
-- ============================================================================
begin;

-- 1) A phase can continue from the latest accepted activity, not Sard only.
alter table public.smart_plan_phases
  drop constraint if exists smart_plan_phases_start_mode_check;
alter table public.smart_plan_phases
  add constraint smart_plan_phases_start_mode_check
  check (start_mode in ('plan_start','last_sard','last_activity','manual','range_start'));

comment on column public.smart_plan_phases.start_mode is
  'plan_start | last_activity | manual | range_start. last_sard is retained for legacy RC11 rows.';

-- 2) Lightweight anchor RPC used by editors. Keeps opening a plan editor from downloading
--    the full recitation history. RLS remains in force because this is SECURITY INVOKER.
drop function if exists public.rc171_latest_activity_anchor(uuid,text,date);
create or replace function public.rc171_latest_activity_anchor(
  p_student_id uuid,
  p_activity text,
  p_as_of date default current_date,
  p_plan_id uuid default null
) returns jsonb
language plpgsql
stable
set search_path = public
as $$
declare
  v_quality_gate boolean := true;
  v_minimum text := 'good';
  v_result jsonb;
begin
  if p_plan_id is not null then
    select p.quality_gate_enabled, p.minimum_evaluation::text
      into v_quality_gate, v_minimum
      from public.smart_plans p where p.id = p_plan_id;
    v_quality_gate := coalesce(v_quality_gate, true);
    v_minimum := coalesce(nullif(v_minimum,''), 'good');
  end if;

  select to_jsonb(x) into v_result
  from (
    select
      r.id, r.student_id, r.activity_type::text as activity_type,
      r.start_surah, r.start_ayah, r.end_surah, r.end_ayah,
      r.start_page, r.end_page, r.pages_count, r.evaluation::text as evaluation,
      r.mistakes, r.prompts, r.created_at, se.session_date
    from public.recitation_entries r
    join public.sessions se on se.id = r.session_id and se.status <> 'cancelled'
    where r.student_id = p_student_id
      and se.session_date <= p_as_of
      and case lower(coalesce(p_activity,''))
        when 'hifz' then r.activity_type::text in ('new_hifz','hifz')
        when 'sard' then r.activity_type::text = 'sard'
        when 'tathbit' then r.activity_type::text in ('tathbit','review_far')
        when 'review' then r.activity_type::text in ('review_near','review_far')
        else false
      end
      and r.end_surah is not null and r.end_ayah is not null
      and (
        case
          when lower(coalesce(r.evaluation::text,'')) in ('repeat','weak','إعادة','ضعيف') then false
          when lower(coalesce(r.evaluation::text,'')) in ('good','very_good','excellent') then true
          when lower(coalesce(r.evaluation::text,'')) in ('','not_rated') and coalesce(r.pages_count,0) > 0 then
            (100.0 - ((coalesce(r.mistakes,0) + coalesce(r.prompts,0) * 0.25) / greatest(1.0, r.pages_count)) * 15.0) >= 70.0
          when not v_quality_gate then true
          else (case lower(coalesce(r.evaluation::text,''))
                  when 'repeat' then 0 when 'weak' then 0 when 'good' then 2
                  when 'very_good' then 3 when 'excellent' then 4 else -1 end)
               >= (case lower(v_minimum) when 'repeat' then 0 when 'good' then 2 when 'very_good' then 3 when 'excellent' then 4 else 2 end)
        end
      )
    order by se.session_date desc, r.created_at desc
    limit 1
  ) x;
  return v_result;
end;
$$;
revoke all on function public.rc171_latest_activity_anchor(uuid,text,date,uuid) from public;
grant execute on function public.rc171_latest_activity_anchor(uuid,text,date,uuid) to authenticated;

-- 3) Server helper for Tathbit monthly continuation.
create or replace function public.rc171_next_quran_location(
  p_surah integer,
  p_ayah integer,
  p_route text default 'forward_surahs_forward_ayahs'
) returns jsonb
language plpgsql
immutable
set search_path = public
as $$
declare
  v_counts integer[] := array[
    7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,
    112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,
    59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,
    52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,
    21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6
  ];
  v_next_surah integer;
begin
  if p_surah not between 1 and 114 or p_ayah < 1 or p_ayah > v_counts[p_surah] then
    return null;
  end if;
  if p_ayah < v_counts[p_surah] then
    return jsonb_build_object('surah',p_surah,'ayah',p_ayah + 1);
  end if;
  if p_route = 'reverse_surahs_forward_ayahs' then
    v_next_surah := p_surah - 1;
    if v_next_surah < 1 then return null; end if;
  else
    v_next_surah := p_surah + 1;
    if v_next_surah > 114 then return null; end if;
  end if;
  return jsonb_build_object('surah',v_next_surah,'ayah',1);
end;
$$;

-- 4) Guardian overview: Student > Category > Halaqa priority; plan totals are activity-specific,
--    and rejected recitations do not inflate Smart Plan progress.
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

  select s.id, s.full_name, s.track_code, s.status, s.halaqa_id, s.category_id,
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
     and (
       p.student_id = p_student_id
       or (p.scope_type = 'category' and p.category_id = v_student.category_id and p.halaqa_id = v_student.halaqa_id)
       or (p.scope_type = 'halaqa' and p.halaqa_id = v_student.halaqa_id)
     )
     and p_today between p.start_date and p.end_date
     and ((v_program = 'tathbit') = (p.plan_type = 'tathbit'))
   order by case
              when p.student_id = p_student_id then 3
              when p.scope_type = 'category' and p.category_id = v_student.category_id then 2
              when p.scope_type = 'halaqa' then 1
              else 0
            end desc,
            p.start_date desc
   limit 1;

  with rec as (
    select r.*, se.session_date,
           (lower(coalesce(r.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف')) as accepted
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
           ))::numeric as target_pages
      from public.smart_plan_phases ph
      left join public.smart_plan_phase_states ps
        on ps.phase_id = ph.id and ps.student_id = p_student_id
     where v_plan.id is not null
       and ph.plan_id = v_plan.id
       and ph.status <> 'archived'
       and coalesce(ps.status, 'not_started') <> 'skipped'
     group by ph.phase_type
  ), plan_rec as (
    select r.*
    from rec r
    where v_plan.id is not null
      and r.accepted
      and r.session_date between v_plan.start_date and v_plan.end_date
      and (
        r.plan_id = v_plan.id
        or exists (select 1 from public.smart_plan_phases ph where ph.id = r.phase_id and ph.plan_id = v_plan.id)
        or (r.plan_id is null and r.phase_id is null) -- legacy/import fallback
      )
  ), facts as (
    select
      coalesce((select sum(pages_count) from rec where accepted and session_date >= v_month_start and activity_type::text = 'new_hifz'), 0)::numeric as month_hifz,
      coalesce((select sum(pages_count) from rec where accepted and session_date >= v_month_start and activity_type::text in ('review_near','review_far','tilawah')), 0)::numeric as month_review,
      coalesce((select sum(pages_count) from rec where accepted and session_date >= v_month_start and activity_type::text = 'sard'), 0)::numeric as month_sard,
      coalesce((select sum(pages_count) from rec where accepted and session_date >= v_month_start and activity_type::text in ('tathbit','review_far') and v_program = 'tathbit'), 0)::numeric as month_tathbit,
      coalesce((select sum(pages_count) from plan_rec where activity_type::text = 'new_hifz'), 0)::numeric as plan_hifz,
      coalesce((select sum(pages_count) from plan_rec where activity_type::text = 'sard'), 0)::numeric as plan_sard,
      coalesce((select sum(pages_count) from plan_rec where activity_type::text in ('tathbit','review_far')), 0)::numeric as plan_tathbit,
      coalesce((select target_pages from phase_summary where phase_type = 'hifz'), v_plan.target_pages, 0)::numeric as hifz_target,
      coalesce((select target_pages from phase_summary where phase_type = 'sard'), 0)::numeric as sard_target,
      coalesce((select target_pages from phase_summary where phase_type = 'tathbit'), v_plan.target_pages, 0)::numeric as tathbit_target
  )
  select jsonb_build_object(
    'student', jsonb_build_object(
      'id', v_student.id, 'full_name', v_student.full_name, 'track', v_program,
      'category_name', v_student.category_name, 'halaqa_name', v_student.halaqa_name
    ),
    'today', jsonb_build_object(
      'session_exists', exists (select 1 from public.sessions se where se.halaqa_id = v_student.halaqa_id and se.session_date = p_today and se.status <> 'cancelled'),
      'session_started', exists (select 1 from public.sessions se where se.halaqa_id = v_student.halaqa_id and se.session_date = p_today and se.status in ('open','closed')),
      'attendance', (select a.status from att a where a.session_date = p_today limit 1),
      'recited', exists (select 1 from rec where session_date = p_today and activity_type::text <> 'test'),
      'pages', coalesce((select sum(pages_count) from rec where session_date = p_today), 0),
      'ranges', coalesce((
        select jsonb_agg(jsonb_build_object(
          'activity', activity_type, 'start_surah', start_surah, 'start_ayah', start_ayah,
          'end_surah', end_surah, 'end_ayah', end_ayah, 'pages', pages_count
        ) order by created_at) from rec where session_date = p_today
      ), '[]'::jsonb)
    ),
    'month', jsonb_build_object(
      'hifz', f.month_hifz, 'review', f.month_review, 'sard', f.month_sard, 'tathbit', f.month_tathbit,
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
      'id', v_plan.id, 'type', v_plan.plan_type, 'title', v_plan.title,
      'start_date', v_plan.start_date, 'end_date', v_plan.end_date,
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
          'date', session_date, 'activity', activity_type, 'start_surah', start_surah, 'start_ayah', start_ayah,
          'end_surah', end_surah, 'end_ayah', end_ayah, 'pages', pages_count, 'evaluation', evaluation,
          'mistakes', mistakes, 'prompts', prompts, 'quality', safety_percentage
        ) as x from rec order by session_date desc, created_at desc limit 5
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

-- 5) Tathbit monthly roll uses the last accepted Tathbit anchor for the next month's start
--    and freezes only Tathbit-compatible accepted activity.
create or replace function public.rc152_roll_tathbit_months()
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_frozen int := 0; v_created int := 0;
  v_month_start date := date_trunc('month', current_date)::date;
  v_month_end date := (date_trunc('month', current_date) + interval '1 month - 1 day')::date;
  r record;
  v_anchor record;
  v_next jsonb;
  v_route text;
begin
  with done as (
    select p.id, p.target_pages,
           coalesce(sum(e.pages_count) filter (
             where e.activity_type::text in ('tathbit','review_far')
               and lower(coalesce(e.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف')
           ), 0) as pages,
           count(e.id) filter (
             where e.activity_type::text in ('tathbit','review_far')
               and lower(coalesce(e.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف')
           ) as sessions
      from public.smart_plans p
      left join public.recitation_entries e on e.plan_id = p.id
     where p.plan_type = 'tathbit' and p.frozen_at is null and p.end_date < current_date and p.status <> 'archived'
     group by p.id, p.target_pages
  )
  update public.smart_plans p
     set status = 'completed', frozen_at = now(),
         frozen_summary = jsonb_build_object(
           'completed_pages', d.pages, 'target_pages', d.target_pages, 'recitations', d.sessions,
           'percent', case when d.target_pages > 0 then round(least(100, d.pages * 100.0 / d.target_pages), 1) else 0 end,
           'frozen_on', current_date)
    from done d where d.id = p.id;
  get diagnostics v_frozen = row_count;

  for r in
    select distinct on (s.id) s.id as sid, s.halaqa_id as shalaqa, p.*
      from public.students s
      join public.smart_plans p on p.student_id = s.id and p.plan_type = 'tathbit' and p.legacy = false
     where s.track_code = 'tathbit' and coalesce(s.status::text, 'active') = 'active' and s.withdrawn_at is null
       and not exists (select 1 from public.smart_plans q
                        where q.student_id = s.id and q.plan_type = 'tathbit' and q.status <> 'archived'
                          and current_date between q.start_date and q.end_date)
     order by s.id, p.end_date desc
  loop
    select e.end_surah, e.end_ayah
      into v_anchor
      from public.recitation_entries e
      join public.sessions se on se.id = e.session_id and se.status <> 'cancelled'
     where e.student_id = r.sid
       and se.session_date < v_month_start
       and e.activity_type::text in ('tathbit','review_far')
       and lower(coalesce(e.evaluation::text,'')) not in ('repeat','weak','إعادة','ضعيف')
       and e.end_surah is not null and e.end_ayah is not null
     order by se.session_date desc, e.created_at desc
     limit 1;

    v_route := coalesce(nullif(r.settings->>'route_direction',''), 'forward_surahs_forward_ayahs');
    v_next := case when v_anchor.end_surah is not null then public.rc171_next_quran_location(v_anchor.end_surah, v_anchor.end_ayah, v_route) else null end;

    insert into public.smart_plans (scope_type, halaqa_id, student_id, plan_type, review_tier, title, goal_unit,
      target_units, target_pages, start_date, end_date, schedule_weekdays, catch_up_weekday, max_daily_multiplier,
      auto_extend, quality_gate_enabled, minimum_evaluation, range_start_page, range_end_page, range_start_surah,
      range_start_ayah, range_end_surah, range_end_ayah, selected_surahs, selected_juz, selected_rubs,
      near_revision_pages, far_revision_pages, milestone_mode, milestone_requires_approval, delay_alert_days,
      home_prep_push, status, note, settings, created_by, period_month)
    values ('student', r.shalaqa, r.sid, 'tathbit', 'none',
      'خطة تثبيت ' || to_char(v_month_start, 'YYYY-MM'), r.goal_unit, r.target_units, r.target_pages,
      v_month_start, v_month_end, r.schedule_weekdays, r.catch_up_weekday, r.max_daily_multiplier,
      false, r.quality_gate_enabled, r.minimum_evaluation,
      case when v_next is null then r.range_start_page else null end,
      case when v_next is null then r.range_end_page else null end,
      coalesce((v_next->>'surah')::smallint, r.range_start_surah),
      coalesce((v_next->>'ayah')::smallint, r.range_start_ayah),
      case when v_next is null then r.range_end_surah else null end,
      case when v_next is null then r.range_end_ayah else null end,
      r.selected_surahs, r.selected_juz, r.selected_rubs,
      r.near_revision_pages, r.far_revision_pages, r.milestone_mode, r.milestone_requires_approval, r.delay_alert_days,
      r.home_prep_push, 'active', r.note,
      (r.settings - 'memorization_line_keys') || jsonb_build_object(
        'rolled_from', r.id,
        'continuation_mode', 'last_activity',
        'continuation_anchor_surah', v_anchor.end_surah,
        'continuation_anchor_ayah', v_anchor.end_ayah
      ),
      r.created_by, v_month_start);
    v_created := v_created + 1;
  end loop;
  return jsonb_build_object('frozen', v_frozen, 'created', v_created);
end $$;
revoke all on function public.rc152_roll_tathbit_months() from public;
grant execute on function public.rc152_roll_tathbit_months() to authenticated, service_role;

commit;
